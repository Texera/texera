package edu.uci.ics.amber.operator.sleep

import com.fasterxml.jackson.annotation.JsonProperty
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle
import edu.uci.ics.amber.core.executor.OpExecWithClassName
import edu.uci.ics.amber.core.virtualidentity.{ExecutionIdentity, WorkflowIdentity}
import edu.uci.ics.amber.core.workflow.{InputPort, OutputPort, PhysicalOp}
import edu.uci.ics.amber.operator.LogicalOp
import edu.uci.ics.amber.operator.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.amber.util.JSONUtils.objectMapper


class SleepOpDesc extends LogicalOp {

  @JsonProperty(required = true)
  @JsonSchemaTitle("Sleep")
  var time: Int = _

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
          "edu.uci.ics.amber.operator.sleep.SleepOpExec",
          objectMapper.writeValueAsString(this)
        )
      )
      .withInputPorts(operatorInfo.inputPorts)
      .withOutputPorts(operatorInfo.outputPorts)
      .withParallelizable(false)
      .withSuggestedWorkerNum(1)

  }

  override def operatorInfo: OperatorInfo =
    OperatorInfo(
      "Sleep",
      "Limit the number of output rows",
      OperatorGroupConstants.CONTROL_GROUP,
      inputPorts = List(InputPort()),
      outputPorts = List(OutputPort()),
    )
}