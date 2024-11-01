package edu.uci.ics.amber.engine.architecture.controller.promisehandlers

import com.twitter.util.Future
import edu.uci.ics.amber.engine.architecture.controller.ControllerAsyncRPCHandlerInitializer
import edu.uci.ics.amber.engine.architecture.rpc.{AsyncRPCContext, ConsoleMessageTriggeredRequest}
import edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn

trait ConsoleMessageHandler {
  this: ControllerAsyncRPCHandlerInitializer =>

  override def consoleMessageTriggered(
      msg: ConsoleMessageTriggeredRequest,
      ctx: AsyncRPCContext
  ): Future[EmptyReturn] = {
    // forward message to frontend
    sendToClient(msg.consoleMessage)
    EmptyReturn()
  }

}
