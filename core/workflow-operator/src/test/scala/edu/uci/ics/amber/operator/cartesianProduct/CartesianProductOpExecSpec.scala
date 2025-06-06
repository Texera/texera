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

package edu.uci.ics.amber.operator.cartesianProduct

import edu.uci.ics.amber.core.tuple.{
  Attribute,
  AttributeType,
  Schema,
  SchemaEnforceable,
  Tuple,
  TupleLike
}
import edu.uci.ics.amber.core.workflow.PortIdentity
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec

class CartesianProductOpExecSpec extends AnyFlatSpec with BeforeAndAfter {
  val leftPort: Int = 0
  val rightPort: Int = 1

  var opDesc: CartesianProductOpDesc = _
  var opExec: CartesianProductOpExec = _

  def generate_tuple(schema: Schema, value: Option[Int]): Tuple = {
    Tuple
      .builder(schema)
      .addSequentially(
        (1 to schema.getAttributes.length).map(_ => value.map(_.toString).orNull).toArray
      )
      .build()
  }

  def generate_schema(
      base_name: String,
      num_attributes: Int = 1,
      append_num: Boolean = true
  ): Schema = {
    val attrs: Iterable[Attribute] = Range
      .inclusive(1, num_attributes)
      .map(num =>
        new Attribute(base_name + (if (append_num) "#@" + num else ""), AttributeType.STRING)
      )
    Schema().add(attrs)
  }

  before {
    opDesc = new CartesianProductOpDesc()
  }

  it should "work with basic two input streams with no duplicate attribute names" in {
    val numLeftSchemaAttributes: Int = 3
    val numRightSchemaAttributes: Int = 3
    val numLeftTuples: Int = 5
    val numRightTuples: Int = 5

    val leftSchema = generate_schema("left", numLeftSchemaAttributes)
    val rightSchema = generate_schema("right", numRightSchemaAttributes)

    opExec = new CartesianProductOpExec()

    opExec.open()
    // process 5 left tuples
    (1 to numLeftTuples).map(value => {
      assert(
        opExec
          .processTuple(generate_tuple(leftSchema, Some(value)), leftPort)
          .isEmpty
      )
    })
    assert(opExec.onFinish(leftPort).isEmpty)

    // process 5 right tuples
    val outputTuples: List[TupleLike] = (numLeftTuples + 1 to numLeftTuples + numRightTuples)
      .map(value =>
        opExec
          .processTuple(generate_tuple(rightSchema, Some(value)), rightPort)
      )
      .foldLeft(Iterator[TupleLike]())(_ ++ _)
      .toList
    assert(opExec.onFinish(rightPort).isEmpty)

    // verify correct output size
    assert(outputTuples.size == numLeftTuples * numRightTuples)
    assert(
      outputTuples.head.getFields.length == numLeftSchemaAttributes + numRightSchemaAttributes
    )

    opExec.close()
  }

  it should "work with basic two input streams with duplicate attribute names" in {
    val numLeftSchemaAttributes: Int = 5
    val numRightSchemaAttributes: Int = 7
    val numLeftTuples: Int = 4
    val numRightTuples: Int = 3

    val duplicateAttribute: Attribute = new Attribute("left", AttributeType.STRING)
    val leftSchema = generate_schema("left", numLeftSchemaAttributes - 1)
      .add(duplicateAttribute)
    val rightSchema = generate_schema("right", numRightSchemaAttributes - 1)
      .add(duplicateAttribute)
    val inputSchemas = Map(PortIdentity() -> leftSchema, PortIdentity(1) -> rightSchema)
    val outputSchema = opDesc.getExternalOutputSchemas(inputSchemas).values.head

    // verify output schema is as expected & has no duplicates
    assert(
      outputSchema.getAttributeNames.toSet.size == outputSchema.getAttributeNames.size
    ) // no duplicates in output Schema
    // check left tuple attributes name remain same
    (0 until numLeftSchemaAttributes).map(index =>
      assert(
        leftSchema.getAttributeNames
          .apply(index)
          .equals(outputSchema.getAttributeNames.apply(index))
      )
    )
    // check right tuple attributes without duplicate names are handled
    (0 until numRightSchemaAttributes - 1).map(index =>
      assert(
        rightSchema.getAttributeNames
          .apply(index)
          .equals(outputSchema.getAttributeNames.apply(numLeftSchemaAttributes + index))
      )
    )
    // check right tuple attribute with duplicate name is handled
    val expectedAttrName: String = "left#@1#@1"
    assert(
      expectedAttrName.equals(
        outputSchema.getAttributeNames.apply(
          numLeftSchemaAttributes + numRightSchemaAttributes - 1
        )
      )
    )

    opExec = new CartesianProductOpExec()
    opExec.open()
    // process 4 left tuples
    (1 to numLeftTuples).map(value => {
      assert(
        opExec
          .processTuple(generate_tuple(leftSchema, Some(value)), leftPort)
          .isEmpty
      )
    })
    assert(opExec.onFinish(leftPort).isEmpty)

    // process 3 right tuples
    val outputTuples: List[TupleLike] = (numLeftTuples + 1 to numLeftTuples + numRightTuples)
      .map(value =>
        opExec
          .processTuple(generate_tuple(rightSchema, Some(value)), rightPort)
      )
      .foldLeft(Iterator[TupleLike]())(_ ++ _)
      .toList
    assert(opExec.onFinish(rightPort).isEmpty)

    // verify correct output size
    assert(outputTuples.size == numLeftTuples * numRightTuples)
    // verify output tuple like matches schema
    outputTuples.foreach(tupleLike =>
      tupleLike.asInstanceOf[SchemaEnforceable].enforceSchema(outputSchema)
    )
    opExec.close()
  }
}
