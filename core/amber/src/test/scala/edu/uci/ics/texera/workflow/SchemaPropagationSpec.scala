package edu.uci.ics.texera.workflow

import edu.uci.ics.amber.core.tuple.{AttributeType, Schema}
import edu.uci.ics.amber.core.workflow.{PhysicalOp, WorkflowContext}
import edu.uci.ics.amber.operator.LogicalOp
import edu.uci.ics.amber.operator.metadata.OperatorInfo
import edu.uci.ics.amber.operator.source.SourceOperatorDescriptor
import edu.uci.ics.amber.virtualidentity.{ExecutionIdentity, OperatorIdentity, WorkflowIdentity}
import edu.uci.ics.amber.workflow.{InputPort, OutputPort, PortIdentity}
import org.apache.arrow.util.Preconditions
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec

class SchemaPropagationSpec extends AnyFlatSpec with BeforeAndAfter {

  private abstract class TempTestSourceOpDesc extends SourceOperatorDescriptor {
    override def getPhysicalOp(
        workflowId: WorkflowIdentity,
        executionId: ExecutionIdentity
    ): PhysicalOp = ???

    override def operatorInfo: OperatorInfo =
      OperatorInfo("", "", "", List(InputPort()), List(OutputPort()))
  }

  private class TempTestSinkOpDesc extends LogicalOp {
    override def getPhysicalOp(
        workflowId: WorkflowIdentity,
        executionId: ExecutionIdentity
    ): PhysicalOp = ???

    override def operatorInfo: OperatorInfo =
      OperatorInfo("", "", "", List(InputPort()), List(OutputPort()))

    override def getOutputSchema(schemas: Array[Schema]): Schema = {
      Preconditions.checkArgument(schemas.length == 1)
      schemas(0)
    }
  }

  it should "propagate workflow schema with multiple input and output ports" in {
    // build the following workflow DAG:
    // trainingData ---\                 /----> mlVizSink
    // testingData  ----> mlTrainingOp--<
    // inferenceData ---------------------> mlInferenceOp --> inferenceSink

    val dataSchema = Schema.builder().add("dataCol", AttributeType.INTEGER).build()
    val trainingScan = new TempTestSourceOpDesc() {
      override def operatorIdentifier: OperatorIdentity = OperatorIdentity("trainingScan")

      override def sourceSchema(): Schema = dataSchema
    }

    val testingScan = new TempTestSourceOpDesc() {
      override def operatorIdentifier: OperatorIdentity = OperatorIdentity("testingScan")

      override def sourceSchema(): Schema = dataSchema
    }

    val inferenceScan = new TempTestSourceOpDesc() {
      override def operatorIdentifier: OperatorIdentity = OperatorIdentity("inferenceScan")

      override def sourceSchema(): Schema = dataSchema
    }

    val mlModelSchema = Schema.builder().add("model", AttributeType.STRING).build()
    val mlVizSchema = Schema.builder().add("visualization", AttributeType.STRING).build()

    val mlTrainingOp = new LogicalOp() {
      override def operatorIdentifier: OperatorIdentity = OperatorIdentity("mlTrainingOp")

      override def getPhysicalOp(
          workflowId: WorkflowIdentity,
          executionId: ExecutionIdentity
      ): PhysicalOp = ???

      override def operatorInfo: OperatorInfo =
        OperatorInfo(
          "",
          "",
          "",
          List(
            InputPort(displayName = "training"),
            InputPort(PortIdentity(0), displayName = "testing")
          ),
          List(
            OutputPort(displayName = "visualization"),
            OutputPort(PortIdentity(1), displayName = "model")
          )
        )

      override def getOutputSchema(schemas: Array[Schema]): Schema = ???

      override def getOutputSchemas(schemas: Array[Schema]): Array[Schema] = {
        Preconditions.checkArgument(schemas.length == 2)
        Preconditions.checkArgument(schemas.distinct.length == 1)
        Array(mlVizSchema, mlModelSchema)
      }
    }

    val mlInferOp = new LogicalOp() {
      override def operatorIdentifier: OperatorIdentity = OperatorIdentity("mlInferOp")

      override def getPhysicalOp(
          workflowId: WorkflowIdentity,
          executionId: ExecutionIdentity
      ): PhysicalOp = ???

      override def operatorInfo: OperatorInfo =
        OperatorInfo(
          "",
          "",
          "",
          List(InputPort(displayName = "model"), InputPort(PortIdentity(1), displayName = "data")),
          List(OutputPort(displayName = "data"))
        )

      override def getOutputSchema(schemas: Array[Schema]): Schema = ???

      override def getOutputSchemas(schemas: Array[Schema]): Array[Schema] = {
        Preconditions.checkArgument(schemas.length == 2)
        Array(schemas(1))
      }
    }

    val mlVizSink = new TempTestSinkOpDesc {
      override def operatorIdentifier: OperatorIdentity = OperatorIdentity("mlVizSink")
    }

    val inferenceSink = new TempTestSinkOpDesc {
      override def operatorIdentifier: OperatorIdentity = OperatorIdentity("inferenceSink")
    }

    val operators = List(
      trainingScan,
      testingScan,
      inferenceScan,
      mlTrainingOp,
      mlInferOp,
      mlVizSink,
      inferenceSink
    )

    val links = List(
      LogicalLink(
        trainingScan.operatorIdentifier,
        PortIdentity(),
        mlTrainingOp.operatorIdentifier,
        PortIdentity()
      ),
      LogicalLink(
        testingScan.operatorIdentifier,
        PortIdentity(),
        mlTrainingOp.operatorIdentifier,
        PortIdentity(1)
      ),
      LogicalLink(
        inferenceScan.operatorIdentifier,
        PortIdentity(),
        mlInferOp.operatorIdentifier,
        PortIdentity(1)
      ),
      LogicalLink(
        mlTrainingOp.operatorIdentifier,
        PortIdentity(),
        mlVizSink.operatorIdentifier,
        PortIdentity(0)
      ),
      LogicalLink(
        mlTrainingOp.operatorIdentifier,
        PortIdentity(1),
        mlInferOp.operatorIdentifier,
        PortIdentity()
      ),
      LogicalLink(
        mlInferOp.operatorIdentifier,
        PortIdentity(),
        inferenceSink.operatorIdentifier,
        PortIdentity()
      )
    )

    val ctx = new WorkflowContext()
    val logicalPlan = LogicalPlan(operators, links)
    logicalPlan.propagateWorkflowSchema(ctx, None)
    val schemaResult = logicalPlan.getInputSchemaMap

    assert(schemaResult(mlTrainingOp.operatorIdentifier).head.get.equals(dataSchema))
    assert(schemaResult(mlTrainingOp.operatorIdentifier)(1).get.equals(dataSchema))

    assert(schemaResult(mlInferOp.operatorIdentifier).head.get.equals(mlModelSchema))
    assert(schemaResult(mlInferOp.operatorIdentifier)(1).get.equals(dataSchema))

    assert(schemaResult(mlVizSink.operatorIdentifier).head.get.equals(mlVizSchema))
    assert(schemaResult(inferenceSink.operatorIdentifier).head.get.equals(dataSchema))

  }

}
