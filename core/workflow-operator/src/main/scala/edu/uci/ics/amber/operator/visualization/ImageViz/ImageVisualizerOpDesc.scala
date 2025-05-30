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

package edu.uci.ics.amber.operator.visualization.ImageViz

import com.fasterxml.jackson.annotation.{JsonProperty, JsonPropertyDescription}
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle
import edu.uci.ics.amber.core.tuple.{AttributeType, Schema}
import edu.uci.ics.amber.core.workflow.OutputPort.OutputMode
import edu.uci.ics.amber.core.workflow.{InputPort, OutputPort, PortIdentity}
import edu.uci.ics.amber.operator.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.amber.operator.metadata.annotations.AutofillAttributeName
import edu.uci.ics.amber.operator.PythonOperatorDescriptor
class ImageVisualizerOpDesc extends PythonOperatorDescriptor {

  @JsonProperty(required = true)
  @JsonSchemaTitle("image content column")
  @JsonPropertyDescription("The Binary data of the Image")
  @AutofillAttributeName
  var binaryContent: String = _

  override def getOutputSchemas(
      inputSchemas: Map[PortIdentity, Schema]
  ): Map[PortIdentity, Schema] = {
    val outputSchema = Schema()
      .add("html-content", AttributeType.STRING)
    Map(operatorInfo.outputPorts.head.id -> outputSchema)
    Map(operatorInfo.outputPorts.head.id -> outputSchema)
  }

  override def operatorInfo: OperatorInfo =
    OperatorInfo(
      "Image Visualizer",
      "visualize image content",
      OperatorGroupConstants.VISUALIZATION_MEDIA_GROUP,
      inputPorts = List(InputPort()),
      outputPorts = List(OutputPort(mode = OutputMode.SINGLE_SNAPSHOT))
    )

  def createBinaryData(): String = {
    assert(binaryContent.nonEmpty)
    s"""
       |        binary_image_data = tuple_['$binaryContent']
       |""".stripMargin
  }

  override def generatePythonCode(): String = {
    val finalCode =
      s"""
         |from pytexera import *
         |import base64
         |from io import BytesIO
         |
         |class ProcessTupleOperator(UDFOperatorV2):
         |    images_html = []
         |
         |    def render_error(self, error_msg):
         |        return f'<h1>Image is not available.</h1><p>Reason: {error_msg}</p>'
         |
         |    def encode_image_to_html(self, binary_image_data):
         |        try:
         |            encoded_image_data = base64.b64encode(binary_image_data)
         |            encoded_image_str = encoded_image_data.decode("utf-8")
         |            html = f'<img src="data:image;base64,{encoded_image_str}" alt="Image" style="max-width: 100vw; max-height: 90vh; width: auto; height: auto;">'
         |            return html
         |        except Exception as e:
         |            return self.render_error("Binary input is not valid")
         |
         |    @overrides
         |    def process_tuple(self, tuple_: Tuple, port: int) -> Iterator[Optional[TupleLike]]:
         |        ${createBinaryData()}
         |        self.images_html.append(self.encode_image_to_html(binary_image_data))
         |        yield
         |
         |    @overrides
         |    def on_finish(self, port: int) -> Iterator[Optional[TupleLike]]:
         |        all_images_html = "<div>" + "".join(self.images_html) + "</div>"
         |        yield {"html-content": all_images_html}
         |""".stripMargin
    finalCode
  }

}
