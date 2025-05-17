package edu.uci.ics.amber.operator.loop

import edu.uci.ics.amber.core.executor.OperatorExecutor
import edu.uci.ics.amber.core.tuple.{Tuple, TupleLike}
import scala.collection.mutable

class LoopEndOpExec extends OperatorExecutor {
  private val data = new mutable.ArrayBuffer[Tuple]

  override def processTuple(tuple: Tuple, port: Int): Iterator[TupleLike] = {
    data.append(tuple)
    Iterator.empty
  }

  override def onFinish(port: Int): Iterator[TupleLike] = data.iterator
}
