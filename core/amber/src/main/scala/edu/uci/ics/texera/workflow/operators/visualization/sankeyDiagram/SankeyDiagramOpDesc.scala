package edu.uci.ics.texera.workflow.operators.visualization.sankeyDiagram

import com.fasterxml.jackson.annotation.{JsonProperty, JsonPropertyDescription}
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle
import edu.uci.ics.texera.workflow.common.metadata.annotations.AutofillAttributeName
import edu.uci.ics.texera.workflow.common.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.texera.workflow.common.operators.PythonOperatorDescriptor
import edu.uci.ics.texera.workflow.common.tuple.schema.{Attribute, AttributeType, Schema}
import edu.uci.ics.amber.engine.common.workflow.{InputPort, OutputPort}
import edu.uci.ics.texera.workflow.operators.visualization.{
  VisualizationConstants,
  VisualizationOperator
}

class SankeyDiagramOpDesc extends VisualizationOperator with PythonOperatorDescriptor {

  @JsonProperty(value = "Source Attribute", required = true)
  @JsonSchemaTitle("Source Attribute")
  @JsonPropertyDescription("the source node of the Sankey diagram")
  @AutofillAttributeName
  var sourceAttribute: String = ""

  @JsonProperty(value = "Target Attribute", required = true)
  @JsonSchemaTitle("Target Attribute")
  @JsonPropertyDescription("the target node of the Sankey diagram")
  @AutofillAttributeName
  var targetAttribute: String = ""

  @JsonProperty(value = "Value Attribute", required = true)
  @JsonSchemaTitle("Value Attribute")
  @JsonPropertyDescription("the value/volume of the flow between source and target")
  @AutofillAttributeName
  var valueAttribute: String = ""

  override def getOutputSchema(schemas: Array[Schema]): Schema = {
    Schema.builder().add(new Attribute("html-content", AttributeType.STRING)).build()
  }

  override def operatorInfo: OperatorInfo =
    OperatorInfo(
      "Sankey Diagram",
      "Visualize data using a Sankey diagram",
      OperatorGroupConstants.VISUALIZATION_GROUP,
      inputPorts = List(InputPort()),
      outputPorts = List(OutputPort())
    )

  def createPlotlyFigure(): String = {
    s"""
       |        # Grouping source, target, and summing value for the Sankey diagram
       |        table = table.groupby(['$sourceAttribute', '$targetAttribute'])['$valueAttribute'].sum().reset_index(name='value')
       |
       |        # Create a list of unique labels from both source and target
       |        labels = pd.concat([table['$sourceAttribute'], table['$targetAttribute']]).unique().tolist()
       |
       |        # Create indices for source and target from the label list
       |        table['source_index'] = table['$sourceAttribute'].apply(lambda x: labels.index(x))
       |        table['target_index'] = table['$targetAttribute'].apply(lambda x: labels.index(x))
       |
       |        # Create the Sankey diagram
       |        fig = go.Figure(data=[go.Sankey(
       |            node=dict(
       |                pad=15,
       |                thickness=20,
       |                line=dict(color="black", width=0.5),
       |                label=labels,
       |                color="blue"
       |            ),
       |            link=dict(
       |                source=table['source_index'].tolist(),
       |                target=table['target_index'].tolist(),
       |                value=table['value'].tolist()
       |            )
       |        )])
       |
       |        fig.update_layout(title_text="Sankey Diagram", font_size=10)
       |""".stripMargin
  }

  override def generatePythonCode(): String = {
    val finalCode = s"""
                       |from pytexera import *
                       |import plotly.graph_objects as go
                       |import plotly.io
                       |import pandas as pd
                       |
                       |class ProcessTableOperator(UDFTableOperator):
                       |
                       |    def render_error(self, error_msg):
                       |        return '''<h1>Sankey Diagram is not available.</h1>
                       |                  <p>Reasons are: {} </p>
                       |               '''.format(error_msg)
                       |
                       |    @overrides
                       |    def process_table(self, table: Table, port: int) -> Iterator[Optional[TableLike]]:
                       |        if table.empty:
                       |            yield {'html-content': self.render_error("Input table is empty.")}
                       |            return
                       |        ${createPlotlyFigure()}
                       |        if table.empty:
                       |            yield {'html-content': self.render_error("No valid rows left (every row has at least 1 missing value).")}
                       |            return
                       |        # convert fig to html content
                       |        html = plotly.io.to_html(fig, include_plotlyjs='cdn', auto_play=False)
                       |        yield {'html-content': html}
                       |""".stripMargin
    finalCode
  }

  // make the chart type to html visualization so it can be recognized by both backend and frontend.
  override def chartType(): String = VisualizationConstants.HTML_VIZ
}
