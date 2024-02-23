package edu.uci.ics.amber.engine.architecture.controller.promisehandlers

import edu.uci.ics.amber.engine.architecture.controller.ControllerAsyncRPCHandlerInitializer
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.ForLoopHandler.IterationCompleted
import edu.uci.ics.amber.engine.architecture.worker.promisehandlers.ResumeLoopHandler.ResumeLoop
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCServer.ControlCommand
import edu.uci.ics.amber.engine.common.virtualidentity.ActorVirtualIdentity

object ForLoopHandler {
  final case class IterationCompleted(startWorkerId: ActorVirtualIdentity, endWorkerId: ActorVirtualIdentity) extends ControlCommand[Unit]
}

trait ForLoopHandler {
  this: ControllerAsyncRPCHandlerInitializer =>
  registerHandler { (msg: IterationCompleted, _) =>
    send(ResumeLoop(msg.startWorkerId, msg.endWorkerId), msg.startWorkerId)
  }
}
