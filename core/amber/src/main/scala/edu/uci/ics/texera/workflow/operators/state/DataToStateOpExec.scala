package edu.uci.ics.texera.workflow.operators.state

import edu.uci.ics.amber.engine.common.tuple.amber.TupleLike
import edu.uci.ics.texera.workflow.common.State
import edu.uci.ics.texera.workflow.common.operators.OperatorExecutor
import edu.uci.ics.texera.workflow.common.tuple.Tuple

import scala.collection.mutable

class DataToStateOpExec extends OperatorExecutor {
  private val buffer = new mutable.ArrayBuffer[Tuple]()
  private var stateTuple: Tuple = _

  override def processTuple(tuple: Tuple, port: Int): Iterator[TupleLike] = {
    port match {
      case 0 =>
        if (stateTuple == null)
          stateTuple = tuple
      case 1 =>
        buffer += tuple
    }
    Iterator()
  }

  override def onFinishProduceState(port: Int): State = State().fromTuple(stateTuple)

  override def onFinish(port: Int): Iterator[TupleLike] = buffer.iterator
}
