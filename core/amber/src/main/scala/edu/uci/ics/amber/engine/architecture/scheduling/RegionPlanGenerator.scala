package edu.uci.ics.amber.engine.architecture.scheduling

import edu.uci.ics.amber.engine.architecture.deploysemantics.PhysicalOp
import edu.uci.ics.amber.engine.architecture.scheduling.RegionPlanGenerator.replaceVertex
import edu.uci.ics.amber.engine.architecture.scheduling.resourcePolicies.{
  DefaultResourceAllocator,
  ExecutionClusterInfo
}
import edu.uci.ics.amber.engine.common.virtualidentity.{OperatorIdentity, PhysicalOpIdentity}
import edu.uci.ics.amber.engine.common.workflow.PhysicalLink
import edu.uci.ics.texera.workflow.common.WorkflowContext
import edu.uci.ics.texera.workflow.common.storage.OpResultStorage
import edu.uci.ics.texera.workflow.common.workflow.PhysicalPlan
import edu.uci.ics.texera.workflow.operators.sink.managed.ProgressiveSinkOpDesc
import edu.uci.ics.texera.workflow.operators.source.cache.CacheSourceOpDesc
import org.jgrapht.graph.DirectedAcyclicGraph
import org.jgrapht.traverse.TopologicalOrderIterator

import scala.collection.mutable
import scala.jdk.CollectionConverters.{CollectionHasAsScala, IteratorHasAsScala}

object RegionPlanGenerator {
  def replaceVertex(
      graph: DirectedAcyclicGraph[Region, RegionLink],
      oldVertex: Region,
      newVertex: Region
  ): Unit = {
    if (oldVertex.equals(newVertex)) {
      return
    }
    graph.addVertex(newVertex)
    graph
      .outgoingEdgesOf(oldVertex)
      .asScala
      .toList
      .foreach(oldEdge => {
        val dest = graph.getEdgeTarget(oldEdge)
        graph.removeEdge(oldEdge)
        graph.addEdge(newVertex, dest, RegionLink(newVertex.id, dest.id))
      })
    graph
      .incomingEdgesOf(oldVertex)
      .asScala
      .toList
      .foreach(oldEdge => {
        val source = graph.getEdgeSource(oldEdge)
        graph.removeEdge(oldEdge)
        graph.addEdge(source, newVertex, RegionLink(source.id, newVertex.id))
      })
    graph.removeVertex(oldVertex)
  }
}

abstract class RegionPlanGenerator(
    workflowContext: WorkflowContext,
    var physicalPlan: PhysicalPlan,
    opResultStorage: OpResultStorage
) {
  private val executionClusterInfo = new ExecutionClusterInfo()

  def generate(): (RegionPlan, PhysicalPlan)

  def allocateResource(
      regionDAG: DirectedAcyclicGraph[Region, RegionLink]
  ): Unit = {
    val dataTransferBatchSize = workflowContext.workflowSettings.dataTransferBatchSize

    val resourceAllocator =
      new DefaultResourceAllocator(physicalPlan, executionClusterInfo, dataTransferBatchSize)
    // generate the resource configs
    new TopologicalOrderIterator(regionDAG).asScala
      .foreach(region => {
        val (newRegion, _) = resourceAllocator.allocate(region)
        replaceVertex(regionDAG, region, newRegion)
      })
  }

  def getRegions(
      physicalOpId: PhysicalOpIdentity,
      regionDAG: DirectedAcyclicGraph[Region, RegionLink]
  ): Set[Region] = {
    regionDAG
      .vertexSet()
      .asScala
      .filter(region => region.getOperators.map(_.id).contains(physicalOpId))
      .toSet
  }

  /**
    * For a dependee input link, although it connects two regions A->B, we include this link and its toOp in region A
    *  so that the dependee link will be completed first.
    */
  def populateDependeeLinks(
      regionDAG: DirectedAcyclicGraph[Region, RegionLink]
  ): Unit = {

    val dependeeLinks = physicalPlan
      .topologicalIterator()
      .flatMap { physicalOpId =>
        val upstreamPhysicalOpIds = physicalPlan.getUpstreamPhysicalOpIds(physicalOpId)
        upstreamPhysicalOpIds.flatMap { upstreamPhysicalOpId =>
          physicalPlan
            .getLinksBetween(upstreamPhysicalOpId, physicalOpId)
            .filter(link =>
              !physicalPlan.getOperator(physicalOpId).isSinkOperator && (physicalPlan
                .getOperator(physicalOpId)
                .isInputLinkDependee(link))
            )
        }
      }
      .toSet

    dependeeLinks
      .flatMap { link => getRegions(link.fromOpId, regionDAG).map(region => region -> link) }
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2))
      .foreach {
        case (region, links) =>
          val newRegion = region.copy(
            physicalLinks = region.physicalLinks ++ links,
            physicalOps =
              region.getOperators ++ links.map(_.toOpId).map(id => physicalPlan.getOperator(id))
          )
          replaceVertex(regionDAG, region, newRegion)
      }
  }

  def replaceLinkWithMaterialization(
      physicalLink: PhysicalLink,
      writerReaderPairs: mutable.HashMap[PhysicalOpIdentity, PhysicalOpIdentity]
  ): PhysicalPlan = {

    val fromOp = physicalPlan.getOperator(physicalLink.fromOpId)
    val fromPortId = physicalLink.fromPortId

    val toOp = physicalPlan.getOperator(physicalLink.toOpId)
    val toPortId = physicalLink.toPortId

    var newPhysicalPlan = physicalPlan
      .removeLink(physicalLink)

    // create cache writer and link
    val matWriterPhysicalOp: PhysicalOp =
      createMatWriter(physicalLink)
    val sourceToWriterLink =
      PhysicalLink(
        fromOp.id,
        fromPortId,
        matWriterPhysicalOp.id,
        matWriterPhysicalOp.inputPorts.keys.head
      )
    newPhysicalPlan = newPhysicalPlan
      .addOperator(matWriterPhysicalOp)
      .addLink(sourceToWriterLink)

    // create cache reader and link
    val matReaderPhysicalOp: PhysicalOp =
      createMatReader(matWriterPhysicalOp.id.logicalOpId, physicalLink)
    val readerToDestLink =
      PhysicalLink(
        matReaderPhysicalOp.id,
        matReaderPhysicalOp.outputPorts.keys.head,
        toOp.id,
        toPortId
      )
    // add the pair to the map for later adding edges between 2 regions.
    writerReaderPairs(matWriterPhysicalOp.id) = matReaderPhysicalOp.id
    newPhysicalPlan
      .addOperator(matReaderPhysicalOp)
      .addLink(readerToDestLink)
  }

  def createMatReader(
      matWriterLogicalOpId: OperatorIdentity,
      physicalLink: PhysicalLink
  ): PhysicalOp = {
    val matReader = new CacheSourceOpDesc(
      matWriterLogicalOpId,
      opResultStorage: OpResultStorage
    )
    matReader.setContext(workflowContext)
    matReader.setOperatorId(s"cacheSource_${getMatIdFromPhysicalLink(physicalLink)}")

    matReader
      .getPhysicalOp(
        workflowContext.workflowId,
        workflowContext.executionId
      )
      .propagateSchema()

  }

  def createMatWriter(
      physicalLink: PhysicalLink
  ): PhysicalOp = {
    val matWriter = new ProgressiveSinkOpDesc()
    matWriter.setContext(workflowContext)
    matWriter.setOperatorId(s"materialized_${getMatIdFromPhysicalLink(physicalLink)}")

    // expect exactly one input port and one output port

    matWriter.setStorage(
      opResultStorage.create(
        key = matWriter.operatorIdentifier,
        mode = OpResultStorage.defaultStorageMode
      )
    )

    matWriter.getPhysicalOp(
      workflowContext.workflowId,
      workflowContext.executionId
    )

  }

  private def getMatIdFromPhysicalLink(physicalLink: PhysicalLink) =
    s"${physicalLink.fromOpId.logicalOpId}_${physicalLink.fromOpId.layerName}_" +
      s"${physicalLink.fromPortId.id}_" +
      s"${physicalLink.toOpId.logicalOpId}_${physicalLink.toOpId.layerName}_" +
      s"${physicalLink.toPortId.id}"

}
