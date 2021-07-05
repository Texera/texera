package edu.uci.ics.texera.unittest.workflow.operators.hashjoin

import edu.uci.ics.amber.engine.common.InputExhausted
import edu.uci.ics.amber.engine.common.virtualidentity.{LayerIdentity, LinkIdentity}
import edu.uci.ics.texera.workflow.common.tuple.Tuple
import edu.uci.ics.texera.workflow.common.tuple.schema.{
  Attribute,
  AttributeType,
  OperatorSchemaInfo,
  Schema
}
import edu.uci.ics.texera.workflow.operators.hashJoin.{HashJoinOpDesc, HashJoinOpExec}
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec

class HashJoinOpExecSpec extends AnyFlatSpec with BeforeAndAfter {
  val build: LinkIdentity = linkID()
  val probe: LinkIdentity = linkID()

  var opExec: HashJoinOpExec[String] = _
  var opDesc: HashJoinOpDesc[String] = _
  var counter: Int = 0

  def linkID(): LinkIdentity = LinkIdentity(layerID(), layerID())

  def layerID(): LayerIdentity = {
    counter += 1
    LayerIdentity("" + counter, "" + counter, "" + counter)
  }

  def tuple(name: String, n: Int = 1, i: Int): Tuple = {

    Tuple
      .newBuilder(schema(name, n))
      .addSequentially(Array[Object]((i * 2).toString, i.toString))
      .build()
  }

  def schema(name: String, n: Int = 1): Schema = {
    Schema
      .newBuilder()
      .add(
        new Attribute(name, AttributeType.STRING),
        new Attribute(name + "_" + n, AttributeType.STRING)
      )
      .build()
  }

  it should "work with basic two input streams with different buildAttributeName and probeAttributeName" in {

    opDesc = new HashJoinOpDesc[String]()
    opDesc.buildAttributeName = "build_1"
    opDesc.probeAttributeName = "probe_1"
    val inputSchemas = Array(schema("build"), schema("probe"))
    val outputSchema = opDesc.getOutputSchema(inputSchemas)

    opExec = new HashJoinOpExec[String](
      build,
      "build_1",
      "probe_1",
      OperatorSchemaInfo(inputSchemas, outputSchema)
    )
    opExec.open()
    counter = 0
    (0 to 7).map(i => {
      assert(opExec.processTexeraTuple(Left(tuple("build", 1, i)), build).isEmpty)
    })
    assert(opExec.processTexeraTuple(Right(InputExhausted()), build).isEmpty)

    val outputTuples = (5 to 9)
      .map(i => opExec.processTexeraTuple(Left(tuple("probe", 1, i)), probe))
      .foldLeft(Iterator[Tuple]())(_ ++ _)
      .toList

    assert(opExec.processTexeraTuple(Right(InputExhausted()), probe).isEmpty)

    assert(outputTuples.size == 3)
    assert(outputTuples.head.getSchema.getAttributeNames.size() == 3)

    opExec.close()
  }

  it should "work with basic two input streams with the same buildAttributeName and probeAttributeName" in {
    opDesc = new HashJoinOpDesc[String]()
    opDesc.buildAttributeName = "same"
    opDesc.probeAttributeName = "same"
    val inputSchemas = Array(schema("same", 1), schema("same", 2))
    val outputSchema = opDesc.getOutputSchema(inputSchemas)
    opExec = new HashJoinOpExec[String](
      build,
      "same",
      "same",
      OperatorSchemaInfo(inputSchemas, outputSchema)
    )
    opExec.open()
    counter = 0
    (0 to 7).map(i => {
      assert(opExec.processTexeraTuple(Left(tuple("same", n = 1, i)), build).isEmpty)
    })
    assert(opExec.processTexeraTuple(Right(InputExhausted()), build).isEmpty)

    val outputTuples = (5 to 9)
      .map(i => opExec.processTexeraTuple(Left(tuple("same", n = 2, i)), probe))
      .foldLeft(Iterator[Tuple]())(_ ++ _)
      .toList

    assert(opExec.processTexeraTuple(Right(InputExhausted()), probe).isEmpty)

    assert(outputTuples.size == 3)
    assert(outputTuples.head.getSchema.getAttributeNames.size() == 3)

    opExec.close()
  }
}
