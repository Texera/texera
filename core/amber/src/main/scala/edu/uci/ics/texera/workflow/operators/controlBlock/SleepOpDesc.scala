package edu.uci.ics.texera.workflow.operators.controlBlock

import com.fasterxml.jackson.annotation.{JsonProperty, JsonPropertyDescription}
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle
import edu.uci.ics.amber.engine.architecture.deploysemantics.layer.OpExecInitInfo
import edu.uci.ics.amber.engine.common.model.PhysicalOp
import edu.uci.ics.amber.engine.common.model.tuple.Schema
import edu.uci.ics.amber.engine.common.virtualidentity.{ExecutionIdentity, WorkflowIdentity}
import edu.uci.ics.amber.engine.common.workflow.{InputPort, OutputPort}
import edu.uci.ics.texera.workflow.common.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.texera.workflow.common.operators.{LogicalOp, StateTransferFunc}

import scala.util.{Success, Try}

class SleepOpDesc extends LogicalOp {

  @JsonProperty(required = true)
  @JsonSchemaTitle("Sleep")
  var limit: Int = _

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
          new SleepOpExec(limit)
        })
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

  override def getOutputSchema(schemas: Array[Schema]): Schema = schemas(0)
}
