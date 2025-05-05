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

package edu.uci.ics.amber.operator.huggingFace

import com.fasterxml.jackson.annotation.{JsonProperty, JsonPropertyDescription}
import edu.uci.ics.amber.core.tuple.{AttributeType, Schema}
import edu.uci.ics.amber.operator.PythonOperatorDescriptor
import edu.uci.ics.amber.operator.metadata.annotations.AutofillAttributeName
import edu.uci.ics.amber.operator.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.amber.core.workflow.{InputPort, OutputPort, PortIdentity}
class HuggingFaceSpamSMSDetectionOpDesc extends PythonOperatorDescriptor {
  @JsonProperty(value = "attribute", required = true)
  @JsonPropertyDescription("column to perform spam detection on")
  @AutofillAttributeName
  var attribute: String = _

  @JsonProperty(
    value = "Spam result attribute",
    required = true,
    defaultValue = "is_spam"
  )
  @JsonPropertyDescription("column name of whether spam or not")
  var resultAttributeSpam: String = _

  @JsonProperty(
    value = "Score result attribute",
    required = true,
    defaultValue = "score"
  )
  @JsonPropertyDescription("column name of Probability for classification")
  var resultAttributeProbability: String = _

  override def generatePythonCode(): String = {
    s"""from transformers import pipeline
       |from pytexera import *
       |
       |class ProcessTupleOperator(UDFOperatorV2):
       |
       |    def open(self):
       |        self.pipeline = pipeline("text-classification", model="mrm8488/bert-tiny-finetuned-sms-spam-detection")
       |
       |    @overrides
       |    def process_tuple(self, tuple_: Tuple, port: int) -> Iterator[Optional[TupleLike]]:
       |        result = self.pipeline(tuple_["$attribute"])[0]
       |        tuple_["$resultAttributeSpam"] = (result["label"] == "LABEL_1")
       |        tuple_["$resultAttributeProbability"] = result["score"]
       |        yield tuple_""".stripMargin
  }

  override def operatorInfo: OperatorInfo =
    OperatorInfo(
      "Hugging Face Spam Detection",
      "Spam Detection by SMS Spam Detection Model from Hugging Face",
      OperatorGroupConstants.HUGGINGFACE_GROUP,
      inputPorts = List(InputPort()),
      outputPorts = List(OutputPort())
    )

  override def getOutputSchemas(
      inputSchemas: Map[PortIdentity, Schema]
  ): Map[PortIdentity, Schema] = {
    Map(
      operatorInfo.outputPorts.head.id -> inputSchemas.values.head
        .add(resultAttributeSpam, AttributeType.BOOLEAN)
        .add(resultAttributeProbability, AttributeType.DOUBLE)
    )
  }
}
