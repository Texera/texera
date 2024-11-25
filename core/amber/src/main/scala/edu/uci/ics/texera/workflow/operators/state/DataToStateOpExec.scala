package edu.uci.ics.texera.workflow.operators.state

import edu.uci.ics.amber.engine.common.executor.OperatorExecutor
import edu.uci.ics.amber.engine.common.model.State
import edu.uci.ics.amber.engine.common.model.tuple.{Tuple, TupleLike}

import scala.collection.mutable

class DataToStateOpExec(passToAllDownstream: Boolean) extends OperatorExecutor {
  private val dataTuples = new mutable.ArrayBuffer[Tuple]()
  private var stateTuple: Option[Tuple] = None

  override def processTuple(tuple: Tuple, port: Int): Iterator[TupleLike] = {
    port match {
      case 0 =>
        if (stateTuple.isEmpty)
          stateTuple = Some(tuple)
      case 1 =>
        dataTuples += tuple
    }
    Iterator.empty
  }

  override def produceStateOnFinish(port: Int): Option[State] =
    Some(State(stateTuple, passToAllDownstream))

  override def onFinish(port: Int): Iterator[TupleLike] = dataTuples.iterator
}
