package edu.uci.ics.texera.workflow.operators.controlBlock

import edu.uci.ics.amber.engine.common.executor.OperatorExecutor
import edu.uci.ics.amber.engine.common.model.tuple.{Tuple, TupleLike}

class SleepOpExec(limit: Int) extends OperatorExecutor {

  override def processTuple(tuple: Tuple, port: Int): Iterator[TupleLike] = {
      Thread.sleep(1000*limit)
      Iterator(tuple)
  }

}
