package edu.uci.ics.texera.workflow.operators.controlBlock.state

import edu.uci.ics.amber.engine.common.executor.OperatorExecutor
import edu.uci.ics.amber.engine.common.model.State
import edu.uci.ics.amber.engine.common.model.tuple.{Tuple, TupleLike}

class DataToStateOpExec(passToAllDownstream: Boolean) extends OperatorExecutor {
  private var stateTuple: Option[Tuple] = None

  override def processTuple(tuple: Tuple, port: Int): Iterator[TupleLike] = {
    if (stateTuple.isEmpty)
      stateTuple = Some(tuple)
    Iterator.empty
  }

  override def produceStateOnFinish(port: Int): Option[State] =
    Some(State(stateTuple, passToAllDownstream))
}
