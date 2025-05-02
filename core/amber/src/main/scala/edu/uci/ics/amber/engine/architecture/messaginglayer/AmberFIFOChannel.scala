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

import edu.uci.ics.amber.engine.common.AmberLogging
import edu.uci.ics.amber.engine.common.ambermessage.WorkflowFIFOMessage
import edu.uci.ics.amber.engine.common.ambermessage.WorkflowMessage.getInMemSize
import edu.uci.ics.amber.core.virtualidentity.{ActorVirtualIdentity, ChannelIdentity}
import edu.uci.ics.amber.core.workflow.PortIdentity

import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable

/* The abstracted FIFO/exactly-once logic */
class AmberFIFOChannel(val channelId: ChannelIdentity) extends AmberLogging {

  override def actorId: ActorVirtualIdentity = channelId.toWorkerId

  private val ofoMap = new mutable.HashMap[Long, WorkflowFIFOMessage]
  private var current = 0L
  private var enabled = true
  private val fifoQueue = new mutable.ListBuffer[WorkflowFIFOMessage]
  private val holdCredit = new AtomicLong()
  private var portId: Option[PortIdentity] = None

  def acceptMessage(msg: WorkflowFIFOMessage): Unit = {
    val seq = msg.sequenceNumber
    val payload = msg.payload
    if (isDuplicated(seq)) {
      logger.debug(
        s"received duplicated message $payload with seq = $seq while current seq = $current"
      )
    } else if (isAhead(seq)) {
      logger.debug(s"received ahead message $payload with seq = $seq while current seq = $current")
      stash(seq, msg)
    } else {
      enforceFIFO(msg)
    }
  }

  def getCurrentSeq: Long = current

  private def isDuplicated(sequenceNumber: Long): Boolean =
    sequenceNumber < current || ofoMap.contains(sequenceNumber)

  private def isAhead(sequenceNumber: Long): Boolean = sequenceNumber > current

  private def stash(sequenceNumber: Long, data: WorkflowFIFOMessage): Unit = {
    ofoMap(sequenceNumber) = data
  }

  private def enforceFIFO(data: WorkflowFIFOMessage): Unit = {
    fifoQueue.append(data)
    holdCredit.getAndAdd(getInMemSize(data))
    current += 1
    while (ofoMap.contains(current)) {
      val msg = ofoMap(current)
      fifoQueue.append(msg)
      holdCredit.getAndAdd(getInMemSize(msg))
      ofoMap.remove(current)
      current += 1
    }
  }

  def take: WorkflowFIFOMessage = {
    val msg = fifoQueue.remove(0)
    holdCredit.getAndAdd(-getInMemSize(msg))
    msg
  }

  def hasMessage: Boolean = fifoQueue.nonEmpty

  def enable(isEnabled: Boolean): Unit = {
    this.enabled = isEnabled
  }

  def isEnabled: Boolean = enabled

  def getTotalMessageSize: Long = {
    if (fifoQueue.nonEmpty) {
      fifoQueue.map(getInMemSize(_)).sum
    } else {
      0
    }
  }

  def getTotalStashedSize: Long =
    if (ofoMap.nonEmpty) {
      ofoMap.values.map(getInMemSize(_)).sum
    } else {
      0
    }

  def getQueuedCredit: Long = {
    holdCredit.get()
  }

  def setPortId(portId: PortIdentity): Unit = {
    this.portId = Some(portId)
  }

  def getPortId: PortIdentity = {
    this.portId.get
  }
}
