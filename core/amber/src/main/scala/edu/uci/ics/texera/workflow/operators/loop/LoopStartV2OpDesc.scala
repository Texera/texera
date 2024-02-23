package edu.uci.ics.texera.workflow.operators.loop

import com.fasterxml.jackson.annotation.JsonProperty
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle
import edu.uci.ics.amber.engine.architecture.deploysemantics.PhysicalOp
import edu.uci.ics.amber.engine.architecture.deploysemantics.layer.OpExecInitInfo
import edu.uci.ics.amber.engine.common.virtualidentity.{ExecutionIdentity, WorkflowIdentity}
import edu.uci.ics.amber.engine.common.workflow.{InputPort, OutputPort, PortIdentity}
import edu.uci.ics.texera.workflow.common.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.texera.workflow.common.operators.LogicalOp
import edu.uci.ics.texera.workflow.common.tuple.schema.{AttributeType, Schema}

class LoopStartV2OpDesc extends LogicalOp {
  @JsonProperty(defaultValue = "false")
  @JsonSchemaTitle("Attach Control to Data")
  var attach: Boolean = false

  override def getPhysicalOp(
      workflowId: WorkflowIdentity,
      executionId: ExecutionIdentity
  ): PhysicalOp = {
    val outputSchema = outputPortToSchemaMapping(operatorInfo.outputPorts.head.id)
    PhysicalOp
      .oneToOnePhysicalOp(
        workflowId,
        executionId,
        operatorIdentifier,
        OpExecInitInfo((_, _, operatorConfig) => {
          new LoopStartV2OpExec(outputSchema, operatorConfig.workerConfigs.head.workerId)
        })
      )
      .withInputPorts(operatorInfo.inputPorts, inputPortToSchemaMapping)
      .withOutputPorts(operatorInfo.outputPorts, outputPortToSchemaMapping)
      .withSuggestedWorkerNum(1)
  }

  override def operatorInfo: OperatorInfo =
    OperatorInfo(
      "LoopStartV2",
      "Limit the number of output rows",
      OperatorGroupConstants.CONTROL_GROUP,
      inputPorts = List(
        InputPort(PortIdentity(0), displayName = "Control", dependencies = List(PortIdentity(1))),
        InputPort(PortIdentity(1), displayName = "Data")
      ),
      outputPorts = List(OutputPort()),
      supportReconfiguration = true
    )

  override def getOutputSchema(schemas: Array[Schema]): Schema =
    if (attach) {
      Schema
        .newBuilder()
        .add("Iteration", AttributeType.INTEGER)
        .add(schemas(0))
        .add(schemas(1))
        .build()
    } else {
      schemas(1)
    }
}
