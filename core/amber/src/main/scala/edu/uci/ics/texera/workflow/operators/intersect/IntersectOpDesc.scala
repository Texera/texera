package edu.uci.ics.texera.workflow.operators.intersect

import com.google.common.base.Preconditions
import edu.uci.ics.amber.engine.architecture.deploysemantics.layer.OpExecInitInfo
import edu.uci.ics.amber.engine.common.model.PhysicalOp
import edu.uci.ics.amber.engine.common.model.tuple.Schema
import edu.uci.ics.amber.engine.common.virtualidentity.{ExecutionIdentity, WorkflowIdentity}
import edu.uci.ics.amber.engine.common.workflow.{InputPort, OutputPort, PortIdentity}
import edu.uci.ics.texera.workflow.common.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.texera.workflow.common.operators.LogicalOp
import edu.uci.ics.texera.workflow.common.workflow.HashPartition

class IntersectOpDesc extends LogicalOp {

  override def getPhysicalOp(
      workflowId: WorkflowIdentity,
      executionId: ExecutionIdentity
  ): PhysicalOp = {
    PhysicalOp
      .oneToOnePhysicalOp(
        workflowId,
        executionId,
        operatorIdentifier,
        OpExecInitInfo((_, _) => new IntersectOpExec())
      )
      .withInputPorts(operatorInfo.inputPorts)
      .withOutputPorts(operatorInfo.outputPorts)
      .withPartitionRequirement(List(Option(HashPartition()), Option(HashPartition())))
      .withDerivePartition(_ => HashPartition())
  }

  override def operatorInfo: OperatorInfo =
    OperatorInfo(
      "Intersect",
      "Take the intersect of two inputs",
      OperatorGroupConstants.SET_GROUP,
      inputPorts = List(InputPort(PortIdentity()), InputPort(PortIdentity(1))),
      outputPorts = List(OutputPort(blocking = true))
    )

  override def getOutputSchema(schemas: Array[Schema]): Schema = {
    Preconditions.checkArgument(schemas.forall(_ == schemas(0)))
    schemas(0)
  }

}
