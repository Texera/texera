package edu.uci.ics.texera.workflow

import com.typesafe.scalalogging.LazyLogging
import edu.uci.ics.amber.core.storage.result.ExecutionResourcesMapping
import edu.uci.ics.amber.core.storage.{DocumentFactory, StorageConfig, VFSURIFactory}
import edu.uci.ics.amber.core.workflow.{PhysicalPlan, WorkflowContext}
import edu.uci.ics.amber.engine.architecture.controller.Workflow
import edu.uci.ics.amber.engine.common.Utils.objectMapper
import edu.uci.ics.amber.operator.SpecialPhysicalOpFactory
import edu.uci.ics.amber.core.virtualidentity.OperatorIdentity
import edu.uci.ics.amber.core.workflow.OutputPort.OutputMode.SINGLE_SNAPSHOT
import edu.uci.ics.amber.core.workflow.PhysicalLink
import edu.uci.ics.amber.engine.common.AmberConfig
import edu.uci.ics.texera.web.model.websocket.request.LogicalPlanPojo
import edu.uci.ics.texera.web.resource.dashboard.user.workflow.WorkflowExecutionsResource
import edu.uci.ics.texera.web.service.ExecutionsMetadataPersistService

import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.util.{Failure, Success, Try}

class WorkflowCompiler(
    context: WorkflowContext
) extends LazyLogging {

  // function to expand logical plan to physical plan
  private def expandLogicalPlan(
      logicalPlan: LogicalPlan,
      logicalOpsToViewResult: List[String],
      errorList: Option[ArrayBuffer[(OperatorIdentity, Throwable)]]
  ): PhysicalPlan = {
    val terminalLogicalOps = logicalPlan.getTerminalOperatorIds
    val toAddSink = (terminalLogicalOps ++ logicalOpsToViewResult.map(OperatorIdentity(_))).toSet
    var physicalPlan = PhysicalPlan(operators = Set.empty, links = Set.empty)
    // create a JSON object that holds pointers to the workflow's results in Mongo
    val resultsJSON = objectMapper.createObjectNode()
    val sinksPointers = objectMapper.createArrayNode()

    logicalPlan.getTopologicalOpIds.asScala.foreach(logicalOpId =>
      Try {
        val logicalOp = logicalPlan.getOperator(logicalOpId)

        val subPlan = logicalOp.getPhysicalPlan(context.workflowId, context.executionId)
        subPlan
          .topologicalIterator()
          .map(subPlan.getOperator)
          .foreach({ physicalOp =>
            {
              val externalLinks = logicalPlan
                .getUpstreamLinks(logicalOp.operatorIdentifier)
                .filter(link => physicalOp.inputPorts.contains(link.toPortId))
                .flatMap { link =>
                  physicalPlan
                    .getPhysicalOpsOfLogicalOp(link.fromOpId)
                    .find(_.outputPorts.contains(link.fromPortId))
                    .map(fromOp =>
                      PhysicalLink(fromOp.id, link.fromPortId, physicalOp.id, link.toPortId)
                    )
                }

              val internalLinks = subPlan.getUpstreamPhysicalLinks(physicalOp.id)

              // Add the operator to the physical plan
              physicalPlan = physicalPlan.addOperator(physicalOp.propagateSchema())

              // Add all the links to the physical plan
              physicalPlan = (externalLinks ++ internalLinks)
                .foldLeft(physicalPlan) { (plan, link) => plan.addLink(link) }
            }
          })

        // assign the sinks to toAddSink operators' external output ports
        subPlan
          .topologicalIterator()
          .filter(opId => toAddSink.contains(opId.logicalOpId))
          .map(physicalPlan.getOperator)
          .foreach { physicalOp =>
            physicalOp.outputPorts
              .filterNot(_._1.internal)
              .foreach {
                case (outputPortId, (outputPort, _, schema)) =>
                  var storageUri =
                    WorkflowExecutionsResource.getResultUriByExecutionAndPort(
                      context.workflowId,
                      context.executionId,
                      physicalOp.id.logicalOpId,
                      Some(physicalOp.id.layerName),
                      outputPortId
                    )
                  if (
                    (!AmberConfig.isUserSystemEnabled && !ExecutionResourcesMapping
                      .getResourceURIs(context.executionId)
                      .contains(
                        storageUri.get
                      )) || (AmberConfig.isUserSystemEnabled && storageUri.isEmpty)
                  ) {
                    // Create storage if it doesn't exist
                    storageUri = Option(
                      VFSURIFactory.createResultURI(
                        context.workflowId,
                        context.executionId,
                        physicalOp.id.logicalOpId,
                        Some(physicalOp.id.layerName),
                        outputPortId
                      )
                    )
                    // Determine the storage type, defaulting to iceberg for large HTML visualizations
                    val storageType =
                      if (outputPort.mode == SINGLE_SNAPSHOT) DocumentFactory.ICEBERG
                      else StorageConfig.resultStorageMode

                    // Create storage if it doesn't exist
                    val sinkStorageSchema =
                      schema.getOrElse(throw new IllegalStateException("Schema is missing"))

                    // create the storage resource and record the URI
                    DocumentFactory.createDocument(storageUri.get, sinkStorageSchema)
                    WorkflowExecutionsResource.insertOperatorPortResultUri(
                      context.executionId,
                      physicalOp.id.logicalOpId,
                      physicalOp.id.layerName,
                      outputPortId,
                      storageUri.get
                    )

                    // Add sink collection name to the JSON array of sinks
                    sinksPointers.add(
                      objectMapper
                        .createObjectNode()
                        .put("storageType", storageType)
                        .put("storageKey", storageUri.get.toString)
                    )
                  }

                  // TODO: remove
                  // Create and link the sink operator
                  val sinkPhysicalOp = SpecialPhysicalOpFactory.newSinkPhysicalOp(
                    storageUri.get,
                    outputPort.mode
                  )
                  val sinkLink = PhysicalLink(
                    physicalOp.id,
                    outputPort.id,
                    sinkPhysicalOp.id,
                    sinkPhysicalOp.outputPorts.head._1
                  )

                  physicalPlan = physicalPlan.addOperator(sinkPhysicalOp).addLink(sinkLink)
              }
          }
      } match {
        case Success(_) =>

        case Failure(err) =>
          errorList match {
            case Some(list) => list.append((logicalOpId, err))
            case None       => throw err
          }
      }
    )

    // update execution entry in MySQL to have pointers to the mongo collections
    resultsJSON.set("results", sinksPointers)
    ExecutionsMetadataPersistService.tryUpdateExistingExecution(context.executionId) {
      _.setResult(resultsJSON.toString)
    }
    physicalPlan
  }

  /**
    * Compile a workflow to physical plan, along with the schema propagation result and error(if any)
    *
    * Comparing to WorkflowCompilingService's compiler, which is used solely for workflow editing,
    *  This compile is used before executing the workflow.
    *
    * TODO: we should consider merge this compile with WorkflowCompilingService's compile
    * @param logicalPlanPojo the pojo parsed from workflow str provided by user
    * @return Workflow, containing the physical plan, logical plan and workflow context
    */
  def compile(
      logicalPlanPojo: LogicalPlanPojo
  ): Workflow = {
    // 1. convert the pojo to logical plan
    val logicalPlan: LogicalPlan = LogicalPlan(logicalPlanPojo)

    // 2. resolve the file name in each scan source operator
    logicalPlan.resolveScanSourceOpFileName(None)

    // 3. expand the logical plan to the physical plan, and assign storage
    val physicalPlan = expandLogicalPlan(logicalPlan, logicalPlanPojo.opsToViewResult, None)

    Workflow(context, logicalPlan, physicalPlan)
  }
}
