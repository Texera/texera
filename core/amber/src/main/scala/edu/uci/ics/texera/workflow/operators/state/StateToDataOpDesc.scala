package edu.uci.ics.texera.workflow.operators.state

import com.fasterxml.jackson.annotation.{JsonProperty, JsonPropertyDescription}
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle
import edu.uci.ics.amber.engine.architecture.deploysemantics.{PhysicalOp, SchemaPropagationFunc}
import edu.uci.ics.amber.engine.architecture.deploysemantics.layer.OpExecInitInfo
import edu.uci.ics.amber.engine.common.virtualidentity.{ExecutionIdentity, WorkflowIdentity}
import edu.uci.ics.amber.engine.common.workflow.{InputPort, OutputPort, PortIdentity}
import edu.uci.ics.texera.workflow.common.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.texera.workflow.common.operators.LogicalOp
import edu.uci.ics.texera.workflow.common.tuple.schema.{Attribute, AttributeType, Schema}

class StateToDataOpDesc extends LogicalOp {
  @JsonProperty
  @JsonSchemaTitle("State output column(s)")
  @JsonPropertyDescription(
    "Name of the output columns that the first port will produce, if any"
  )
  var outputColumns: List[Attribute] = List()

  override def getPhysicalOp(
      workflowId: WorkflowIdentity,
      executionId: ExecutionIdentity
  ): PhysicalOp = {
    PhysicalOp
      .oneToOnePhysicalOp(
        workflowId,
        executionId,
        operatorIdentifier,
        OpExecInitInfo((_, _) => {
          new StateToDataOpExec()
        })
      )
      .withInputPorts(operatorInfo.inputPorts)
      .withOutputPorts(operatorInfo.outputPorts)
      .withPropagateSchema(
        SchemaPropagationFunc(inputSchemas =>
          getOutputSchemas(
            operatorInfo.inputPorts.map(port => inputSchemas(port.id)).toArray
          ).zipWithIndex.map {
            case (schema, index) => PortIdentity(index) -> schema
          }.toMap
        )
      )
      .withSuggestedWorkerNum(1)
  }

  override def operatorInfo: OperatorInfo =
    OperatorInfo(
      "State To Data",
      "Convert State to Data",
      OperatorGroupConstants.STATE_GROUP,
      inputPorts = List(InputPort()),
      outputPorts = List(OutputPort(PortIdentity(), "State"), OutputPort(PortIdentity(1), "Data"))
    )

  override def getOutputSchema(schemas: Array[Schema]): Schema = throw new NotImplementedError()

  override def getOutputSchemas(schemas: Array[Schema]): Array[Schema] =
    Array(
      Schema.builder().add("passToAllDownstream", AttributeType.BOOLEAN).add(outputColumns).build(),
      schemas(0)
    )

}
