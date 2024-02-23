package edu.uci.ics.texera.workflow.operators.loop

import edu.uci.ics.texera.workflow.common.operators.source.SourceOperatorExecutor
import edu.uci.ics.texera.workflow.common.tuple.Tuple
import edu.uci.ics.texera.workflow.common.tuple.schema.Schema
import scala.math.BigDecimal.double2bigDecimal

class GeneratorOpExec(
    val iteration: Int,
    val attributes: List[RangeAttribute],
    val outputSchema: Schema
) extends SourceOperatorExecutor {
  override def produceTexeraTuple(): Iterator[Tuple] = {
    attributes
      .map(attribute =>
        (attribute.start until BigDecimal(attribute.start + attribute.step * iteration) by attribute.step).map(
          i => (outputSchema.getAttribute(attribute.name), i.toDouble)
        )
      )
      .transpose
      .map(row => {
        val builder = Tuple.newBuilder(outputSchema)
        row.map(col => builder.add(col._1, col._2))
        builder.build()
      })
      .iterator
  }

  override def open(): Unit = {}

  override def close(): Unit = {}
}
