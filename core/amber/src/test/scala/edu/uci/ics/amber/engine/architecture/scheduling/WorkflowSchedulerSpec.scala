package edu.uci.ics.amber.engine.architecture.scheduling

import edu.uci.ics.amber.engine.architecture.controller.{ControllerConfig, ExecutionState, Workflow}
import edu.uci.ics.amber.engine.architecture.worker.statistics.WorkerState.COMPLETED
import edu.uci.ics.amber.engine.common.VirtualIdentityUtils
import edu.uci.ics.amber.engine.common.virtualidentity.{PhysicalLinkIdentity, OperatorIdentity}
import edu.uci.ics.amber.engine.e2e.TestOperators
import edu.uci.ics.amber.engine.e2e.TestUtils.buildWorkflow
import edu.uci.ics.texera.workflow.common.workflow.{LogicalLink, LogicalPort}
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec

class WorkflowSchedulerSpec extends AnyFlatSpec with MockFactory {

  def setOperatorCompleted(
      workflow: Workflow,
      executionState: ExecutionState,
      logicalOpId: OperatorIdentity
  ): Unit = {
    val physicalOps = workflow.physicalPlan.getPhysicalOpsOfLogicalOp(logicalOpId)
    physicalOps.foreach { physicalOp =>
      executionState.getOperatorExecution(physicalOp.id).setAllWorkerState(COMPLETED)
    }
  }

  "Scheduler" should "correctly schedule regions in headerlessCsv->keyword->sink workflow" in {
    val headerlessCsvOpDesc = TestOperators.headerlessSmallCsvScanOpDesc()
    val keywordOpDesc = TestOperators.keywordSearchOpDesc("column-1", "Asia")
    val sink = TestOperators.sinkOpDesc()
    val workflow = buildWorkflow(
      List(headerlessCsvOpDesc, keywordOpDesc, sink),
      List(
        LogicalLink(
          LogicalPort(headerlessCsvOpDesc.operatorIdentifier, 0),
          LogicalPort(keywordOpDesc.operatorIdentifier, 0)
        ),
        LogicalLink(
          LogicalPort(keywordOpDesc.operatorIdentifier, 0),
          LogicalPort(sink.operatorIdentifier, 0)
        )
      )
    )
    val executionState = new ExecutionState(workflow)
    val scheduler =
      new WorkflowScheduler(
        workflow.regionPlan.regions.toBuffer,
        executionState,
        ControllerConfig.default,
        null
      )
    Set(
      headerlessCsvOpDesc.operatorIdentifier,
      keywordOpDesc.operatorIdentifier,
      sink.operatorIdentifier
    ).foreach(logicalOpId => setOperatorCompleted(workflow, executionState, logicalOpId))
    scheduler.schedulingPolicy.addToRunningRegions(
      scheduler.schedulingPolicy.startWorkflow(workflow),
      null
    )
    val opIdentity = headerlessCsvOpDesc.operatorIdentifier
    val physicalOpId = workflow.physicalPlan.getPhysicalOpsOfLogicalOp(opIdentity).head.id
    val nextRegions =
      scheduler.schedulingPolicy.onWorkerCompletion(
        workflow,
        executionState,
        VirtualIdentityUtils.createWorkerIdentity(workflow.workflowId.executionId, physicalOpId, 0)
      )
    assert(nextRegions.isEmpty)
    assert(scheduler.schedulingPolicy.getCompletedRegions.size == 1)
  }

  "Scheduler" should "correctly schedule regions in buildcsv->probecsv->hashjoin->hashjoin->sink workflow" in {
    val buildCsv = TestOperators.headerlessSmallCsvScanOpDesc()
    val probeCsv = TestOperators.smallCsvScanOpDesc()
    val hashJoin1 = TestOperators.joinOpDesc("column-1", "Region")
    val hashJoin2 = TestOperators.joinOpDesc("column-2", "Country")
    val sink = TestOperators.sinkOpDesc()
    val workflow = buildWorkflow(
      List(
        buildCsv,
        probeCsv,
        hashJoin1,
        hashJoin2,
        sink
      ),
      List(
        LogicalLink(
          LogicalPort(buildCsv.operatorIdentifier, 0),
          LogicalPort(hashJoin1.operatorIdentifier, 0)
        ),
        LogicalLink(
          LogicalPort(probeCsv.operatorIdentifier, 0),
          LogicalPort(hashJoin1.operatorIdentifier, 1)
        ),
        LogicalLink(
          LogicalPort(buildCsv.operatorIdentifier, 0),
          LogicalPort(hashJoin2.operatorIdentifier, 0)
        ),
        LogicalLink(
          LogicalPort(hashJoin1.operatorIdentifier, 0),
          LogicalPort(hashJoin2.operatorIdentifier, 1)
        ),
        LogicalLink(
          LogicalPort(hashJoin2.operatorIdentifier, 0),
          LogicalPort(sink.operatorIdentifier, 0)
        )
      )
    )

    val executionState = new ExecutionState(workflow)
    val scheduler =
      new WorkflowScheduler(
        workflow.regionPlan.regions.toBuffer,
        executionState,
        ControllerConfig.default,
        null
      )
    scheduler.schedulingPolicy.addToRunningRegions(
      scheduler.schedulingPolicy.startWorkflow(workflow),
      null
    )
    Set(buildCsv.operatorIdentifier).foreach(logicalOpId =>
      setOperatorCompleted(workflow, executionState, logicalOpId)
    )
    val opIdentity = buildCsv.operatorIdentifier
    val physicalOpId = workflow.physicalPlan.getPhysicalOpsOfLogicalOp(opIdentity).head.id
    var nextRegions =
      scheduler.schedulingPolicy.onWorkerCompletion(
        workflow,
        executionState,
        VirtualIdentityUtils.createWorkerIdentity(workflow.workflowId.executionId, physicalOpId, 0)
      )
    assert(nextRegions.isEmpty)

    nextRegions = scheduler.schedulingPolicy.onLinkCompletion(
      workflow,
      executionState,
      PhysicalLinkIdentity(
        workflow.physicalPlan
          .getPhysicalOpsOfLogicalOp(
            buildCsv.operatorIdentifier
          )
          .last
          .id,
        0,
        workflow.physicalPlan
          .getPhysicalOpsOfLogicalOp(
            hashJoin1.operatorIdentifier
          )
          .head
          .id,
        0
      )
    )
    assert(nextRegions.isEmpty)
    nextRegions = scheduler.schedulingPolicy.onLinkCompletion(
      workflow,
      executionState,
      PhysicalLinkIdentity(
        workflow.physicalPlan
          .getPhysicalOpsOfLogicalOp(
            buildCsv.operatorIdentifier
          )
          .last
          .id,
        0,
        workflow.physicalPlan
          .getPhysicalOpsOfLogicalOp(
            hashJoin2.operatorIdentifier
          )
          .head
          .id,
        0
      )
    )
    assert(nextRegions.nonEmpty)
    assert(scheduler.schedulingPolicy.getCompletedRegions.size == 1)
    scheduler.schedulingPolicy.addToRunningRegions(nextRegions, null)
    Set(
      probeCsv.operatorIdentifier,
      hashJoin1.operatorIdentifier,
      hashJoin2.operatorIdentifier,
      sink.operatorIdentifier
    ).foreach(logicalOpId => setOperatorCompleted(workflow, executionState, logicalOpId))
    val probeLogicalOpId = probeCsv.operatorIdentifier
    val probePhysicalOpId =
      workflow.physicalPlan.getPhysicalOpsOfLogicalOp(probeLogicalOpId).head.id
    nextRegions = scheduler.schedulingPolicy.onWorkerCompletion(
      workflow,
      executionState,
      VirtualIdentityUtils.createWorkerIdentity(
        workflow.workflowId.executionId,
        probePhysicalOpId,
        0
      )
    )
    assert(nextRegions.isEmpty)
    assert(scheduler.schedulingPolicy.getCompletedRegions.size == 2)
  }

}
