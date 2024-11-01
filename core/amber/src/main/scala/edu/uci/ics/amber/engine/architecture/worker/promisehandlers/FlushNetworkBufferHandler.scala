package edu.uci.ics.amber.engine.architecture.worker.promisehandlers

import com.twitter.util.Future
import edu.uci.ics.amber.engine.architecture.rpc.AsyncRPCContext
import edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest
import edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn
import edu.uci.ics.amber.engine.architecture.worker.DataProcessorRPCHandlerInitializer

trait FlushNetworkBufferHandler {
  this: DataProcessorRPCHandlerInitializer =>

  override def flushNetworkBuffer(
      request: EmptyRequest,
      ctx: AsyncRPCContext
  ): Future[EmptyReturn] = {
    dp.outputManager.flush()
    EmptyReturn()
  }

}
