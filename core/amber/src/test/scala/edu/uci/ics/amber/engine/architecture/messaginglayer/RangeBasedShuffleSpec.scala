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

import edu.uci.ics.amber.core.tuple.{Attribute, AttributeType, Schema, Tuple}
import edu.uci.ics.amber.engine.architecture.sendsemantics.partitioners.RangeBasedShufflePartitioner
import edu.uci.ics.amber.engine.architecture.sendsemantics.partitionings.RangeBasedShufflePartitioning
import edu.uci.ics.amber.core.virtualidentity.{ActorVirtualIdentity, ChannelIdentity}
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec

class RangeBasedShuffleSpec extends AnyFlatSpec with MockFactory {
  val identifier = ActorVirtualIdentity("batch producer mock")
  val fakeID1: ActorVirtualIdentity = ActorVirtualIdentity("rec1")
  val fakeID2: ActorVirtualIdentity = ActorVirtualIdentity("rec2")
  val fakeID3: ActorVirtualIdentity = ActorVirtualIdentity("rec3")
  val fakeID4: ActorVirtualIdentity = ActorVirtualIdentity("rec4")
  val fakeID5: ActorVirtualIdentity = ActorVirtualIdentity("rec5")

  val attr: Attribute = new Attribute("Attr1", AttributeType.INTEGER)
  val schema: Schema = Schema().add(attr)
  val partitioning: RangeBasedShufflePartitioning =
    RangeBasedShufflePartitioning(
      400,
      List(
        ChannelIdentity(identifier, fakeID1, isControl = false),
        ChannelIdentity(identifier, fakeID2, isControl = false),
        ChannelIdentity(identifier, fakeID3, isControl = false),
        ChannelIdentity(identifier, fakeID4, isControl = false),
        ChannelIdentity(identifier, fakeID5, isControl = false)
      ),
      Seq("Attr1"),
      -400,
      600
    )

  val partitioner: RangeBasedShufflePartitioner = RangeBasedShufflePartitioner(partitioning)

  "RangeBasedShuffleSpec" should "return 0 when value is less than rangeMin" in {
    val tuple = Tuple.builder(schema).add(attr, -600).build()
    val idx = partitioner.getBucketIndex(tuple)
    assert(idx.next() == 0)
  }

  "RangeBasedShuffleSpec" should "return last receiver when value is more than rangeMax" in {
    val tuple = Tuple.builder(schema).add(attr, 800).build()
    val idx = partitioner.getBucketIndex(tuple)
    assert(idx.next() == 4)
  }

  "RangeBasedShuffleSpec" should "find index correctly" in {
    var tuple = Tuple.builder(schema).add(attr, -400).build()
    var idx = partitioner.getBucketIndex(tuple)
    assert(idx.next() == 0)

    tuple = Tuple.builder(schema).add(attr, -200).build()
    idx = partitioner.getBucketIndex(tuple)
    assert(idx.next() == 0)

    tuple = Tuple.builder(schema).add(attr, -199).build()
    idx = partitioner.getBucketIndex(tuple)
    assert(idx.next() == 1)
  }

  "RangeBasedShuffleSpec" should "handle different data types correctly" in {
    var tuple = Tuple.builder(schema).add(attr, -90).build()
    var idx = partitioner.getBucketIndex(tuple)
    assert(idx.next() == 1)

    val partitioning2: RangeBasedShufflePartitioning =
      RangeBasedShufflePartitioning(
        400,
        List(
          ChannelIdentity(identifier, fakeID1, isControl = false),
          ChannelIdentity(identifier, fakeID2, isControl = false),
          ChannelIdentity(identifier, fakeID3, isControl = false),
          ChannelIdentity(identifier, fakeID4, isControl = false),
          ChannelIdentity(identifier, fakeID5, isControl = false)
        ),
        Seq("Attr2"),
        -400,
        600
      )

    val partitioner2: RangeBasedShufflePartitioner = RangeBasedShufflePartitioner(partitioning2)
    val doubleAttr: Attribute = new Attribute("Attr2", AttributeType.DOUBLE)
    val doubleSchema: Schema = Schema().add(doubleAttr)
    tuple = Tuple.builder(doubleSchema).add(doubleAttr, -90.5).build()
    idx = partitioner2.getBucketIndex(tuple)
    assert(idx.next() == 1)

    val partitioning3: RangeBasedShufflePartitioning =
      RangeBasedShufflePartitioning(
        400,
        List(
          ChannelIdentity(identifier, fakeID1, isControl = false),
          ChannelIdentity(identifier, fakeID2, isControl = false),
          ChannelIdentity(identifier, fakeID3, isControl = false),
          ChannelIdentity(identifier, fakeID4, isControl = false),
          ChannelIdentity(identifier, fakeID5, isControl = false)
        ),
        Seq("Attr3"),
        -400,
        600
      )

    val partitioner3: RangeBasedShufflePartitioner = RangeBasedShufflePartitioner(partitioning3)
    val longAttr: Attribute = new Attribute("Attr3", AttributeType.LONG)
    val longSchema: Schema = Schema().add(longAttr)
    tuple = Tuple.builder(longSchema).add(longAttr, -90L).build()
    idx = partitioner3.getBucketIndex(tuple)
    assert(idx.next() == 1)
  }

}
