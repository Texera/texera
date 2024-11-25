package edu.uci.ics.texera.workflow.operators.controlBlock.loop

import edu.uci.ics.amber.engine.common.executor.OperatorExecutor
import edu.uci.ics.amber.engine.common.model.tuple.{Tuple, TupleLike}

import scala.collection.mutable

class LoopEndOpExec extends OperatorExecutor {
  private val data = new mutable.ArrayBuffer[Tuple]

  override def processTuple(tuple: Tuple, port: Int): Iterator[TupleLike] = {
    data.append(tuple)
    Iterator.empty
  }

  override def onFinish(port: Int): Iterator[TupleLike] = data.iterator
}
