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

package edu.uci.ics.amber.engine.architecture.worker

import edu.uci.ics.amber.engine.architecture.messaginglayer.InputGateway
import edu.uci.ics.amber.engine.common.AmberLogging
import edu.uci.ics.amber.core.virtualidentity.{ActorVirtualIdentity, ChannelIdentity}

import scala.collection.mutable

class PauseManager(val actorId: ActorVirtualIdentity, inputGateway: InputGateway)
    extends AmberLogging {

  private val globalPauses = new mutable.HashSet[PauseType]()
  private val specificInputPauses = mutable.MultiDict[PauseType, ChannelIdentity]()

  def pause(pauseType: PauseType): Unit = {
    globalPauses.add(pauseType)
    // disable all data queues
    inputGateway.getAllDataChannels.foreach(_.enable(false))
  }

  def pauseInputChannel(pauseType: PauseType, inputs: List[ChannelIdentity]): Unit = {
    inputs.foreach(input => {
      specificInputPauses.addOne((pauseType, input))
      // disable specified data queues
      inputGateway.getChannel(input).enable(false)
    })
  }

  def resume(pauseType: PauseType): Unit = {
    globalPauses.remove(pauseType)
    specificInputPauses.removeKey(pauseType)

    // still globally paused no action, don't need to resume anything
    if (globalPauses.nonEmpty) {
      return
    }
    // global pause is empty, specific input pause is also empty, resume all
    if (specificInputPauses.isEmpty) {
      inputGateway.getAllDataChannels.foreach(_.enable(true))
      return
    }
    // need to resume specific input channels
    val pausedChannels = specificInputPauses.values.toSet
    inputGateway.getAllChannels.foreach(_.enable(true))
    pausedChannels.foreach { ChannelIdentity =>
      inputGateway.getChannel(ChannelIdentity).enable(false)
    }
  }

  def isPaused: Boolean = {
    globalPauses.nonEmpty
  }

}
