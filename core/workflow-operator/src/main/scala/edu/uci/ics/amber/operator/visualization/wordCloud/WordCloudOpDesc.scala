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

package edu.uci.ics.amber.operator.visualization.wordCloud

import com.fasterxml.jackson.annotation.JsonProperty
import com.kjetland.jackson.jsonSchema.annotations.{
  JsonSchemaInject,
  JsonSchemaInt,
  JsonSchemaTitle
}
import edu.uci.ics.amber.core.tuple.{AttributeType, Schema}
import edu.uci.ics.amber.core.workflow.OutputPort.OutputMode
import edu.uci.ics.amber.core.workflow.{InputPort, OutputPort, PortIdentity}
import edu.uci.ics.amber.operator.PythonOperatorDescriptor
import edu.uci.ics.amber.operator.metadata.annotations.AutofillAttributeName
import edu.uci.ics.amber.operator.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.amber.operator.visualization.ImageUtility
class WordCloudOpDesc extends PythonOperatorDescriptor {
  @JsonProperty(required = true)
  @JsonSchemaTitle("Text column")
  @AutofillAttributeName
  var textColumn: String = ""

  @JsonProperty(defaultValue = "100")
  @JsonSchemaTitle("Number of most frequent words")
  @JsonSchemaInject(ints = Array(new JsonSchemaInt(path = "exclusiveMinimum", value = 0)))
  var topN: Integer = 100

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
      "Word Cloud",
      "Generate word cloud for   texts",
      OperatorGroupConstants.VISUALIZATION_MEDIA_GROUP,
      inputPorts = List(InputPort()),
      outputPorts = List(OutputPort(mode = OutputMode.SINGLE_SNAPSHOT))
    )

  def manipulateTable(): String = {
    s"""
       |        table.dropna(subset = ['$textColumn'], inplace = True) #remove missing values
       |        table = table[table['$textColumn'].str.contains(r'\\w', regex=True)]
       |""".stripMargin
  }

  def createWordCloudFigure(): String = {
    s"""
       |        text = ' '.join(table['$textColumn'])
       |
       |        # Generate an image in a FHD resolution
       |        from wordcloud import WordCloud, STOPWORDS
       |        wordcloud = WordCloud(width=1920, height=1080, stopwords=set(STOPWORDS), max_words=$topN, background_color='white', include_numbers=True).generate(text)
       |
       |        from io import BytesIO
       |        image_stream = BytesIO()
       |        wordcloud.to_image().save(image_stream, format='PNG')
       |        binary_image_data = image_stream.getvalue()
       |""".stripMargin
  }

  override def generatePythonCode(): String = {
    val finalCode =
      s"""
         |from pytexera import *
         |
         |class ProcessTableOperator(UDFTableOperator):
         |
         |    # Generate custom error message as html string
         |    def render_error(self, error_msg) -> str:
         |        return '''<h1>Wordcloud is not available.</h1>
         |                  <p>Reason is: {} </p>
         |               '''.format(error_msg)
         |
         |    @overrides
         |    def process_table(self, table: Table, port: int) -> Iterator[Optional[TableLike]]:
         |        if table.empty:
         |           yield {'html-content': self.render_error("input table is empty.")}
         |           return
         |        ${manipulateTable()}
         |        if table.empty:
         |           yield {'html-content': self.render_error("text column does not contain words or contains only nulls.")}
         |           return
         |        ${createWordCloudFigure()}
         |        ${ImageUtility.encodeImageToHTML()}
         |        yield {'html-content': html}
         |""".stripMargin

    print(finalCode)
    finalCode
  }
}
