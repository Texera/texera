package edu.uci.ics.amber.operator.projection

import com.fasterxml.jackson.annotation.{JsonProperty, JsonPropertyDescription}
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle
import edu.uci.ics.amber.core.executor.OpExecWithClassName
import edu.uci.ics.amber.core.tuple.Schema
import edu.uci.ics.amber.core.virtualidentity.{ExecutionIdentity, WorkflowIdentity}
import edu.uci.ics.amber.core.workflow.PhysicalOp.oneToOnePhysicalOp
import edu.uci.ics.amber.core.workflow._
import edu.uci.ics.amber.operator.map.MapOpDesc
import edu.uci.ics.amber.operator.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.amber.util.JSONUtils.objectMapper

class ProjectionOpDesc extends MapOpDesc {

  @JsonProperty(required = true, defaultValue = "false")
  @JsonSchemaTitle("Drop Option")
  @JsonPropertyDescription("check to drop the selected attributes")
  var isDrop: Boolean = false

  var attributes: List[AttributeUnit] = List()

  override def getPhysicalOp(
      workflowId: WorkflowIdentity,
      executionId: ExecutionIdentity
  ): PhysicalOp = {
    oneToOnePhysicalOp(
      workflowId,
      executionId,
      operatorIdentifier,
      OpExecWithClassName(
        "edu.uci.ics.amber.operator.projection.ProjectionOpExec",
        objectMapper.writeValueAsString(this)
      )
    )
      .withInputPorts(operatorInfo.inputPorts)
      .withOutputPorts(operatorInfo.outputPorts)
      .withDerivePartition(derivePartition())
      .withPropagateSchema(SchemaPropagationFunc(inputSchemas => {
        require(attributes.nonEmpty, "Attributes must not be empty")

        val inputSchema = inputSchemas.values.head
        val outputSchema = if (!isDrop) {
          attributes.foldLeft(Schema()) { (schema, attribute) =>
            val originalType = inputSchema.getAttribute(attribute.getOriginalAttribute).getType
            schema.add(attribute.getAlias, originalType)
          }
        } else {
          attributes.foldLeft(inputSchema) { (schema, attribute) =>
            schema.remove(attribute.getOriginalAttribute)
          }
        }

        Map(operatorInfo.outputPorts.head.id -> outputSchema)
      }))
  }

  def derivePartition()(partition: List[PartitionInfo]): PartitionInfo = {
    val inputPartitionInfo = partition.head

    val outputPartitionInfo = inputPartitionInfo match {
      case HashPartition(hashAttributeNames) =>
        if (hashAttributeNames.nonEmpty) HashPartition(hashAttributeNames) else UnknownPartition()
      case RangePartition(rangeAttributeNames, min, max) =>
        if (rangeAttributeNames.nonEmpty) RangePartition(rangeAttributeNames, min, max)
        else UnknownPartition()
      case _ => inputPartitionInfo
    }

    outputPartitionInfo
  }

  override def operatorInfo: OperatorInfo = {
    OperatorInfo(
      "Projection",
      "Keeps or drops the column",
      OperatorGroupConstants.CLEANING_GROUP,
      inputPorts = List(InputPort()),
      outputPorts = List(OutputPort())
    )
  }
}
