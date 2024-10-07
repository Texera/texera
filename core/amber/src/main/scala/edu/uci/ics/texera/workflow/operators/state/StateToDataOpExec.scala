package edu.uci.ics.texera.workflow.operators.state

import edu.uci.ics.amber.engine.common.tuple.amber.TupleLike
import edu.uci.ics.amber.engine.common.workflow.PortIdentity
import edu.uci.ics.texera.workflow.common.State
import edu.uci.ics.texera.workflow.common.operators.OperatorExecutor
import edu.uci.ics.texera.workflow.common.tuple.Tuple

import scala.collection.mutable

class StateToDataOpExec extends OperatorExecutor {
  private val outputTuples = new mutable.ArrayBuffer[(Tuple, Option[PortIdentity])]()
  private var stateTuple: Option[Tuple] = None

  override def processState(state: State, port: Int): Option[State] = {
    if (stateTuple.isEmpty) {
      stateTuple = Some(state.toTuple)
      None
    } else {
      Some(state)
    }
  }

  override def processTuple(tuple: Tuple, port: Int): Iterator[TupleLike] = {
    outputTuples += ((tuple, Some(PortIdentity(1))))
    Iterator.empty
  }

  override def onFinishMultiPort(port: Int): Iterator[(TupleLike, Option[PortIdentity])] = {
    if (stateTuple.isDefined) {
      outputTuples += ((stateTuple.get, Some(PortIdentity())))
    }
    outputTuples.iterator
  }
}
