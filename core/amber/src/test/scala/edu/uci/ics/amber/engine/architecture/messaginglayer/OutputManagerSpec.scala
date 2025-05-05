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

import com.softwaremill.macwire.wire
import edu.uci.ics.amber.core.marker.EndOfInputChannel
import edu.uci.ics.amber.core.tuple.{AttributeType, Schema, TupleLike}
import edu.uci.ics.amber.engine.architecture.sendsemantics.partitionings.OneToOnePartitioning
import edu.uci.ics.amber.engine.common.ambermessage._
import edu.uci.ics.amber.core.virtualidentity.{
  ActorVirtualIdentity,
  ChannelIdentity,
  OperatorIdentity,
  PhysicalOpIdentity
}
import edu.uci.ics.amber.core.workflow.{PhysicalLink, PortIdentity}
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec

class OutputManagerSpec extends AnyFlatSpec with MockFactory {
  private val mockHandler =
    mock[WorkflowFIFOMessage => Unit]
  private val identifier = ActorVirtualIdentity("batch producer mock")
  private val mockDataOutputPort = // scalafix:ok; need it for wiring purpose
    new NetworkOutputGateway(identifier, mockHandler)
  var counter: Int = 0
  val schema: Schema = Schema()
    .add("field1", AttributeType.INTEGER)
    .add("field2", AttributeType.INTEGER)
    .add("field3", AttributeType.INTEGER)
    .add("field4", AttributeType.INTEGER)
    .add("field5", AttributeType.STRING)
    .add("field6", AttributeType.DOUBLE)

  def physicalOpId(): PhysicalOpIdentity = {
    counter += 1
    PhysicalOpIdentity(OperatorIdentity("" + counter), "" + counter)
  }

  def mkDataMessage(
      to: ActorVirtualIdentity,
      from: ActorVirtualIdentity,
      seq: Long,
      payload: DataPayload
  ): WorkflowFIFOMessage = {
    WorkflowFIFOMessage(ChannelIdentity(from, to, isControl = false), seq, payload)
  }

  "OutputManager" should "aggregate tuples and output" in {
    val outputManager = wire[OutputManager]
    val mockPortId = PortIdentity()
    outputManager.addPort(mockPortId, schema, None)

    val tuples = Array.fill(21)(
      TupleLike(1, 2, 3, 4, "5", 9.8).enforceSchema(schema)
    )
    val fakeID = ActorVirtualIdentity("testReceiver")
    inSequence {
      (mockHandler.apply _).expects(
        mkDataMessage(fakeID, identifier, 0, DataFrame(tuples.slice(0, 10)))
      )
      (mockHandler.apply _).expects(
        mkDataMessage(fakeID, identifier, 1, DataFrame(tuples.slice(10, 20)))
      )
      (mockHandler.apply _).expects(
        mkDataMessage(fakeID, identifier, 2, DataFrame(tuples.slice(20, 21)))
      )
      (mockHandler.apply _).expects(
        mkDataMessage(fakeID, identifier, 3, MarkerFrame(EndOfInputChannel()))
      )
    }
    val fakeLink = PhysicalLink(physicalOpId(), mockPortId, physicalOpId(), mockPortId)
    val fakeReceiver =
      Array[ChannelIdentity](ChannelIdentity(identifier, fakeID, isControl = false))

    outputManager.addPartitionerWithPartitioning(
      fakeLink,
      OneToOnePartitioning(10, fakeReceiver.toSeq)
    )
    tuples.foreach { t =>
      outputManager.passTupleToDownstream(TupleLike(t.getFields).enforceSchema(schema), None)
    }
    outputManager.emitMarker(EndOfInputChannel())
  }

}
