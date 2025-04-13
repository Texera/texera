package edu.uci.ics.amber.operator.visualization.hierarchychart

import com.fasterxml.jackson.annotation.{JsonProperty, JsonPropertyDescription}
import com.kjetland.jackson.jsonSchema.annotations.{JsonSchemaInject, JsonSchemaTitle}
import edu.uci.ics.amber.core.tuple.{AttributeType, Schema}
import edu.uci.ics.amber.operator.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.amber.operator.metadata.annotations.AutofillAttributeName
import edu.uci.ics.amber.core.workflow.OutputPort.OutputMode
import edu.uci.ics.amber.core.workflow.{InputPort, OutputPort, PortIdentity}
import edu.uci.ics.amber.operator.PythonOperatorDescriptor
// type constraint: value can only be numeric
@JsonSchemaInject(json = """
{
  "attributeTypeRules": {
    "value": {
      "enum": ["integer", "long", "double"]
    }
  }
}
""")
class HierarchyChartOpDesc extends PythonOperatorDescriptor {
  @JsonProperty(required = true)
  @JsonSchemaTitle("Chart Type")
  @JsonPropertyDescription("Treemap or Sunburst")
  var hierarchyChartType: HierarchyChartType = _

  @JsonProperty(required = true)
  @JsonSchemaTitle("Hierarchy Path")
  @JsonPropertyDescription(
    "Hierarchy of attributes from a higher-level category to lower-level category"
  )
  var hierarchy: List[HierarchySection] = List()

  @JsonProperty(value = "value", required = true)
  @JsonSchemaTitle("Value Column")
  @JsonPropertyDescription("The value associated with the size of each sector in the chart")
  @AutofillAttributeName
  var value: String = ""

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
      "Hierarchy Chart",
      "Visualize data in hierarchy",
      OperatorGroupConstants.VISUALIZATION_BASIC_GROUP,
      inputPorts = List(InputPort()),
      outputPorts = List(OutputPort(mode = OutputMode.SINGLE_SNAPSHOT))
    )

  private def getHierarchyAttributesInPython: String =
    hierarchy.map(_.attributeName).mkString("'", "','", "'")

  def manipulateTable(): String = {
    assert(value.nonEmpty)
    val attributes = getHierarchyAttributesInPython
    s"""
       |        table['$value'] = table[table['$value'] > 0]['$value'] # remove non-positive numbers from the data
       |        table.dropna(subset = [$attributes], inplace = True) #remove missing values
       |""".stripMargin
  }

  def createPlotlyFigure(): String = {
    assert(hierarchy.nonEmpty)
    val attributes = getHierarchyAttributesInPython
    s"""
       |        fig = px.${hierarchyChartType.getPlotlyExpressApiName}(table, path=[$attributes], values='$value',
       |                                                               color='$value', hover_data=[$attributes],
       |                                                               color_continuous_scale='RdBu')
       |""".stripMargin
  }

  override def generatePythonCode(): String = {
    val finalCode =
      s"""
         |from pytexera import *
         |
         |import plotly.express as px
         |import plotly.graph_objects as go
         |import plotly.io
         |import numpy as np
         |
         |class ProcessTableOperator(UDFTableOperator):
         |
         |    # Generate custom error message as html string
         |    def render_error(self, error_msg) -> str:
         |        return '''<h1>Hierarchy chart is not available.</h1>
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
         |        fig.update_layout(margin=dict(l=0, r=0, b=0, t=0))
         |        html = plotly.io.to_html(fig, include_plotlyjs='cdn', auto_play=False)
         |        yield {'html-content': html}
         |""".stripMargin
    finalCode
  }

}
