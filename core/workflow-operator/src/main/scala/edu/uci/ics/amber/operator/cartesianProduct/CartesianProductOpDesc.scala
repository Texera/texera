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

import edu.uci.ics.amber.core.executor.OpExecWithClassName
import edu.uci.ics.amber.core.tuple.{Attribute, Schema}
import edu.uci.ics.amber.core.virtualidentity.{ExecutionIdentity, WorkflowIdentity}
import edu.uci.ics.amber.core.workflow._
import edu.uci.ics.amber.operator.LogicalOp
import edu.uci.ics.amber.operator.metadata.{OperatorGroupConstants, OperatorInfo}

class CartesianProductOpDesc extends LogicalOp {
  override def getPhysicalOp(
      workflowId: WorkflowIdentity,
      executionId: ExecutionIdentity
  ): PhysicalOp = {
    PhysicalOp
      .oneToOnePhysicalOp(
        workflowId,
        executionId,
        operatorIdentifier,
        OpExecWithClassName("edu.uci.ics.amber.operator.cartesianProduct.CartesianProductOpExec")
      )
      .withInputPorts(operatorInfo.inputPorts)
      .withOutputPorts(operatorInfo.outputPorts)
      .withPropagateSchema(
        SchemaPropagationFunc(inputSchemas => {

          // Combines the left and right input schemas into a single output schema.
          //
          // - The output schema includes all attributes from the left schema first, followed by
          //   attributes from the right schema.
          // - Duplicate attribute names are resolved by appending an increasing suffix (e.g., `#@1`, `#@2`).
          // - Attributes from the left schema retain their original names in the output schema.
          //
          // Example:
          // Left schema: (dup, dup#@1, dup#@2)
          // Right schema: (r1, r2, dup)
          // Output schema: (dup, dup#@1, dup#@2, r1, r2, dup#@3)
          //
          // In this example, the last attribute from the right schema (`dup`) is renamed to `dup#@3`
          // to avoid conflicts.

          var outputSchema = Schema()
          val leftSchema = inputSchemas(operatorInfo.inputPorts.head.id)
          val rightSchema = inputSchemas(operatorInfo.inputPorts.last.id)
          val leftAttributeNames = leftSchema.getAttributeNames
          val rightAttributeNames = rightSchema.getAttributeNames
          outputSchema = outputSchema.add(leftSchema)
          rightSchema.getAttributes.foreach(attr => {
            var newName = attr.getName
            while (
              leftAttributeNames.contains(newName) || rightAttributeNames
                .filterNot(attrName => attrName == attr.getName)
                .contains(newName)
            ) {
              newName = s"$newName#@1"
            }
            if (newName == attr.getName) {
              // non-duplicate attribute, add to builder as is
              outputSchema = outputSchema.add(attr)
            } else {
              // renamed the duplicate attribute, construct new Attribute
              outputSchema = outputSchema.add(new Attribute(newName, attr.getType))
            }
          })
          Map(operatorInfo.outputPorts.head.id -> outputSchema)
        })
      )
      // TODO : refactor to parallelize this operator for better performance and scalability:
      //  can consider hash partition on larger input, broadcast smaller table to each partition
      .withParallelizable(false)

  }

  override def operatorInfo: OperatorInfo =
    OperatorInfo(
      "Cartesian Product",
      "Append fields together to get the cartesian product of two inputs",
      OperatorGroupConstants.JOIN_GROUP,
      inputPorts = List(
        InputPort(PortIdentity(), displayName = "left"),
        InputPort(PortIdentity(1), displayName = "right", dependencies = List(PortIdentity()))
      ),
      outputPorts = List(OutputPort())
    )
}
