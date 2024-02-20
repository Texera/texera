package edu.uci.ics.amber.engine.architecture.worker

import edu.uci.ics.amber.engine.architecture.worker.promisehandlers._
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCHandlerInitializer
import edu.uci.ics.amber.engine.common.virtualidentity.ActorVirtualIdentity
import edu.uci.ics.amber.engine.common.AmberLogging

class DataProcessorRPCHandlerInitializer(val dp: DataProcessor)
    extends AsyncRPCHandlerInitializer(dp.asyncRPCClient, dp.asyncRPCServer)
    with AmberLogging
    with OpenOperatorHandler
    with PauseHandler
    with AddPartitioningHandler
    with QueryAndRemoveBreakpointsHandler
    with QueryCurrentInputTupleHandler
    with QueryStatisticsHandler
    with ResumeHandler
    with StartHandler
    with AssignPortHandler
    with AddInputChannelHandler
    with AssignLocalBreakpointHandler
    with ShutdownDPThreadHandler
    with FlushNetworkBufferHandler
    with ModifyOperatorLogicHandler
    with RetrieveStateHandler
    with PrepareCheckpointHandler
    with FinalizeCheckpointHandler {
  val actorId: ActorVirtualIdentity = dp.actorId
}
