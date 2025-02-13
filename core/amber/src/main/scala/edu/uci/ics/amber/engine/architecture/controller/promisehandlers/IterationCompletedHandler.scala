package edu.uci.ics.amber.engine.architecture.controller.promisehandlers

import com.twitter.util.Future
import edu.uci.ics.amber.engine.architecture.controller.ControllerAsyncRPCHandlerInitializer
import edu.uci.ics.amber.engine.architecture.rpc.controlcommands.{AsyncRPCContext, IterationCompletedRequest}
import edu.uci.ics.amber.engine.architecture.rpc.controlreturns.EmptyReturn


trait IterationCompletedHandler {
  this: ControllerAsyncRPCHandlerInitializer =>

  override def iterationCompleted(
                                   msg: IterationCompletedRequest,
                                   ctx: AsyncRPCContext
                                 ): Future[EmptyReturn] = {
    workerInterface.resumeLoop(msg, mkContext(msg.startWorkerId))
    EmptyReturn()
  }
}