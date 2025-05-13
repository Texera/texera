/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package edu.uci.ics.amber.engine.architecture.messaginglayer

import edu.uci.ics.amber.engine.architecture.logreplay.OrderEnforcer
import edu.uci.ics.amber.engine.common.AmberLogging
import edu.uci.ics.amber.core.virtualidentity.{ActorVirtualIdentity, ChannelIdentity}

import scala.collection.mutable

class NetworkInputGateway(val actorId: ActorVirtualIdentity)
    extends AmberLogging
    with Serializable
    with InputGateway {

  private val inputChannels =
    new mutable.HashMap[ChannelIdentity, AmberFIFOChannel]()

  @transient lazy private val enforcers = mutable.ListBuffer[OrderEnforcer]()

  def tryPickControlChannel: Option[AmberFIFOChannel] = {
    val ret = inputChannels
      .find {
        case (cid, channel) =>
          cid.isControl && channel.isEnabled && channel.hasMessage && enforcers.forall(enforcer =>
            enforcer.isCompleted || enforcer.canProceed(cid)
          )
      }
      .map(_._2)

    enforcers.filter(enforcer => enforcer.isCompleted).foreach(enforcer => enforcers -= enforcer)
    ret
  }

  def tryPickChannel: Option[AmberFIFOChannel] = {
    val control = tryPickControlChannel
    val ret = if (control.isDefined) {
      control
    } else {
      inputChannels
        .find({
          case (cid, channel) =>
            !cid.isControl && channel.isEnabled && channel.hasMessage && enforcers
              .forall(enforcer => enforcer.isCompleted || enforcer.canProceed(cid))
        })
        .map(_._2)
    }
    enforcers.filter(enforcer => enforcer.isCompleted).foreach(enforcer => enforcers -= enforcer)
    ret
  }

  def getAllDataChannels: Iterable[AmberFIFOChannel] =
    inputChannels.filter(!_._1.isControl).values

  // this function is called by both main thread(for getting credit)
  // and DP thread(for enqueuing messages) so a lock is required here
  def getChannel(channelId: ChannelIdentity): AmberFIFOChannel = {
    synchronized {
      inputChannels.getOrElseUpdate(channelId, new AmberFIFOChannel(channelId))
    }
  }

  def getAllControlChannels: Iterable[AmberFIFOChannel] =
    inputChannels.filter(_._1.isControl).values

  override def getAllChannels: Iterable[AmberFIFOChannel] = inputChannels.values

  override def addEnforcer(enforcer: OrderEnforcer): Unit = {
    enforcers += enforcer
  }

}
