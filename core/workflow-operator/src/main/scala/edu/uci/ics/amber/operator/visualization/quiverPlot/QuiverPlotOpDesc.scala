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

package edu.uci.ics.amber.operator.visualization.quiverPlot

import com.fasterxml.jackson.annotation.{JsonProperty, JsonPropertyDescription}
import com.kjetland.jackson.jsonSchema.annotations.{JsonSchemaInject, JsonSchemaTitle}
import edu.uci.ics.amber.core.tuple.{AttributeType, Schema}
import edu.uci.ics.amber.operator.PythonOperatorDescriptor
import edu.uci.ics.amber.core.workflow.OutputPort.OutputMode
import edu.uci.ics.amber.core.workflow.{InputPort, OutputPort, PortIdentity}
import edu.uci.ics.amber.operator.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.amber.operator.metadata.annotations.AutofillAttributeName

@JsonSchemaInject(json = """
{
  "attributeTypeRules": {
    "value": {
      "enum": ["integer", "long", "double"]
    }
  }
}
""")
class QuiverPlotOpDesc extends PythonOperatorDescriptor {

  //property panel variable: 4 requires: {x,y,u,v}, all columns should only contain numerical data

  @JsonProperty(value = "x", required = true)
  @JsonSchemaTitle("x")
  @JsonPropertyDescription("Column for the x-coordinate of the starting point")
  @AutofillAttributeName var x: String = ""

  @JsonProperty(value = "y", required = true)
  @JsonSchemaTitle("y")
  @JsonPropertyDescription("Column for the y-coordinate of the starting point")
  @AutofillAttributeName var y: String = ""

  @JsonProperty(value = "u", required = true)
  @JsonSchemaTitle("u")
  @JsonPropertyDescription("Column for the vector component in the x-direction")
  @AutofillAttributeName var u: String = ""

  @JsonProperty(value = "v", required = true)
  @JsonSchemaTitle("v")
  @JsonPropertyDescription("Column for the vector component in the y-direction")
  @AutofillAttributeName var v: String = ""

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
      "Quiver Plot",
      "Visualize vector data in a Quiver Plot",
      OperatorGroupConstants.VISUALIZATION_SCIENTIFIC_GROUP,
      inputPorts = List(InputPort()),
      outputPorts = List(OutputPort(mode = OutputMode.SINGLE_SNAPSHOT))
    )

  //data cleaning for missing value
  def manipulateTable(): String = {
    s"""
       |        table = table.dropna() #remove missing values
       |""".stripMargin
  }

  override def generatePythonCode(): String = {
    val finalCode =
      s"""
         |from pytexera import *
         |import pandas as pd
         |import plotly.figure_factory as ff
         |import numpy as np
         |import plotly.io
         |import plotly.graph_objects as go
         |
         |class ProcessTableOperator(UDFTableOperator):
         |
         |    def render_error(self, error_msg):
         |        return '''<h1>Quiver Plot is not available.</h1>
         |                  <p>Reasons are: {} </p>
         |               '''.format(error_msg)
         |
         |    @overrides
         |    def process_table(self, table: Table, port: int) -> Iterator[Optional[TableLike]]:
         |        if table.empty:
         |            yield {'html-content': self.render_error("Input table is empty.")}
         |            return
         |
         |        required_columns = {'${x}', '${y}', '${u}', '${v}'}
         |        if not required_columns.issubset(table.columns):
         |            yield {'html-content': self.render_error(f"Input table must contain columns: {', '.join(required_columns)}")}
         |            return
         |
         |        ${manipulateTable()}
         |
         |        def type_check(value):
         |            return isinstance(value,(int,float))
         |        for col in required_columns:
         |            if not table[col].apply(type_check).all():
         |                yield {"html-content": "Type error: All columns should only contain numerical data"}
         |                return
         |
         |        try:
         |            #graph the quiver plot
         |            fig = ff.create_quiver(
         |                table['${x}'], table['${y}'],
         |                table['${u}'], table['${v}'],
         |                scale=0.1
         |            )
         |            html = fig.to_html(include_plotlyjs='cdn', full_html=False)
         |        except Exception as e:
         |            yield {'html-content': self.render_error(f"Plotly error: {str(e)}")}
         |            return
         |
         |        html = plotly.io.to_html(fig, include_plotlyjs='cdn', auto_play=False)
         |        yield {'html-content': html}
         |""".stripMargin
    finalCode
  }

}
