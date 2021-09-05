package edu.uci.ics.amber.engine.architecture.worker

import akka.actor.ActorContext
import edu.uci.ics.amber.engine.architecture.messaginglayer.{
  BatchToTupleConverter,
  ControlOutputPort,
  DataOutputPort,
  TupleToBatchConverter
}
import edu.uci.ics.amber.engine.architecture.worker.promisehandlers._
import edu.uci.ics.amber.engine.common.{AmberLogging, IOperatorExecutor}
import edu.uci.ics.amber.engine.common.rpc.{
  AsyncRPCClient,
  AsyncRPCHandlerInitializer,
  AsyncRPCServer
}
import edu.uci.ics.amber.engine.common.statetransition.WorkerStateManager
import edu.uci.ics.amber.engine.common.virtualidentity.ActorVirtualIdentity

class WorkerAsyncRPCHandlerInitializer(
    val actorId: ActorVirtualIdentity,
    val controlOutputPort: ControlOutputPort,
    val dataOutputPort: DataOutputPort,
    val tupleToBatchConverter: TupleToBatchConverter,
    val batchToTupleConverter: BatchToTupleConverter,
    val pauseManager: PauseManager,
    val dataProcessor: DataProcessor,
    val operator: IOperatorExecutor,
    val breakpointManager: BreakpointManager,
    val stateManager: WorkerStateManager,
    val actorContext: ActorContext,
    source: AsyncRPCClient,
    receiver: AsyncRPCServer
) extends AsyncRPCHandlerInitializer(source, receiver)
    with AmberLogging
    with PauseHandler
    with AddPartitioningHandler
    with QueryAndRemoveBreakpointsHandler
    with QueryCurrentInputTupleHandler
    with QueryStatisticsHandler
    with ResumeHandler
    with StartHandler
    with UpdateInputLinkingHandler
    with AssignLocalBreakpointHandler
    with ShutdownDPThreadHandler {
  var lastReportTime = 0L
}
