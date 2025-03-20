package edu.uci.ics.amber.engine.architecture.worker.promisehandlers

import com.twitter.util.Future
import edu.uci.ics.amber.core.marker.StartOfIteration
import edu.uci.ics.amber.core.virtualidentity.{ActorVirtualIdentity, ChannelIdentity}
import edu.uci.ics.amber.engine.architecture.rpc.controlcommands.{AsyncRPCContext, IterationCompletedRequest}
import edu.uci.ics.amber.engine.architecture.rpc.controlreturns.EmptyReturn
import edu.uci.ics.amber.engine.architecture.worker.promisehandlers.ResumeLoopHandler.loopToSelfChannelId
import edu.uci.ics.amber.engine.architecture.worker.DataProcessorRPCHandlerInitializer
import edu.uci.ics.amber.engine.common.ambermessage.MarkerFrame

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