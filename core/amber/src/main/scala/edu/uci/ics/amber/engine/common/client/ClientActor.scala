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

package edu.uci.ics.amber.engine.common.client

import akka.actor.{Actor, ActorRef}
import akka.pattern.StatusReply.Ack
import com.twitter.util.Promise
import edu.uci.ics.amber.core.workflow.{PhysicalPlan, WorkflowContext}
import edu.uci.ics.amber.engine.architecture.common.WorkflowActor.{
  CreditRequest,
  CreditResponse,
  NetworkAck,
  NetworkMessage
}
import edu.uci.ics.amber.engine.architecture.controller.{ClientEvent, Controller, ControllerConfig}
import edu.uci.ics.amber.engine.architecture.rpc.controlcommands.{AsyncRPCContext, ControlRequest}
import edu.uci.ics.amber.engine.architecture.rpc.controlreturns.{
  ControlError,
  ControlReturn,
  ReturnInvocation
}
import edu.uci.ics.amber.engine.common.AmberLogging
import edu.uci.ics.amber.engine.common.ambermessage.WorkflowMessage.getInMemSize
import edu.uci.ics.amber.engine.common.ambermessage.{
  DirectControlMessagePayload,
  DataPayload,
  WorkflowFIFOMessage,
  WorkflowRecoveryMessage
}
import edu.uci.ics.amber.engine.common.client.ClientActor.{
  ClosureRequest,
  CommandRequest,
  InitializeRequest,
  ObservableRequest
}
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCClient
import edu.uci.ics.amber.engine.common.virtualidentity.util.{CLIENT, CONTROLLER}
import edu.uci.ics.amber.error.ErrorUtils.reconstructThrowable
import edu.uci.ics.amber.core.virtualidentity.{ActorVirtualIdentity, ChannelIdentity}

import scala.collection.mutable

// TODO: Rename or refactor it since it has mixed duties (send/receive messages, execute callbacks)
private[client] object ClientActor {
  case class InitializeRequest(
      workflowContext: WorkflowContext,
      physicalPlan: PhysicalPlan,
      controllerConfig: ControllerConfig
  )

  case class ObservableRequest(pf: PartialFunction[Any, Unit])

  case class ClosureRequest[T](closure: () => T)

  case class CommandRequest(
      methodName: String,
      command: ControlRequest,
      promise: Promise[ControlReturn]
  )
}

private[client] class ClientActor extends Actor with AmberLogging {
  var actorId: ActorVirtualIdentity = ActorVirtualIdentity("Client")
  var controller: ActorRef = _
  var controlId = 0L
  val promiseMap = new mutable.LongMap[Promise[ControlReturn]]()
  var handlers: PartialFunction[Any, Unit] = PartialFunction.empty

  private def getQueuedCredit(channelId: ChannelIdentity): Long = {
    0L // client does not have queued credits
  }

  private def handleClientEvent(evt: ClientEvent): Unit = {
    if (handlers.isDefinedAt(evt)) {
      handlers(evt)
    }
  }

  override def receive: Receive = {
    case InitializeRequest(workflowContext, physicalPlan, controllerConfig) =>
      assert(controller == null)
      controller = context.actorOf(
        Controller.props(workflowContext, physicalPlan, controllerConfig)
      )
      sender() ! Ack
    case CreditRequest(channelId: ChannelIdentity) =>
      sender() ! CreditResponse(channelId, getQueuedCredit(channelId))
    case ClosureRequest(closure) =>
      try {
        sender() ! closure()
      } catch {
        case e: Throwable =>
          sender() ! e
      }
    case commandRequest: CommandRequest =>
      controller ! AsyncRPCClient.ControlInvocation(
        commandRequest.methodName,
        commandRequest.command,
        AsyncRPCContext(CLIENT, CONTROLLER),
        controlId
      )
      promiseMap(controlId) = commandRequest.promise
      controlId += 1
    case req: ObservableRequest =>
      handlers = req.pf orElse handlers
      sender() ! scala.runtime.BoxedUnit.UNIT
    case NetworkMessage(
          mId,
          fifoMsg @ WorkflowFIFOMessage(_, _, payload)
        ) =>
      sender() ! NetworkAck(mId, getInMemSize(fifoMsg), getQueuedCredit(fifoMsg.channelId))
      payload match {
        case payload: DirectControlMessagePayload =>
          payload match {
            case ReturnInvocation(originalCommandID, controlReturn) =>
              if (promiseMap.contains(originalCommandID)) {
                controlReturn match {
                  case t: ControlError =>
                    promiseMap(originalCommandID).setException(reconstructThrowable(t))
                  case other =>
                    promiseMap(originalCommandID).setValue(other)
                }
                promiseMap.remove(originalCommandID)
              }
            case o => logger.warn(s"Amber Client should not receive control invocation: $o")
          }
        case _: DataPayload     => ???
        case event: ClientEvent => handleClientEvent(event)
        case msg                => logger.info(s"Amber Client received: $msg")
      }
    case x: WorkflowRecoveryMessage =>
      sender() ! Ack
      controller ! x
    case other =>
      logger.warn("client actor cannot handle " + other) //skip
  }
}
