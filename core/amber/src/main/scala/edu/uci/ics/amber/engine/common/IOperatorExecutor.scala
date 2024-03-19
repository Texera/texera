package edu.uci.ics.amber.engine.common

import edu.uci.ics.amber.engine.common.tuple.amber.TupleLike
import edu.uci.ics.amber.engine.common.workflow.PortIdentity
import edu.uci.ics.texera.workflow.common.tuple.Tuple

trait IOperatorExecutor {

  def open(): Unit

  def close(): Unit

  def processTupleMultiPort(tuple: Tuple, port: Int): Iterator[(TupleLike, Option[PortIdentity])]

  def onFinishMultiPort(port: Int): Iterator[(TupleLike, Option[PortIdentity])]

}
