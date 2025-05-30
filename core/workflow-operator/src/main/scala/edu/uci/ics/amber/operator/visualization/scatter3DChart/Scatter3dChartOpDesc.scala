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

package edu.uci.ics.amber.operator.visualization.scatter3DChart

import com.fasterxml.jackson.annotation.{JsonProperty, JsonPropertyDescription}
import com.kjetland.jackson.jsonSchema.annotations.{JsonSchemaInject, JsonSchemaTitle}
import edu.uci.ics.amber.core.tuple.{AttributeType, Schema}
import edu.uci.ics.amber.core.workflow.{InputPort, OutputPort, PortIdentity}
import edu.uci.ics.amber.operator.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.amber.operator.metadata.annotations.AutofillAttributeName
import edu.uci.ics.amber.operator.PythonOperatorDescriptor
import edu.uci.ics.amber.core.workflow.OutputPort.OutputMode
@JsonSchemaInject(json = """
{
  "attributeTypeRules": {
    "title": "string"
  }
}
""")
class Scatter3dChartOpDesc extends PythonOperatorDescriptor {
  @JsonProperty(value = "x", required = true)
  @JsonSchemaTitle("X Column")
  @JsonPropertyDescription("Data column for the x-axis")
  @AutofillAttributeName
  var x: String = ""

  @JsonProperty(value = "y", required = true)
  @JsonSchemaTitle("Y Column")
  @JsonPropertyDescription("Data column for the y-axis")
  @AutofillAttributeName
  var y: String = ""

  @JsonProperty(value = "z", required = true)
  @JsonSchemaTitle("Z Column")
  @JsonPropertyDescription("Data column for the z-axis")
  @AutofillAttributeName
  var z: String = ""

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
      "Scatter3D Chart",
      "Visualize data in a Scatter3D Plot",
      OperatorGroupConstants.VISUALIZATION_ADVANCED_GROUP,
      inputPorts = List(InputPort()),
      outputPorts = List(OutputPort(mode = OutputMode.SINGLE_SNAPSHOT))
    )

  private def createPlotlyFigure(): String = {
    assert(x.nonEmpty)
    assert(y.nonEmpty)
    assert(z.nonEmpty)
    s"""
       |        fig = go.Figure(data=[go.Scatter3d(
       |            x=table["$x"],
       |            y=table["$y"],
       |            z=table["$z"],
       |            mode='markers',
       |            marker=dict(
       |                size=12,
       |                colorscale='Viridis',
       |                opacity=0.8
       |            )
       |        )])
       |        fig.update_traces(marker=dict(size=5, opacity=0.8))
       |        fig.update_layout(
       |            scene=dict(
       |                xaxis_title='X: $x',
       |                yaxis_title='Y: $y',
       |                zaxis_title='Z: $z'
       |            ),
       |            margin=dict(t=0, b=0, l=0, r=0)
       |        )
       |""".stripMargin
  }

  override def generatePythonCode(): String = {
    val finalcode =
      s"""
         |from pytexera import *
         |
         |import plotly.express as px
         |import plotly.graph_objects as go
         |import plotly.io
         |import pandas as pd
         |import numpy as np
         |
         |class ProcessTableOperator(UDFTableOperator):
         |    def render_error(self, error_msg):
         |        return '''<h1>Chart is not available.</h1>
         |                  <p>Reason is: {} </p>
         |               '''.format(error_msg)
         |
         |    @overrides
         |    def process_table(self, table: Table, port: int) -> Iterator[Optional[TableLike]]:
         |        if table.empty:
         |           yield {'html-content': self.render_error("input table is empty.")}
         |           return
         |        ${createPlotlyFigure()}
         |        # convert fig to html content
         |        html = plotly.io.to_html(fig, include_plotlyjs='cdn', auto_play=False)
         |        yield {'html-content': html}
         |
         |""".stripMargin
    finalcode
  }
}
