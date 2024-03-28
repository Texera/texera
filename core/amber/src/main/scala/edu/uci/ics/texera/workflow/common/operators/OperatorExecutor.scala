package edu.uci.ics.texera.workflow.common.operators

import edu.uci.ics.amber.engine.common.tuple.amber.TupleLike
import edu.uci.ics.amber.engine.common.workflow.PortIdentity
import edu.uci.ics.texera.workflow.common.State
import edu.uci.ics.texera.workflow.common.tuple.Tuple

trait OperatorExecutor {

  def open(): Unit = {}

  def close(): Unit = {}

  def processTupleMultiPort(
      tuple: Tuple,
      port: Int
  ): Iterator[(TupleLike, Option[PortIdentity])] = {
    processTuple(tuple, port).map(t => (t, None))
  }

  def processTuple(tuple: Tuple, port: Int): Iterator[TupleLike]

  def processMarker(marker: State, port: Int): Iterator[State] = Iterator.empty

  def onFinishMultiPort(port: Int): Iterator[(TupleLike, Option[PortIdentity])] = {
    onFinish(port).map(t => (t, None))
  }
  def onFinish(port: Int): Iterator[TupleLike] = Iterator.empty

  def onOutputFinish(port: Int): Iterator[State] = Iterator.empty

}
