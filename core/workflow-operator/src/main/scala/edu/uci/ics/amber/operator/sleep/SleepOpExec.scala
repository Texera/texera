package edu.uci.ics.amber.operator.sleep

import edu.uci.ics.amber.core.executor.OperatorExecutor
import edu.uci.ics.amber.core.tuple.{Tuple, TupleLike}
import edu.uci.ics.amber.util.JSONUtils.objectMapper

class SleepOpExec(descString: String) extends OperatorExecutor {
  private val desc: SleepOpDesc = objectMapper.readValue(descString, classOf[SleepOpDesc])

  override def processTuple(tuple: Tuple, port: Int): Iterator[TupleLike] = {
    Thread.sleep(1000*desc.time)
    Iterator(tuple)
  }
}