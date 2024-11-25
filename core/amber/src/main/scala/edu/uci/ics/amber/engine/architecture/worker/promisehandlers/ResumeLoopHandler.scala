package edu.uci.ics.amber.engine.architecture.worker.promisehandlers

import com.twitter.util.Future
import edu.uci.ics.amber.engine.architecture.rpc.controlcommands.{AsyncRPCContext, IterationCompletedRequest}
import edu.uci.ics.amber.engine.architecture.rpc.controlreturns.EmptyReturn
import edu.uci.ics.amber.engine.architecture.worker.promisehandlers.ResumeLoopHandler.loopToSelfChannelId
import edu.uci.ics.amber.engine.architecture.worker.DataProcessorRPCHandlerInitializer
import edu.uci.ics.amber.engine.common.ambermessage.{DataFrame, MarkerFrame}
import edu.uci.ics.amber.engine.common.model.StartOfIteration
import edu.uci.ics.amber.engine.common.virtualidentity.{ActorVirtualIdentity, ChannelIdentity}

object ResumeLoopHandler {

  private val loopSelf = ActorVirtualIdentity("loopSelf")
  val loopToSelfChannelId: ChannelIdentity = ChannelIdentity(loopSelf, loopSelf, isControl = false)
}


trait ResumeLoopHandler {
  this: DataProcessorRPCHandlerInitializer =>

  override def resumeLoop(
                           request: IterationCompletedRequest,
                           ctx: AsyncRPCContext
  ): Future[EmptyReturn] = {
    dp.processDataPayload(
      loopToSelfChannelId,
      MarkerFrame(StartOfIteration()))
    EmptyReturn()
  }

}
