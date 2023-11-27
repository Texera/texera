package edu.uci.ics.texera.workflow.operators.udf.python.source

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.google.common.base.Preconditions
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle
import edu.uci.ics.amber.engine.architecture.deploysemantics.layer.OpExecConfig
import edu.uci.ics.texera.workflow.common.metadata.OperatorGroupConstants
import edu.uci.ics.texera.workflow.common.metadata.OperatorInfo
import edu.uci.ics.texera.workflow.common.metadata.OutputPort
import edu.uci.ics.texera.workflow.common.operators.source.SourceOperatorDescriptor
import edu.uci.ics.texera.workflow.common.tuple.schema.Attribute
import edu.uci.ics.texera.workflow.common.tuple.schema.OperatorSchemaInfo
import edu.uci.ics.texera.workflow.common.tuple.schema.Schema
import java.util.Collections.singletonList
import scala.collection.JavaConverters.asScalaBuffer
import scala.jdk.CollectionConverters.asJavaIterableConverter

class PythonUDFSourceOpDescV2 extends SourceOperatorDescriptor {
  @JsonProperty(
    required = true,
    defaultValue = "import pytexera as ptx\n" +
      "from overrides import overrides\n" +
      "from typing import Iterator, Union\n" +
      "\n" +
      "\n" +
      "class GenerateOperator(ptx.UDFSourceOperator):\n" +
      "\n" +
      "    @overrides\n" +
      "    def produce(self) -> Iterator[Union[ptx.TupleLike, ptx.TableLike, None]]:\n" +
      "        yield\n"
  )
  @JsonSchemaTitle("Python script")
  @JsonPropertyDescription("Input your code here")
  var code: String = ""

  @JsonProperty(required = true)
  @JsonSchemaTitle("Worker count")
  @JsonPropertyDescription("Specify how many parallel workers to lunch")
  var workers: Int = Int.box(1)

  @JsonProperty
  @JsonSchemaTitle("Columns")
  @JsonPropertyDescription("The columns of the source")
  var columns: List[Attribute] = List()

  override def operatorExecutor(operatorSchemaInfo: OperatorSchemaInfo): OpExecConfig = {
    Preconditions.checkArgument(workers >= 1, "Need at least 1 worker.", Array())
    if (workers > 1)
      OpExecConfig
        .oneToOneLayer(
          operatorIdentifier,
          _ => new PythonUDFSourceOpExecV2(code, operatorSchemaInfo.outputSchemas.head)
        )
        .withIsOneToManyOp(true)
    else
      OpExecConfig
        .manyToOneLayer(
          operatorIdentifier,
          _ => new PythonUDFSourceOpExecV2(code, operatorSchemaInfo.outputSchemas.head)
        )
        .withIsOneToManyOp(true)
  }
  override def operatorInfo =
    new OperatorInfo(
      "1-out Python UDF",
      "User-defined function operator in Python script",
      OperatorGroupConstants.UDF_GROUP,
      scala.collection.immutable.List.empty,
      asScalaBuffer(singletonList(new OutputPort(""))).toList,
      false,
      false,
      true,
      false
    )
  override def sourceSchema(): Schema = {
    val outputSchemaBuilder = Schema.newBuilder
    // for any pythonUDFType, it can add custom output columns (attributes).
    if (columns.nonEmpty) {
      outputSchemaBuilder.add(columns.asJava).build
    } else {
      outputSchemaBuilder.build
    }

  }
}
