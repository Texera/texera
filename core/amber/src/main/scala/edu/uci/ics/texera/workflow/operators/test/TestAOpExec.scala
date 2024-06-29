package edu.uci.ics.texera.workflow.operators.test

import edu.uci.ics.amber.engine.common.tuple.amber.TupleLike
import edu.uci.ics.texera.workflow.common.State
import edu.uci.ics.texera.workflow.common.operators.OperatorExecutor
import edu.uci.ics.texera.workflow.common.tuple.Tuple

class TestAOpExec(limit: Int) extends OperatorExecutor {
  var count = 0

  override def processTuple(tuple: Tuple, port: Int): Iterator[TupleLike] = {
    if (count < limit) {
      count += 1
      Iterator(tuple)
    } else {
      Iterator()
    }
  }

  override def produceState(): State = {
    val state = State()
    state.add("count", "test")
    state
  }
}
