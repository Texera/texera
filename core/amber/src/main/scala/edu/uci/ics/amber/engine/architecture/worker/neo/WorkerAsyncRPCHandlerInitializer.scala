package edu.uci.ics.amber.engine.architecture.worker.neo

import com.twitter.util.Promise
import edu.uci.ics.amber.engine.architecture.messaginglayer.{ControlOutputPort, DataInputPort}
import edu.uci.ics.amber.engine.architecture.worker.neo.promisehandlers.{
  PauseHandler,
  QueryLoadMetricsHandler,
  QueryNextOpLoadMetricsHandler
}
import edu.uci.ics.amber.engine.architecture.worker.neo.promisehandlers.QueryLoadMetricsHandler.QueryLoadMetrics
import edu.uci.ics.amber.engine.common.WorkflowLogger
import edu.uci.ics.amber.engine.common.ambermessage.WorkerMessage.ExecutionPaused
import edu.uci.ics.amber.engine.common.ambertag.neo.VirtualIdentity.ActorVirtualIdentity
import edu.uci.ics.amber.engine.common.rpc.{
  AsyncRPCClient,
  AsyncRPCHandlerInitializer,
  AsyncRPCServer,
  WorkflowPromise
}
import edu.uci.ics.amber.engine.common.statetransition.WorkerStateManager

class WorkerAsyncRPCHandlerInitializer(
    val selfID: ActorVirtualIdentity,
    val controlOutputPort: ControlOutputPort,
    val pauseManager: PauseManager,
    val dataProcessor: DataProcessor,
    val dataInputPort: DataInputPort,
    source: AsyncRPCClient,
    receiver: AsyncRPCServer
) extends AsyncRPCHandlerInitializer(source, receiver)
    with PauseHandler
    with QueryLoadMetricsHandler
    with QueryNextOpLoadMetricsHandler {
  val logger: WorkflowLogger = WorkflowLogger("WorkerControlHandler")
}
