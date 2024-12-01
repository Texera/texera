package edu.uci.ics.texera.workflow.operators.controlBlock.state

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle
import edu.uci.ics.amber.engine.architecture.deploysemantics.layer.OpExecInitInfo
import edu.uci.ics.amber.engine.common.model.tuple.Schema
import edu.uci.ics.amber.engine.common.model.PhysicalOp
import edu.uci.ics.amber.engine.common.virtualidentity.{ExecutionIdentity, WorkflowIdentity}
import edu.uci.ics.amber.engine.common.workflow.{InputPort, OutputPort}
import edu.uci.ics.texera.workflow.common.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.texera.workflow.common.operators.LogicalOp

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
        OpExecInitInfo((_, _) => {
          new DataToStateOpExec(passToAllDownstream.get)
        })
      )
      .withInputPorts(operatorInfo.inputPorts)
      .withOutputPorts(operatorInfo.outputPorts)
      .withSuggestedWorkerNum(1)
  }

  override def operatorInfo: OperatorInfo =
    OperatorInfo(
      "Data To State",
      "Convert Data to State",
      OperatorGroupConstants.CONTROL_GROUP,
      inputPorts = List(InputPort(displayName = "Data")),
      outputPorts = List(OutputPort(displayName = "State"))
    )

  override def getOutputSchema(schemas: Array[Schema]): Schema = schemas(0)
}
