package edu.uci.ics.amber.engine.architecture.controller

import edu.uci.ics.amber.engine.architecture.breakpoint.globalbreakpoint.GlobalBreakpoint
import edu.uci.ics.amber.engine.architecture.deploysemantics.layer.WorkerExecution
import edu.uci.ics.amber.engine.architecture.worker.statistics.WorkerState._
import edu.uci.ics.amber.engine.architecture.worker.statistics.{WorkerState, WorkerStatistics}
import edu.uci.ics.amber.engine.common.VirtualIdentityUtils
import edu.uci.ics.amber.engine.common.virtualidentity.{
  ActorVirtualIdentity,
  ExecutionIdentity,
  PhysicalOpIdentity,
  WorkflowIdentity
}
import edu.uci.ics.amber.engine.common.workflow.PortIdentity
import edu.uci.ics.texera.web.workflowruntimestate.{OperatorRuntimeStats, WorkflowAggregatedState}

import java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters.{CollectionHasAsScala, EnumerationHasAsScala}

class OperatorExecution(
    workflowId: WorkflowIdentity,
    val executionId: ExecutionIdentity,
    physicalOpId: PhysicalOpIdentity,
    numWorkers: Int
) extends Serializable {
  /*
   * Variables related to runtime information
   */

  private val workerExecutions =
    new util.concurrent.ConcurrentHashMap[ActorVirtualIdentity, WorkerExecution]()

  var attachedBreakpoints = new mutable.HashMap[String, GlobalBreakpoint[_]]()

  def states: Array[WorkerState] = workerExecutions.values.asScala.map(_.state).toArray

  def statistics: Array[WorkerStatistics] = workerExecutions.values.asScala.map(_.stats).toArray

  def initWorkerExecution(id: ActorVirtualIdentity): Unit = {

    workerExecutions.put(
      id,
      WorkerExecution(
        id,
        UNINITIALIZED,
        WorkerStatistics(UNINITIALIZED, 0, 0, 0, 0, 0)
      )
    )
  }
  def getWorkerExecution(id: ActorVirtualIdentity): WorkerExecution = {
    if (!workerExecutions.containsKey(id)) {
      initWorkerExecution(id)
    }
    workerExecutions.get(id)
  }

  def getWorkerExecutions: Map[ActorVirtualIdentity, WorkerExecution] =
    workerExecutions
      .keys()
      .asScala
      .map(workerId => workerId -> workerExecutions.get(workerId))
      .toMap

  def getAllWorkerStates: Iterable[WorkerState] = states

  def getInputRowCount: Long = statistics.map(_.inputTupleCount).sum

  def getOutputRowCount: Long = statistics.map(_.outputTupleCount).sum

  def getDataProcessingTime: Long = statistics.map(_.dataProcessingTime).sum

  def getControlProcessingTime: Long = statistics.map(_.controlProcessingTime).sum

  def getIdleTime: Long = statistics.map(_.idleTime).sum

  def getBuiltWorkerIds: Array[ActorVirtualIdentity] =
    workerExecutions.values.asScala.map(_.id).toArray

  def assignBreakpoint(breakpoint: GlobalBreakpoint[_]): Array[ActorVirtualIdentity] = {
    getBuiltWorkerIds
  }

  def setAllWorkerState(state: WorkerState): Unit = {
    (0 until numWorkers).foreach { i =>
      getWorkerExecution(
        VirtualIdentityUtils.createWorkerIdentity(workflowId, physicalOpId, i)
      ).state = state
    }
  }

  def getState: WorkflowAggregatedState = {
    val workerStates = getAllWorkerStates
    if (workerStates.isEmpty) {
      return WorkflowAggregatedState.UNINITIALIZED
    }
    if (workerStates.forall(_ == COMPLETED)) {
      return WorkflowAggregatedState.COMPLETED
    }
    if (workerStates.exists(_ == RUNNING)) {
      return WorkflowAggregatedState.RUNNING
    }
    val unCompletedWorkerStates = workerStates.filter(_ != COMPLETED)
    if (unCompletedWorkerStates.forall(_ == UNINITIALIZED)) {
      WorkflowAggregatedState.UNINITIALIZED
    } else if (unCompletedWorkerStates.forall(_ == PAUSED)) {
      WorkflowAggregatedState.PAUSED
    } else if (unCompletedWorkerStates.forall(_ == READY)) {
      WorkflowAggregatedState.READY
    } else {
      WorkflowAggregatedState.UNKNOWN
    }
  }

  def getOperatorStatistics: OperatorRuntimeStats =
    OperatorRuntimeStats(
      getState,
      getInputRowCount,
      getOutputRowCount,
      numWorkers,
      getDataProcessingTime,
      getControlProcessingTime,
      getIdleTime
    )

  def isInputPortCompleted(portId: PortIdentity): Boolean = {
    workerExecutions
      .values()
      .asScala
      .map(workerExecution => workerExecution.getInputPortExecution(portId))
      .forall(_.completed)
  }

  def isOutputPortCompleted(portId: PortIdentity): Boolean = {
    workerExecutions
      .values()
      .asScala
      .map(workerExecution => workerExecution.getOutputPortExecution(portId))
      .forall(_.completed)
  }
}
