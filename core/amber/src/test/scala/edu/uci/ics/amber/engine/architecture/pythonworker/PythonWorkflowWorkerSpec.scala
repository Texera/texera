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

//package edu.uci.ics.amber.engine.architecture.pythonworker
//
//import akka.actor.{ActorRef, ActorSystem, Props}
//import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
//import edu.uci.ics.amber.clustering.SingleNodeListener
//import edu.uci.ics.amber.engine.architecture.common.WorkflowActor.{NetworkAck, NetworkMessage}
//import edu.uci.ics.amber.engine.architecture.pythonworker.promisehandlers.InitializeOperatorLogicHandler.InitializeOperatorLogic
//import edu.uci.ics.amber.engine.architecture.sendsemantics.partitionings.OneToOnePartitioning
//import edu.uci.ics.amber.engine.architecture.worker.controlcommands.LinkOrdinal
//import edu.uci.ics.amber.engine.architecture.worker.promisehandlers.AddPartitioningHandler.AddPartitioning
//import edu.uci.ics.amber.engine.architecture.worker.promisehandlers.OpenOperatorHandler.OpenOperator
//import edu.uci.ics.amber.engine.architecture.worker.promisehandlers.UpdateInputLinkingHandler.UpdateInputLinking
//import edu.uci.ics.amber.engine.common.Constants
//import edu.uci.ics.amber.engine.common.ambermessage.{
//  ChannelIdentity,
//  ControlPayload,
//  DataFrame,
//  DataPayload,
//  EndOfUpstream,
//  WorkflowFIFOMessage
//}
//import edu.uci.ics.amber.engine.common.rpc.AsyncRPCClient.{ControlInvocation, ReturnInvocation}
//import edu.uci.ics.amber.engine.common.virtualidentity.util.CONTROLLER
//import edu.uci.ics.amber.core.virtualidentity.{
//  ActorVirtualIdentity,
//  PhysicalLink,
//  PhysicalLink,
//  OperatorIdentity
//}
//import edu.uci.ics.amber.engine.e2e.TestOperators
//import edu.uci.ics.texera.workflow.common.tuple.Tuple
//import edu.uci.ics.texera.workflow.common.tuple.schema.{Attribute, AttributeType, Schema}
//import org.scalamock.scalatest.MockFactory
//import org.scalatest.BeforeAndAfterAll
//import org.scalatest.flatspec.AnyFlatSpecLike
//
//import scala.concurrent.duration.DurationInt
//
//class PythonWorkflowWorkerSpec
//    extends TestKit(ActorSystem("PythonWorkerSpec"))
//    with ImplicitSender
//    with AnyFlatSpecLike
//    with BeforeAndAfterAll
//    with MockFactory {
//
//  override def beforeAll: Unit = {
//    system.actorOf(Props[SingleNodeListener], "cluster-info")
//  }
//  override def afterAll: Unit = {
//    TestKit.shutdownActorSystem(system)
//  }
//  private val identifier1 = ActorVirtualIdentity("worker-1")
//  private val identifier2 = ActorVirtualIdentity("worker-2")
//  private val operatorIdentity = OperatorIdentity("testWorkflow", "testOperator")
//  private val layerId1 =
//    PhysicalLink(operatorIdentity.workflow, operatorIdentity.operator, "1st-layer")
//  private val layerId2 =
//    PhysicalLink(operatorIdentity.workflow, operatorIdentity.operator, "2nd-layer")
//  private val pythonOp = TestOperators.pythonOpDesc()
//  private val link = PhysicalLink(layerId1, 0, layerId2, 0)
//  private val schema = Schema
//    .newBuilder()
//    .add(new Attribute("text", AttributeType.STRING))
//    .build()
//  private val initialization = InitializeOperatorLogic(
//    pythonOp.code,
//    isSource = false,
//    Seq(LinkOrdinal(link, 0)),
//    Seq(LinkOrdinal(link, 0)),
//    schema
//  )
//
//  def sendControlToWorker(
//      worker: ActorRef,
//      controls: Array[ControlInvocation],
//      beginSeqNum: Long = 0
//  ): Unit = {
//    var seq = beginSeqNum
//    controls.foreach { ctrl =>
//      worker ! NetworkMessage(
//        seq,
//        WorkflowFIFOMessage(ChannelIdentity(CONTROLLER, identifier1, true), seq, ctrl)
//      )
//      val received = receiveWhile(3.seconds) {
//        case NetworkAck(id, credits) =>
//        // pass
//        case NetworkMessage(id, fifoPayload) =>
//          fifoPayload.payload.asInstanceOf[ControlPayload] match {
//            case ControlInvocation(commandID, command) => assert(commandID == seq)
//            case ReturnInvocation(originalCommandID, controlReturn) =>
//              assert(originalCommandID == seq)
//            case _ => ???
//          }
//          worker ! NetworkAck(id, Constants.unprocessedBatchesSizeLimitInBytesPerWorkerPair)
//      }
//      seq += 1
//    }
//  }
//
//  def mkWorker: ActorRef = TestActorRef(new PythonWorkflowWorker(identifier1))
//
//  "python worker" should "start" in {
//    val worker = mkWorker
//    sendControlToWorker(worker, Array(ControlInvocation(0, initialization)))
//  }
//
//  "python worker" should "process data" in {
//    val worker = mkWorker
//    sendControlToWorker(worker, Array(ControlInvocation(0, initialization)))
//    val mockPolicy = OneToOnePartitioning(1, Array(identifier2))
//    val openControl = ControlInvocation(1, OpenOperator())
//    val invocation = ControlInvocation(2, AddPartitioning(link, mockPolicy))
//    val updateInputLinking = ControlInvocation(3, UpdateInputLinking(identifier2, link))
//    sendControlToWorker(worker, Array(openControl, invocation, updateInputLinking), 1)
//    worker ! NetworkMessage(
//      4,
//      WorkflowFIFOMessage(
//        ChannelIdentity(identifier2, identifier1, false),
//        0,
//        DataFrame(
//          Array(
//            Tuple
//              .newBuilder(schema)
//              .add("text", AttributeType.STRING, "123")
//              .build()
//          )
//        )
//      )
//    )
//    expectMsgClass(classOf[NetworkAck])
//    val data = receiveOne(30.seconds)
//    assert(data.asInstanceOf[NetworkMessage].internalMessage.payload.isInstanceOf[DataFrame])
//  }
//
//  "python worker" should "process data and receive end marker" in {
//    val worker = mkWorker
//    sendControlToWorker(worker, Array(ControlInvocation(0, initialization)))
//    val mockPolicy = OneToOnePartitioning(100, Array(identifier2))
//    val openControl = ControlInvocation(1, OpenOperator())
//    val invocation = ControlInvocation(2, AddPartitioning(link, mockPolicy))
//    val updateInputLinking = ControlInvocation(3, UpdateInputLinking(identifier2, link))
//    sendControlToWorker(worker, Array(openControl, invocation, updateInputLinking), 1)
//    worker ! NetworkMessage(
//      4,
//      WorkflowFIFOMessage(
//        ChannelIdentity(identifier2, identifier1, false),
//        0,
//        DataFrame(
//          (0 until 100)
//            .map(_ =>
//              Tuple
//                .newBuilder(schema)
//                .add("text", AttributeType.STRING, "123")
//                .build()
//            )
//            .toArray
//        )
//      )
//    )
//    expectMsgClass(classOf[NetworkAck])
//    val data = receiveOne(30.seconds)
//    assert(data.asInstanceOf[NetworkMessage].internalMessage.payload.isInstanceOf[DataFrame])
//    worker ! NetworkMessage(
//      5,
//      WorkflowFIFOMessage(
//        ChannelIdentity(identifier2, identifier1, false),
//        1,
//        EndOfUpstream()
//      )
//    )
//    expectMsgClass(classOf[NetworkAck])
//    receiveWhile(10.seconds) {
//      case NetworkMessage(id, fifoPayload) =>
//        fifoPayload.payload match {
//          case payload: ControlPayload => //skip
//          case payload: DataPayload    => assert(payload.isInstanceOf[EndOfUpstream])
//          case _                       => ???
//        }
//    }
//  }
//
//}
