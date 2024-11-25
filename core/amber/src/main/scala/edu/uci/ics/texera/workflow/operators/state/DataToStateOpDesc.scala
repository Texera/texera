package edu.uci.ics.texera.workflow.operators.state

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle
import edu.uci.ics.amber.engine.common.model.tuple.Schema
import edu.uci.ics.amber.engine.common.model.{PhysicalOp, SchemaPropagationFunc}
import edu.uci.ics.amber.engine.common.virtualidentity.{ExecutionIdentity, WorkflowIdentity}
import edu.uci.ics.amber.engine.common.workflow.{InputPort, OutputPort, PortIdentity}
import edu.uci.ics.texera.workflow.common.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.texera.workflow.common.operators.LogicalOp
import edu.uci.ics.amber.engine.architecture.deploysemantics.layer.OpExecInitInfo

class DataToStateOpDesc extends LogicalOp {
  @JsonProperty(defaultValue = "false")
  @JsonSchemaTitle("Pass To All Downstream")
  @JsonDeserialize(contentAs = classOf[java.lang.Boolean])
  var passToAllDownstream: Option[Boolean] = Option(false)

  override def getPhysicalOp(
      workflowId: WorkflowIdentity,
      executionId: ExecutionIdentity
  ): PhysicalOp = {
    PhysicalOp
      .oneToOnePhysicalOp(
        workflowId,
        executionId,
        operatorIdentifier,
        OpExecInitInfo((_, _) => new DataToStateOpExec(passToAllDownstream.get))
      )
      .withInputPorts(operatorInfo.inputPorts)
      .withOutputPorts(operatorInfo.outputPorts)
      .withPropagateSchema(
        SchemaPropagationFunc(inputSchemas => Map(PortIdentity() -> inputSchemas(PortIdentity(1))))
      )
      .withSuggestedWorkerNum(1)
  }

  override def operatorInfo: OperatorInfo =
    OperatorInfo(
      "Data To State",
      "Convert Data to State",
      OperatorGroupConstants.STATE_GROUP,
      inputPorts = List(
        InputPort(PortIdentity(), "State"),
        InputPort(PortIdentity(1), "Data", dependencies = List(PortIdentity()))
      ),
      outputPorts = List(OutputPort())
    )

  override def getOutputSchema(schemas: Array[Schema]): Schema = schemas(1)
}
