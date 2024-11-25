package edu.uci.ics.texera.workflow.operators.controlBlock.ifStatement

import edu.uci.ics.amber.engine.architecture.deploysemantics.layer.OpExecInitInfo
import edu.uci.ics.amber.engine.common.model.{PhysicalOp, SchemaPropagationFunc}
import edu.uci.ics.amber.engine.common.model.tuple.Schema
import edu.uci.ics.amber.engine.common.virtualidentity.{ExecutionIdentity, WorkflowIdentity}
import edu.uci.ics.amber.engine.common.workflow.{InputPort, OutputPort, PortIdentity}
import edu.uci.ics.texera.workflow.common.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.texera.workflow.common.operators.LogicalOp

class IfStatementOpDesc extends LogicalOp {

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
          new IfStatementOpExec()
        })
      )
      .withInputPorts(operatorInfo.inputPorts)
      .withOutputPorts(operatorInfo.outputPorts)
      .withParallelizable(false)
      .withPropagateSchema(
        SchemaPropagationFunc(inputSchemas =>
          operatorInfo.outputPorts
            .map(_.id)
            .map(id => id -> inputSchemas(operatorInfo.inputPorts.last.id))
            .toMap
        )
      )
  }

  override def operatorInfo: OperatorInfo =
    OperatorInfo(
      "If Statement",
      "If Statement",
      OperatorGroupConstants.CONTROL_GROUP,
      inputPorts = List(
        InputPort(PortIdentity(), "Condition", isStatePort = true),
        InputPort(PortIdentity(1), "Data", dependencies = List(PortIdentity()))),
      outputPorts = List(
        OutputPort(PortIdentity(), "True"),
        OutputPort(PortIdentity(1), "False"))
    )

  override def getOutputSchema(schemas: Array[Schema]): Schema = throw new NotImplementedError()

  override def getOutputSchemas(schemas: Array[Schema]): Array[Schema] = Array(schemas(1), schemas(1))
}
