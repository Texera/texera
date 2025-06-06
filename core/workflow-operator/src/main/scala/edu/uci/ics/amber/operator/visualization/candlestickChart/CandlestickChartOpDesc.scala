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

package edu.uci.ics.amber.operator.visualization.candlestickChart

import com.fasterxml.jackson.annotation.{JsonProperty, JsonPropertyDescription}
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle
import edu.uci.ics.amber.core.tuple.{AttributeType, Schema}
import edu.uci.ics.amber.operator.PythonOperatorDescriptor
import edu.uci.ics.amber.operator.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.amber.operator.metadata.annotations.AutofillAttributeName
import edu.uci.ics.amber.core.workflow.OutputPort.OutputMode
import edu.uci.ics.amber.core.workflow.{InputPort, OutputPort, PortIdentity}

class CandlestickChartOpDesc extends PythonOperatorDescriptor {

  @JsonProperty(value = "date", required = true)
  @JsonSchemaTitle("Date Column")
  @JsonPropertyDescription("the date of the candlestick")
  @AutofillAttributeName
  var date: String = ""

  @JsonProperty(value = "open", required = true)
  @JsonSchemaTitle("Opening Price Column")
  @JsonPropertyDescription("the opening price of the candlestick")
  @AutofillAttributeName
  var open: String = ""

  @JsonProperty(value = "high", required = true)
  @JsonSchemaTitle("Highest Price Column")
  @JsonPropertyDescription("the highest price of the candlestick")
  @AutofillAttributeName
  var high: String = ""

  @JsonProperty(value = "low", required = true)
  @JsonSchemaTitle("Lowest Price Column")
  @JsonPropertyDescription("the lowest price of the candlestick")
  @AutofillAttributeName
  var low: String = ""

  @JsonProperty(value = "close", required = true)
  @JsonSchemaTitle("Closing Price Column")
  @JsonPropertyDescription("the closing price of the candlestick")
  @AutofillAttributeName
  var close: String = ""

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
      "Candlestick Chart",
      "Visualize data in a Candlestick Chart",
      OperatorGroupConstants.VISUALIZATION_FINANCIAL_GROUP,
      inputPorts = List(InputPort()),
      outputPorts = List(OutputPort(mode = OutputMode.SINGLE_SNAPSHOT))
    )

  override def generatePythonCode(): String = {
    s"""
       |from pytexera import *
       |
       |import plotly.graph_objects as go
       |import pandas as pd
       |
       |class ProcessTableOperator(UDFTableOperator):
       |
       |    @overrides
       |    def process_table(self, table: Table, port: int) -> Iterator[Optional[TableLike]]:
       |        # Convert table to dictionary
       |        table_dict = table.to_dict()
       |
       |        # Create a DataFrame from the dictionary
       |        df = pd.DataFrame(table_dict)
       |
       |        fig = go.Figure(data=[go.Candlestick(
       |            x=df['$date'],
       |            open=df['$open'],
       |            high=df['$high'],
       |            low=df['$low'],
       |            close=df['$close']
       |        )])
       |        fig.update_layout(title='Candlestick Chart')
       |        html = fig.to_html(include_plotlyjs='cdn', full_html=False)
       |        yield {'html-content': html}
       |""".stripMargin
  }

}
