package edu.uci.ics.amber.operator.state

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle
import edu.uci.ics.amber.core.executor.OpExecWithClassName
import edu.uci.ics.amber.core.virtualidentity.{ExecutionIdentity, WorkflowIdentity}
import edu.uci.ics.amber.core.workflow.{InputPort, OutputPort, PhysicalOp}
import edu.uci.ics.amber.operator.LogicalOp
import edu.uci.ics.amber.operator.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.amber.util.JSONUtils.objectMapper

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
        OpExecWithClassName(
          "edu.uci.ics.amber.operator.state.DataToStateOpExec",
          objectMapper.writeValueAsString(this)
        )
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
}
