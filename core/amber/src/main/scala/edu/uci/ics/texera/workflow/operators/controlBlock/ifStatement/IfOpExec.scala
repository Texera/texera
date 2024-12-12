package edu.uci.ics.texera.workflow.operators.controlBlock.ifStatement

import edu.uci.ics.amber.engine.common.executor.OperatorExecutor
import edu.uci.ics.amber.engine.common.model.State
import edu.uci.ics.amber.engine.common.model.tuple.{Tuple, TupleLike}
import edu.uci.ics.amber.engine.common.workflow.PortIdentity

class IfOpExec(conditionName: String) extends OperatorExecutor {
  private var outputPort: PortIdentity = PortIdentity(1) // by default, it should be the true port.

  //This function can handle one or more states.
  //The state can have mutiple key-value pairs. Keys are not identified by conditionName will be ignored.
  //It can accept any value that can be converted to a boolean. For example, Int 1 will be converted to true.
  override def processState(state: State, port: Int): Option[State] = {
    outputPort =
      if (state.get(conditionName).asInstanceOf[Boolean]) PortIdentity(1) else PortIdentity()
    Some(state)
  }

  override def processTupleMultiPort(
      tuple: Tuple,
      port: Int
  ): Iterator[(TupleLike, Option[PortIdentity])] =
    Iterator((tuple, Some(outputPort)))

  override def processTuple(tuple: Tuple, port: Int): Iterator[TupleLike] = ???
}
