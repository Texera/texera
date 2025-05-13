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

package edu.uci.ics.amber.operator.metadata

/**
  * PropertyNameConstants defines the key names
  * in the JSON representation of each operator.
  *
  * @author Zuozhi Wang
  */
object PropertyNameConstants { // logical plan property names
  final val OPERATOR_ID = "operatorID"
  final val OPERATOR_TYPE = "operatorType"
  final val ORIGIN_OPERATOR_ID = "origin"
  final val DESTINATION_OPERATOR_ID = "destination"
  final val OPERATOR_LIST = "operators"
  final val OPERATOR_LINK_LIST = "links"
  final val OPERATOR_VERSION = "operatorVersion"
  // common operator property names
  final val ATTRIBUTE_NAMES = "attributes"
  final val ATTRIBUTE_NAME = "attribute"
  final val RESULT_ATTRIBUTE_NAME = "resultAttribute"
  final val SPAN_LIST_NAME = "spanListName"
  final val TABLE_NAME = "tableName"

  // physical plan property names
  final val WORKFLOW_ID = "workflowID"
  final val EXECUTION_ID = "executionID"
  final val PARALLELIZABLE = "parallelizable"
  final val LOCATION_PREFERENCE = "locationPreference"
  final val PARTITION_REQUIREMENT = "partitionRequirement"
  // derivePartition is a function type that cannot be serialized
  final val INPUT_PORTS = "inputPorts"
  final val OUTPUT_PORTS = "outputPorts"
  // propagateSchema is a function type that cannot be serialized
  final val IS_ONE_TO_MANY_OP = "isOneToManyOp"
  final val SUGGESTED_WORKER_NUM = "suggestedWorkerNum"
}
