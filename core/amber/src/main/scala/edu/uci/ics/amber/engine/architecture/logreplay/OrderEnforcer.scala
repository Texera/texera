package edu.uci.ics.amber.engine.architecture.logreplay

import edu.uci.ics.amber.engine.common.ChannelIdentity

trait OrderEnforcer {
  var isCompleted: Boolean
  def canProceed(channelId: ChannelIdentity): Boolean
}
