package edu.uci.ics.amber.operator.visualization.boxViolinPlot

import com.fasterxml.jackson.annotation.{JsonProperty, JsonPropertyDescription}
import com.kjetland.jackson.jsonSchema.annotations.{JsonSchemaInject, JsonSchemaTitle}
import edu.uci.ics.amber.core.tuple.{AttributeType, Schema}
import edu.uci.ics.amber.core.workflow.OutputPort.OutputMode
import edu.uci.ics.amber.core.workflow.{InputPort, OutputPort, PortIdentity}
import edu.uci.ics.amber.operator.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.amber.operator.metadata.annotations.AutofillAttributeName
import edu.uci.ics.amber.operator.PythonOperatorDescriptor
@JsonSchemaInject(json = """
{
  "attributeTypeRules": {
    "value": {
      "enum": ["integer", "long", "double"]
    }
  }
}
""")
class BoxViolinPlotOpDesc extends PythonOperatorDescriptor {

  @JsonProperty(value = "value", required = true)
  @JsonSchemaTitle("Value Column")
  @JsonPropertyDescription("Data Column for Boxplot")
  @AutofillAttributeName
  var value: String = ""

  @JsonProperty(defaultValue = "false")
  @JsonSchemaTitle("Horizontal Orientation")
  @JsonPropertyDescription("Orientation Style")
  var orientation: Boolean = _

  @JsonProperty(defaultValue = "false")
  @JsonSchemaTitle("Violin Plot")
  @JsonPropertyDescription("Use Violin Plot")
  var violinplot: Boolean = _

  @JsonProperty(
    value = "Quartile Method",
    required = true,
    defaultValue = "linear"
  )
  var quertiletype: BoxPlotQuartileFunction = _

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
      "Box/Violin Plot",
      "Visualize data using either a Box Plot or a Violin Plot. Boxplots are drawn as a box with a vertical line down the middle which is mean value, and has horizontal lines attached to each side (known as “whiskers”). Violin plots are similar to box plots, but with a rotated kernel density plot on each side, providing more insight into the distribution shape.",
      OperatorGroupConstants.VISUALIZATION_GROUP,
      inputPorts = List(InputPort()),
      outputPorts = List(OutputPort(mode = OutputMode.SINGLE_SNAPSHOT))
    )

  def manipulateTable(): String = {
    assert(value.nonEmpty)

    s"""
       |        table = table.dropna(subset = ['$value']) #remove missing values
       |
       |""".stripMargin
  }

  def createPlotlyFigure(): String = {
    val horizontal = if (orientation) "True" else "False"
    val violin = if (violinplot) "True" else "False"
    s"""
       |        if($violin):
       |            if ($horizontal):
       |                fig = px.violin(table, x='$value', box=True, points='all')
       |            else:
       |                fig = px.violin(table, y='$value', box=True, points='all')
       |        else:
       |            if($horizontal):
       |                fig = px.box(table, x='$value',boxmode="overlay", points='all')
       |            else:
       |                fig = px.box(table, y='$value',boxmode="overlay", points='all')
       |            fig.update_traces(quartilemethod="${quertiletype.getQuartiletype}", jitter=0, col=1)
       |
       |        fig.update_layout(margin=dict(t=0, b=0, l=0, r=0))
       |""".stripMargin
  }

  override def generatePythonCode(): String = {

    val finalCode =
      s"""
         |from pytexera import *
         |
         |import plotly.express as px
         |import pandas as pd
         |import plotly.graph_objects as go
         |import plotly.io
         |import json
         |import pickle
         |import plotly
         |
         |class ProcessTableOperator(UDFTableOperator):
         |
         |    # Generate custom error message as html string
         |    def render_error(self, error_msg) -> str:
         |        return '''<h1>Box/Violin Plot is not available.</h1>
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
         |           yield {'html-content': self.render_error("value column contains only non-positive numbers or nulls.")}
         |           return
         |        ${createPlotlyFigure()}
         |        # convert fig to html content
         |        html = plotly.io.to_html(fig, include_plotlyjs='cdn', auto_play=False)
         |        yield {'html-content': html}
         |        """.stripMargin
    finalCode
  }

}
