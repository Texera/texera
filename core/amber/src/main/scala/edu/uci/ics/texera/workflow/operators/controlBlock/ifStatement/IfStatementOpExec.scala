package edu.uci.ics.texera.workflow.operators.controlBlock.ifStatement

import edu.uci.ics.amber.engine.common.executor.OperatorExecutor
import edu.uci.ics.amber.engine.common.model.State
import edu.uci.ics.amber.engine.common.model.tuple.{Tuple, TupleLike}
import edu.uci.ics.amber.engine.common.workflow.PortIdentity

class IfStatementOpExec extends OperatorExecutor {
  private var output_port: Int = _

  override def processState(state: State, port: Int): Option[State] = {
    output_port = if (state.get(1).asInstanceOf[Boolean]) 0 else 1
    None
  }

  override def processTupleMultiPort(tuple: Tuple, port: Int): Iterator[(TupleLike, Option[PortIdentity])] = Iterator((tuple, Some(PortIdentity(output_port))))

  override def processTuple(tuple: Tuple, port: Int): Iterator[TupleLike] = ???
}
