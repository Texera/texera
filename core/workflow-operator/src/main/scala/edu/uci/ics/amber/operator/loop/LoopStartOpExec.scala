package edu.uci.ics.amber.operator.loop

import edu.uci.ics.amber.core.executor.OperatorExecutor
import edu.uci.ics.amber.core.tuple.{Tuple, TupleLike}
import edu.uci.ics.amber.util.JSONUtils.objectMapper

import scala.collection.mutable

class LoopStartOpExec(descString: String) extends OperatorExecutor {
  private val desc: LoopStartOpDesc = objectMapper.readValue(descString, classOf[LoopStartOpDesc])
  private val data = new mutable.ArrayBuffer[Tuple]
  private var currentIteration = 0


  def checkCondition(): Boolean = {
    desc.iteration>currentIteration
  }

  override def processTuple(tuple: Tuple, port: Int): Iterator[TupleLike] = {
    data.append(tuple)
    Iterator.empty
  }

  override def onFinish(port: Int): Iterator[TupleLike] = {
    currentIteration += 1
    data.iterator
  }
}