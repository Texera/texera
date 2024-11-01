package edu.uci.ics.amber.engine.architecture.controller.promisehandlers

import com.twitter.util.Future
import edu.uci.ics.amber.engine.architecture.controller.ControllerAsyncRPCHandlerInitializer
import edu.uci.ics.amber.engine.architecture.rpc.{AsyncRPCContext, DebugCommandRequest}
import edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn
import edu.uci.ics.amber.engine.common.ActorVirtualIdentity

trait DebugCommandHandler {
  this: ControllerAsyncRPCHandlerInitializer =>

  override def debugCommand(msg: DebugCommandRequest, ctx: AsyncRPCContext): Future[EmptyReturn] = {
    workerInterface.debugCommand(msg, mkContext(ActorVirtualIdentity(msg.workerId)))
    EmptyReturn()
  }

}
