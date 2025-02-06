package edu.uci.ics.amber.operator.state

import edu.uci.ics.amber.core.executor.OperatorExecutor
import edu.uci.ics.amber.core.marker.State
import edu.uci.ics.amber.core.tuple.{Tuple, TupleLike}
import edu.uci.ics.amber.util.JSONUtils.objectMapper

class DataToStateOpExec(descString: String) extends OperatorExecutor {
  private val desc: DataToStateOpDesc =
    objectMapper.readValue(descString, classOf[DataToStateOpDesc])
  private var stateTuple: Option[Tuple] = None

  override def processTuple(tuple: Tuple, port: Int): Iterator[TupleLike] = {
    if (stateTuple.isEmpty)
      stateTuple = Some(tuple)
    Iterator.empty
  }

  override def produceStateOnFinish(port: Int): Option[State] =
    Some(State(stateTuple, desc.passToAllDownstream.get))
}
