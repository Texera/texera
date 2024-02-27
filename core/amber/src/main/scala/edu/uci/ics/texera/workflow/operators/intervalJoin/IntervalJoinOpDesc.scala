package edu.uci.ics.texera.workflow.operators.intervalJoin

import com.fasterxml.jackson.annotation.{JsonProperty, JsonPropertyDescription}
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.google.common.base.Preconditions
import com.kjetland.jackson.jsonSchema.annotations.{JsonSchemaInject, JsonSchemaTitle}
import edu.uci.ics.amber.engine.architecture.deploysemantics.PhysicalOp
import edu.uci.ics.amber.engine.architecture.deploysemantics.layer.OpExecInitInfo
import edu.uci.ics.amber.engine.common.virtualidentity.{ExecutionIdentity, WorkflowIdentity}
import edu.uci.ics.amber.engine.common.workflow.{InputPort, OutputPort, PortIdentity}
import edu.uci.ics.texera.workflow.common.metadata.annotations.{
  AutofillAttributeName,
  AutofillAttributeNameOnPort1
}
import edu.uci.ics.texera.workflow.common.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.texera.workflow.common.operators.LogicalOp
import edu.uci.ics.texera.workflow.common.tuple.schema.{Attribute, Schema}
import edu.uci.ics.texera.workflow.common.workflow.HashPartition

/** This Operator have two assumptions:
  * 1. The tuples in both inputs come in ascending order
  * 2. The left input join key takes as points, join condition is: left key in the range of (right key, right key + constant)
  */
@JsonSchemaInject(json = """
{
  "attributeTypeRules": {
    "leftAttributeName": {
      "enum": ["integer", "long", "double", "timestamp"]
    },
    "rightAttributeName": {
      "const": {
        "$data": "leftAttributeName"
      }
    }
  }
}
""")
class IntervalJoinOpDesc extends LogicalOp {

  @JsonProperty(required = true)
  @JsonSchemaTitle("Left Input attr")
  @JsonPropertyDescription("Choose one attribute in the left table")
  @AutofillAttributeName
  var leftAttributeName: String = _

  @JsonProperty(required = true)
  @JsonSchemaTitle("Right Input attr")
  @JsonPropertyDescription("Choose one attribute in the right table")
  @AutofillAttributeNameOnPort1
  var rightAttributeName: String = _

  @JsonProperty(required = true, defaultValue = "10")
  @JsonSchemaTitle("Interval Constant")
  @JsonPropertyDescription("left attri in (right, right + constant)")
  var constant: Long = 10

  @JsonProperty(required = true, defaultValue = "true")
  @JsonSchemaTitle("Include Left Bound")
  @JsonPropertyDescription("Include condition left attri = right attri")
  var includeLeftBound: Boolean = true

  @JsonProperty(required = true, defaultValue = "true")
  @JsonSchemaTitle("Include Right Bound")
  @JsonPropertyDescription("Include condition left attri = right attri")
  var includeRightBound: Boolean = true

  @JsonDeserialize(contentAs = classOf[TimeIntervalType])
  @JsonProperty(defaultValue = "day", required = false)
  @JsonSchemaTitle("Time interval type")
  @JsonPropertyDescription("Year, Month, Day, Hour, Minute or Second")
  var timeIntervalType: Option[TimeIntervalType] = _

  override def getPhysicalOp(
      workflowId: WorkflowIdentity,
      executionId: ExecutionIdentity
  ): PhysicalOp = {
    val inputSchemas =
      operatorInfo.inputPorts.map(inputPort => inputPortToSchemaMapping(inputPort.id))
    val leftSchema = inputSchemas(0)
    val rightSchema = inputSchemas(1)
    val outputSchema =
      operatorInfo.outputPorts.map(outputPort => outputPortToSchemaMapping(outputPort.id)).head
    val partitionRequirement = List(
      Option(HashPartition(List(leftSchema.getIndex(leftAttributeName)))),
      Option(HashPartition(List(rightSchema.getIndex(rightAttributeName))))
    )

    PhysicalOp
      .oneToOnePhysicalOp(
        workflowId,
        executionId,
        operatorIdentifier,
        OpExecInitInfo((_, _, _) =>
          new IntervalJoinOpExec(
            leftAttributeName,
            rightAttributeName,
            includeLeftBound,
            includeRightBound,
            constant,
            timeIntervalType
          )
        )
      )
      .withInputPorts(operatorInfo.inputPorts, inputPortToSchemaMapping)
      .withOutputPorts(operatorInfo.outputPorts, outputPortToSchemaMapping)
      .withBlockingInputs(List(operatorInfo.inputPorts.head.id))
      .withPartitionRequirement(partitionRequirement)
  }

  override def operatorInfo: OperatorInfo =
    OperatorInfo(
      "Interval Join",
      "Join two inputs with left table join key in the range of [right table join key, right table join key + constant value]",
      OperatorGroupConstants.JOIN_GROUP,
      inputPorts = List(
        InputPort(PortIdentity(), displayName = "left table"),
        InputPort(
          PortIdentity(1),
          displayName = "right table",
          dependencies = List(PortIdentity(0))
        )
      ),
      outputPorts = List(OutputPort())
    )

  def this(
      leftTableAttributeName: String,
      rightTableAttributeName: String,
      schemas: Array[Schema],
      constant: Long,
      includeLeftBound: Boolean,
      includeRightBound: Boolean,
      timeIntervalType: TimeIntervalType
  ) = {
    this() // Calling primary constructor, and it is first line
    this.leftAttributeName = leftTableAttributeName
    this.rightAttributeName = rightTableAttributeName
    this.constant = constant
    this.includeLeftBound = includeLeftBound
    this.includeRightBound = includeRightBound
    this.timeIntervalType = Some(timeIntervalType)
  }

  override def getOutputSchema(schemas: Array[Schema]): Schema = {
    Preconditions.checkArgument(schemas.length == 2)
    val builder: Schema.Builder = Schema.newBuilder()
    val leftTableSchema: Schema = schemas(0)
    val rightTableSchema: Schema = schemas(1)
    builder.add(leftTableSchema)
    rightTableSchema.getAttributesScala
      .map(attr => {
        if (leftTableSchema.containsAttribute(attr.getName)) {
          builder.add(new Attribute(s"${attr.getName}#@1", attr.getType))
        } else {
          builder.add(attr.getName, attr.getType)
        }
      })
    builder.build()
  }

}
