package edu.uci.ics.amber.engine.architecture.controller

import edu.uci.ics.amber.engine.common.rpc.AsyncRPCServer.ControlCommand
import edu.uci.ics.amber.engine.common.virtualidentity.ActorVirtualIdentity
import edu.uci.ics.texera.web.workflowruntimestate.{OperatorMetrics, WorkflowAggregatedState}
import edu.uci.ics.texera.workflow.common.tuple.Tuple

object ControllerEvent {

  case class ExecutionStateUpdate(state: WorkflowAggregatedState) extends ControlCommand[Unit]

  case class ExecutionStatsUpdate(
      operatorMetrics: Map[String, OperatorMetrics]
  ) extends ControlCommand[Unit]

  case class ReportCurrentProcessingTuple(
      operatorID: String,
      tuple: Array[(Tuple, ActorVirtualIdentity)]
  ) extends ControlCommand[Unit]

  case class WorkerAssignmentUpdate(workerMapping: Map[String, Seq[String]])
      extends ControlCommand[Unit]

  final case class FatalError(e: Throwable, fromActor: Option[ActorVirtualIdentity] = None)
      extends ControlCommand[Unit]
}
