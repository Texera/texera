package edu.uci.ics.amber.engine.architecture.scheduling

import com.typesafe.scalalogging.Logger
import edu.uci.ics.amber.engine.architecture.controller.Workflow
import edu.uci.ics.amber.engine.architecture.deploysemantics.layer.WorkerInfo
import edu.uci.ics.amber.engine.architecture.worker.statistics.WorkerState.{
  COMPLETED,
  UNINITIALIZED
}
import edu.uci.ics.amber.engine.architecture.worker.statistics.WorkerStatistics
import edu.uci.ics.amber.engine.common.amberexception.WorkflowRuntimeException
import edu.uci.ics.amber.engine.common.virtualidentity.{
  ActorVirtualIdentity,
  LinkIdentity,
  OperatorIdentity,
  WorkflowIdentity
}
import edu.uci.ics.amber.engine.e2e.TestOperators
import edu.uci.ics.texera.workflow.common.WorkflowContext
import edu.uci.ics.texera.workflow.common.operators.OperatorDescriptor
import edu.uci.ics.texera.workflow.common.storage.OpResultStorage
import edu.uci.ics.texera.workflow.common.workflow.{
  BreakpointInfo,
  OperatorLink,
  OperatorPort,
  WorkflowCompiler,
  WorkflowInfo
}
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.slf4j.LoggerFactory

import scala.collection.immutable.ListMap
import scala.collection.mutable

class WorkflowSchedulerSpec extends AnyFlatSpec with MockFactory {

  def buildWorkflow(
      operators: mutable.MutableList[OperatorDescriptor],
      links: mutable.MutableList[OperatorLink]
  ): Workflow = {
    val context = new WorkflowContext
    context.jobId = "workflow-test"

    val texeraWorkflowCompiler = new WorkflowCompiler(
      WorkflowInfo(operators, links, mutable.MutableList[BreakpointInfo]()),
      context
    )
    texeraWorkflowCompiler.amberWorkflow(WorkflowIdentity("workflow-test"), new OpResultStorage())
  }

  "Scheduler" should "correctly schedule regions in headerlessCsv->keyword->sink workflow" in {
    val headerlessCsvOpDesc = TestOperators.headerlessSmallCsvScanOpDesc()
    val keywordOpDesc = TestOperators.keywordSearchOpDesc("column-1", "Asia")
    val sink = TestOperators.sinkOpDesc()
    val workflow = buildWorkflow(
      mutable.MutableList[OperatorDescriptor](headerlessCsvOpDesc, keywordOpDesc, sink),
      mutable.MutableList[OperatorLink](
        OperatorLink(
          OperatorPort(headerlessCsvOpDesc.operatorID, 0),
          OperatorPort(keywordOpDesc.operatorID, 0)
        ),
        OperatorLink(OperatorPort(keywordOpDesc.operatorID, 0), OperatorPort(sink.operatorID, 0))
      )
    )

    val logger = Logger(
      LoggerFactory.getLogger(s"WorkflowSchedulerTest")
    )
    val scheduler =
      new WorkflowScheduler(Array(), null, null, null, logger, workflow)
    workflow
      .getOperator(headerlessCsvOpDesc.operatorID)
      .topology
      .layers
      .foreach(l => {
        l.workers = ListMap((0 until 1).map { i =>
          workflow.workerToLayer(ActorVirtualIdentity(s"Scan worker $i")) = l
          ActorVirtualIdentity(s"Scan worker $i") -> WorkerInfo(
            ActorVirtualIdentity(s"Scan worker $i"),
            UNINITIALIZED,
            WorkerStatistics(UNINITIALIZED, 0, 0)
          )
        }: _*)
      })
    workflow
      .getOperator(keywordOpDesc.operatorID)
      .topology
      .layers
      .foreach(l => {
        l.workers = ListMap((0 until 1).map { i =>
          workflow.workerToLayer(ActorVirtualIdentity(s"Keyword worker $i")) = l
          ActorVirtualIdentity(s"Keyword worker $i") -> WorkerInfo(
            ActorVirtualIdentity(s"Keyword worker $i"),
            UNINITIALIZED,
            WorkerStatistics(UNINITIALIZED, 0, 0)
          )
        }: _*)
      })
    workflow
      .getOperator(sink.operatorID)
      .topology
      .layers
      .foreach(l => {
        l.workers = ListMap((0 until 1).map { i =>
          workflow.workerToLayer(ActorVirtualIdentity(s"Sink worker $i")) = l
          ActorVirtualIdentity(s"Sink worker $i") -> WorkerInfo(
            ActorVirtualIdentity(s"Sink worker $i"),
            UNINITIALIZED,
            WorkerStatistics(UNINITIALIZED, 0, 0)
          )
        }: _*)
      })
    Set(headerlessCsvOpDesc.operatorID, keywordOpDesc.operatorID, sink.operatorID).foreach(opID => {
      workflow
        .getOperator(opID)
        .topology
        .layers
        .foreach(l => {
          l.workers.keys.foreach(wid => {
            l.workers(wid).state = COMPLETED
          })
        })
    })
    scheduler.runningRegions.add(scheduler.getNextRegionToConstructAndPrepare())
    val isRegionCompleted = scheduler.recordWorkerCompletion(ActorVirtualIdentity("Scan worker 0"))
    assert(isRegionCompleted == true)
    assert(scheduler.getNextRegionToConstructAndPrepare() == null)
  }

  "Scheduler" should "correctly schedule regions in buildcsv->probecsv->hashjoin->hashjoin->sink workflow" in {
    val buildCsv = TestOperators.headerlessSmallCsvScanOpDesc()
    val probeCsv = TestOperators.smallCsvScanOpDesc()
    val hashJoin1 = TestOperators.joinOpDesc("column-1", "Region")
    val hashJoin2 = TestOperators.joinOpDesc("column-2", "Country")
    val sink = TestOperators.sinkOpDesc()
    val workflow = buildWorkflow(
      mutable.MutableList[OperatorDescriptor](
        buildCsv,
        probeCsv,
        hashJoin1,
        hashJoin2,
        sink
      ),
      mutable.MutableList[OperatorLink](
        OperatorLink(
          OperatorPort(buildCsv.operatorID, 0),
          OperatorPort(hashJoin1.operatorID, 0)
        ),
        OperatorLink(
          OperatorPort(probeCsv.operatorID, 0),
          OperatorPort(hashJoin1.operatorID, 1)
        ),
        OperatorLink(
          OperatorPort(buildCsv.operatorID, 0),
          OperatorPort(hashJoin2.operatorID, 0)
        ),
        OperatorLink(
          OperatorPort(hashJoin1.operatorID, 0),
          OperatorPort(hashJoin2.operatorID, 1)
        ),
        OperatorLink(
          OperatorPort(hashJoin2.operatorID, 0),
          OperatorPort(sink.operatorID, 0)
        )
      )
    )
    val logger = Logger(
      LoggerFactory.getLogger(s"WorkflowSchedulerTest")
    )
    val scheduler =
      new WorkflowScheduler(Array(), null, null, null, logger, workflow)

    workflow
      .getOperator(buildCsv.operatorID)
      .topology
      .layers
      .foreach(l => {
        l.workers = ListMap((0 until 1).map { i =>
          workflow.workerToLayer(ActorVirtualIdentity(s"Build Scan worker $i")) = l
          ActorVirtualIdentity(s"Build Scan worker $i") -> WorkerInfo(
            ActorVirtualIdentity(s"Build Scan worker $i"),
            UNINITIALIZED,
            WorkerStatistics(UNINITIALIZED, 0, 0)
          )
        }: _*)
      })
    workflow
      .getOperator(probeCsv.operatorID)
      .topology
      .layers
      .foreach(l => {
        l.workers = ListMap((0 until 1).map { i =>
          workflow.workerToLayer(ActorVirtualIdentity(s"Probe Scan worker $i")) = l
          ActorVirtualIdentity(s"Probe Scan worker $i") -> WorkerInfo(
            ActorVirtualIdentity(s"Probe Scan worker $i"),
            UNINITIALIZED,
            WorkerStatistics(UNINITIALIZED, 0, 0)
          )
        }: _*)
      })
    workflow
      .getOperator(hashJoin1.operatorID)
      .topology
      .layers
      .foreach(l => {
        l.workers = ListMap((0 until 1).map { i =>
          workflow.workerToLayer(ActorVirtualIdentity(s"HashJoin1 worker $i")) = l
          ActorVirtualIdentity(s"HashJoin1 worker $i") -> WorkerInfo(
            ActorVirtualIdentity(s"HashJoin1 worker $i"),
            UNINITIALIZED,
            WorkerStatistics(UNINITIALIZED, 0, 0)
          )
        }: _*)
      })
    workflow
      .getOperator(hashJoin2.operatorID)
      .topology
      .layers
      .foreach(l => {
        l.workers = ListMap((0 until 1).map { i =>
          workflow.workerToLayer(ActorVirtualIdentity(s"HashJoin2 worker $i")) = l
          ActorVirtualIdentity(s"HashJoin2 worker $i") -> WorkerInfo(
            ActorVirtualIdentity(s"HashJoin2 worker $i"),
            UNINITIALIZED,
            WorkerStatistics(UNINITIALIZED, 0, 0)
          )
        }: _*)
      })
    workflow
      .getOperator(sink.operatorID)
      .topology
      .layers
      .foreach(l => {
        l.workers = ListMap((0 until 1).map { i =>
          workflow.workerToLayer(ActorVirtualIdentity(s"Sink worker $i")) = l
          ActorVirtualIdentity(s"Sink worker $i") -> WorkerInfo(
            ActorVirtualIdentity(s"Sink worker $i"),
            UNINITIALIZED,
            WorkerStatistics(UNINITIALIZED, 0, 0)
          )
        }: _*)
      })

    scheduler.runningRegions.add(scheduler.getNextRegionToConstructAndPrepare())
    Set(buildCsv.operatorID).foreach(opID => {
      workflow
        .getOperator(opID)
        .topology
        .layers
        .foreach(l => {
          l.workers.keys.foreach(wid => {
            l.workers(wid).state = COMPLETED
          })
        })
    })
    var isRegionCompleted =
      scheduler.recordWorkerCompletion(ActorVirtualIdentity("Build Scan worker 0"))

    assert(isRegionCompleted == false)
    isRegionCompleted = scheduler.recordLinkCompletion(
      LinkIdentity(
        workflow.getOperator(buildCsv.operatorID).topology.layers.last.id,
        workflow.getOperator(hashJoin1.operatorID).topology.layers.head.id
      )
    )
    assert(isRegionCompleted == false)
    isRegionCompleted = scheduler.recordLinkCompletion(
      LinkIdentity(
        workflow.getOperator(buildCsv.operatorID).topology.layers.last.id,
        workflow.getOperator(hashJoin2.operatorID).topology.layers.head.id
      )
    )
    assert(isRegionCompleted == true)
    scheduler.runningRegions.add(scheduler.getNextRegionToConstructAndPrepare())
    Set(probeCsv.operatorID, hashJoin1.operatorID, hashJoin2.operatorID, sink.operatorID).foreach(
      opID => {
        workflow
          .getOperator(opID)
          .topology
          .layers
          .foreach(l => {
            l.workers.keys.foreach(wid => {
              l.workers(wid).state = COMPLETED
            })
          })
      }
    )
    isRegionCompleted =
      scheduler.recordWorkerCompletion(ActorVirtualIdentity("Probe Scan worker 0"))
    assert(isRegionCompleted == true)
    assert(scheduler.getNextRegionToConstructAndPrepare() == null)
  }

}
