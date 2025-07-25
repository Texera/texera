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

package edu.uci.ics.amber.engine.architecture.logreplay

import edu.uci.ics.amber.engine.architecture.common.ProcessingStepCursor
import edu.uci.ics.amber.engine.architecture.worker.WorkflowWorker.MainThreadDelegateMessage
import edu.uci.ics.amber.engine.common.ambermessage.WorkflowFIFOMessage
import edu.uci.ics.amber.engine.common.storage.SequentialRecordStorage.SequentialRecordWriter
import edu.uci.ics.amber.engine.common.storage.{EmptyRecordStorage, SequentialRecordStorage}
import edu.uci.ics.amber.core.virtualidentity.{ChannelIdentity, EmbeddedControlMessageIdentity}

//In-mem formats:
sealed trait ReplayLogRecord extends Serializable

case class MessageContent(message: WorkflowFIFOMessage) extends ReplayLogRecord

case class ProcessingStep(channelId: ChannelIdentity, step: Long) extends ReplayLogRecord

case class ReplayDestination(id: EmbeddedControlMessageIdentity) extends ReplayLogRecord

case object TerminateSignal extends ReplayLogRecord

object ReplayLogManager {
  def createLogManager(
      logStorage: SequentialRecordStorage[ReplayLogRecord],
      logFileName: String,
      handler: Either[MainThreadDelegateMessage, WorkflowFIFOMessage] => Unit
  ): ReplayLogManager = {
    logStorage match {
      case _: EmptyRecordStorage[ReplayLogRecord] =>
        new EmptyReplayLogManagerImpl(handler)
      case other =>
        val manager = new ReplayLogManagerImpl(handler)
        manager.setupWriter(other.getWriter(logFileName))
        manager
    }
  }
}

trait ReplayLogManager {

  protected val cursor = new ProcessingStepCursor()

  def setupWriter(logWriter: SequentialRecordWriter[ReplayLogRecord]): Unit

  def sendCommitted(msg: Either[MainThreadDelegateMessage, WorkflowFIFOMessage]): Unit

  def terminate(): Unit

  def getStep: Long = cursor.getStep

  def markAsReplayDestination(id: EmbeddedControlMessageIdentity): Unit

  def withFaultTolerant(
      channelId: ChannelIdentity,
      message: Option[WorkflowFIFOMessage]
  )(code: => Unit): Unit = {
    cursor.setCurrentChannel(channelId)
    try {
      code
    } catch {
      case t: Throwable => throw t
    } finally {
      cursor.stepIncrement()
    }
  }

}

class EmptyReplayLogManagerImpl(
    handler: Either[MainThreadDelegateMessage, WorkflowFIFOMessage] => Unit
) extends ReplayLogManager {
  override def setupWriter(
      logWriter: SequentialRecordStorage.SequentialRecordWriter[ReplayLogRecord]
  ): Unit = {}

  override def sendCommitted(msg: Either[MainThreadDelegateMessage, WorkflowFIFOMessage]): Unit = {
    handler(msg)
  }

  override def terminate(): Unit = {}

  override def markAsReplayDestination(id: EmbeddedControlMessageIdentity): Unit = {}
}

class ReplayLogManagerImpl(handler: Either[MainThreadDelegateMessage, WorkflowFIFOMessage] => Unit)
    extends ReplayLogManager {

  private val replayLogger = new ReplayLoggerImpl()

  private var writer: AsyncReplayLogWriter = _

  override def withFaultTolerant(
      channelId: ChannelIdentity,
      message: Option[WorkflowFIFOMessage]
  )(code: => Unit): Unit = {
    replayLogger.logCurrentStepWithMessage(cursor.getStep, channelId, message)
    super.withFaultTolerant(channelId, message)(code)
  }

  override def markAsReplayDestination(id: EmbeddedControlMessageIdentity): Unit = {
    replayLogger.markAsReplayDestination(id)
  }

  override def setupWriter(logWriter: SequentialRecordWriter[ReplayLogRecord]): Unit = {
    writer = new AsyncReplayLogWriter(handler, logWriter)
    writer.start()
  }

  override def sendCommitted(msg: Either[MainThreadDelegateMessage, WorkflowFIFOMessage]): Unit = {
    writer.putLogRecords(replayLogger.drainCurrentLogRecords(cursor.getStep))
    writer.putOutput(msg)
  }

  override def terminate(): Unit = {
    writer.terminate()
  }

}
