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

package edu.uci.ics.amber.operator.sklearn

import com.fasterxml.jackson.annotation.{JsonProperty, JsonPropertyDescription}
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle
import edu.uci.ics.amber.core.tuple.{AttributeType, Schema}
import edu.uci.ics.amber.operator.PythonOperatorDescriptor
import edu.uci.ics.amber.operator.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.amber.operator.metadata.annotations.AutofillAttributeName
import edu.uci.ics.amber.core.workflow.{InputPort, OutputPort, PortIdentity}

class SklearnLinearRegressionOpDesc extends PythonOperatorDescriptor {

  @JsonSchemaTitle("Target Attribute")
  @JsonPropertyDescription("Attribute in your dataset corresponding to target.")
  @JsonProperty(required = true)
  @AutofillAttributeName
  var target: String = _

  @JsonSchemaTitle("Degree")
  @JsonPropertyDescription("Degree of polynomial function")
  @JsonProperty(required = true)
  val degree: Int = 1

  override def generatePythonCode(): String =
    s"""
       |from sklearn.metrics import accuracy_score, f1_score, precision_score, recall_score, mean_absolute_error, r2_score
       |from sklearn.pipeline import make_pipeline
       |from sklearn.linear_model import LinearRegression
       |from sklearn.preprocessing import PolynomialFeatures
       |import numpy as np
       |from pytexera import *
       |class ProcessTableOperator(UDFTableOperator):
       |    @overrides
       |    def process_table(self, table: Table, port: int) -> Iterator[Optional[TableLike]]:
       |        Y = table["$target"]
       |        X = table.drop("$target", axis=1)
       |        if port == 0:
       |            pipeline = make_pipeline(
       |                PolynomialFeatures(degree=$degree),
       |                LinearRegression()
       |            )
       |            self.model = pipeline.fit(X, Y)
       |        else:
       |            predictions = self.model.predict(X)
       |            mae = round(mean_absolute_error(Y, predictions), 4)
       |            r2 = round(r2_score(Y, predictions), 4)
       |            print("MAE:", mae, ", R2:", r2)
       |            yield {"model_name" : "LinearRegression", "model" : self.model}""".stripMargin

  override def operatorInfo: OperatorInfo =
    OperatorInfo(
      "Linear Regression",
      "Sklearn Linear Regression Operator",
      OperatorGroupConstants.SKLEARN_GROUP,
      inputPorts = List(
        InputPort(PortIdentity(), "training"),
        InputPort(PortIdentity(1), "testing", dependencies = List(PortIdentity()))
      ),
      outputPorts = List(OutputPort(blocking = true))
    )

  override def getOutputSchemas(
      inputSchemas: Map[PortIdentity, Schema]
  ): Map[PortIdentity, Schema] = {
    Map(
      operatorInfo.outputPorts.head.id -> Schema()
        .add("model_name", AttributeType.STRING)
        .add("model", AttributeType.BINARY)
    )
  }

}
