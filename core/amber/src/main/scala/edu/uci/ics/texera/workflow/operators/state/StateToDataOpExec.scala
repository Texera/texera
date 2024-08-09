package edu.uci.ics.texera.workflow.operators.state

import edu.uci.ics.amber.engine.common.tuple.amber.TupleLike
import edu.uci.ics.amber.engine.common.workflow.PortIdentity
import edu.uci.ics.texera.workflow.common.State
import edu.uci.ics.texera.workflow.common.operators.OperatorExecutor
import edu.uci.ics.texera.workflow.common.tuple.Tuple

class StateToDataOpExec extends OperatorExecutor {
  private var stateTuple: Tuple = _

  override def processState(state: State, port: Int): State = {
    if (state.size > 0)
      stateTuple = state.toTuple
    State()
  }

  override def processTupleMultiPort(
      tuple: Tuple,
      port: Int
  ): Iterator[(TupleLike, Option[PortIdentity])] = {
    if (stateTuple != null) {
      val outputTuple = stateTuple
      stateTuple = null
      Array((outputTuple, Some(PortIdentity())), (tuple, Some(PortIdentity(1)))).iterator
    } else {
      Iterator((tuple, Some(PortIdentity(1))))
    }
  }

  override def processTuple(tuple: Tuple, port: Int): Iterator[TupleLike] = throw new NotImplementedError()
}
