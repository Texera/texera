package edu.uci.ics.texera.workflow.operators.loop

import com.fasterxml.jackson.annotation.{JsonProperty, JsonPropertyDescription}
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle
import edu.uci.ics.amber.engine.architecture.deploysemantics.PhysicalOp
import edu.uci.ics.amber.engine.architecture.deploysemantics.layer.OpExecInitInfo
import edu.uci.ics.amber.engine.common.virtualidentity.{ExecutionIdentity, WorkflowIdentity}
import edu.uci.ics.amber.engine.common.workflow.OutputPort
import edu.uci.ics.texera.workflow.common.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.texera.workflow.common.operators.source.SourceOperatorDescriptor
import edu.uci.ics.texera.workflow.common.tuple.schema.{Attribute, AttributeType, Schema}
import scala.jdk.CollectionConverters.IterableHasAsJava

class RangeAttribute {
  @JsonProperty(required = true)
  @JsonSchemaTitle("Name")
  @JsonPropertyDescription("Attribute name in the schema")
  var name: String = "Iteration"

  @JsonProperty(defaultValue = "0", required = true)
  @JsonSchemaTitle("Start")
  @JsonPropertyDescription("Start of the range")
  var start: Double = 0

  @JsonProperty(defaultValue = "1", required = true)
  @JsonSchemaTitle("Step")
  @JsonPropertyDescription("Step of the range")
  var step: Double = 1
}

class GeneratorOpDesc extends SourceOperatorDescriptor {
  @JsonProperty(required = true)
  @JsonSchemaTitle("Iteration")
  @JsonPropertyDescription("Number of iteration")
  var iteration: Int = 0

  var attributes: List[RangeAttribute] = List()
  override def getPhysicalOp(
      workflowId: WorkflowIdentity,
      executionId: ExecutionIdentity
  ): PhysicalOp =
    PhysicalOp
      .oneToOnePhysicalOp(
        workflowId,
        executionId,
        operatorIdentifier,
        OpExecInitInfo((_, _, _) =>
          new GeneratorOpExec(
            iteration,
            attributes,
            outputPortToSchemaMapping(operatorInfo.outputPorts.head.id)
          )
        )
      )
      .withInputPorts(operatorInfo.inputPorts, inputPortToSchemaMapping)
      .withOutputPorts(operatorInfo.outputPorts, outputPortToSchemaMapping)
      .withSuggestedWorkerNum(1)

  override def operatorInfo: OperatorInfo =
    OperatorInfo(
      "Generator",
      "Generator",
      OperatorGroupConstants.CONTROL_GROUP,
      inputPorts = List.empty,
      outputPorts = List(OutputPort()),
      supportReconfiguration = true
    )
  override def sourceSchema(): Schema = Schema.newBuilder
      .add(attributes.map(attribute => new Attribute(attribute.name, AttributeType.DOUBLE)).asJava)
      .build()
}
