package edu.uci.ics.amber.engine.architecture.worker.promisehandlers

import edu.uci.ics.amber.engine.architecture.worker.{
  WorkerAsyncRPCHandlerInitializer,
  WorkerStatistics
}
import edu.uci.ics.amber.engine.architecture.worker.promisehandlers.QueryStatisticsHandler.QueryStatistics
import edu.uci.ics.amber.engine.common.{Constants, ITupleSinkOperatorExecutor}
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCServer.{CommandCompleted, ControlCommand}

object QueryStatisticsHandler {
  final case class QueryStatistics() extends ControlCommand[WorkerStatistics]
}

trait QueryStatisticsHandler {
  this: WorkerAsyncRPCHandlerInitializer =>

  registerHandler { (msg: QueryStatistics, sender) =>
    // report internal queue length if the gap > 30s
    val now = System.currentTimeMillis()
    if (now - lastReportTime > Constants.loggingQueueSizeInterval) {
      logger.logInfo(
        s"Data Queue Length = ${dataProcessor.getDataQueueLength}, Control Queue Length = ${dataProcessor.getControlQueueLength}"
      )
      lastReportTime = now
    }

    // collect input and output row count
    val (in, out) = dataProcessor.collectStatistics()

    // sink operator doesn't output to downstream so internal count is 0
    // but for user-friendliness we show its input count as output count
    val displayOut = operator match {
      case sink: ITupleSinkOperatorExecutor =>
        in
      case _ =>
        out
    }

    val state = stateManager.getCurrentState

    WorkerStatistics(state, in, displayOut)
  }

}
