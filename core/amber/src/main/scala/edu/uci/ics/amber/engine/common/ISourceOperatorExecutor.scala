package edu.uci.ics.amber.engine.common

import edu.uci.ics.amber.engine.common.ambertag.OperatorIdentifier
import edu.uci.ics.amber.engine.common.tuple.ITuple

trait ISourceOperatorExecutor extends IOperatorExecutor {

  override def processTuple(
      tuple: Either[ITuple, InputExhausted],
      input: OperatorIdentifier
  ): Iterator[ITuple] = {
    // The input Tuple for source operator will always be InputExhausted.
    // Source and other operators can share the same processing logic.
    // produce() will be called only once.
    produce()
  }

  def produce(): Iterator[ITuple]

}
