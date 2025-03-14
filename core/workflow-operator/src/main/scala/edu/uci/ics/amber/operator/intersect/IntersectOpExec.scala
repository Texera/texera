package edu.uci.ics.amber.operator.intersect

import edu.uci.ics.amber.core.executor.OperatorExecutor
import edu.uci.ics.amber.core.tuple.{Tuple, TupleLike}

import scala.collection.mutable

class IntersectOpExec extends OperatorExecutor {
  private var leftSet: mutable.HashSet[Tuple] = _
  private var rightSet: mutable.HashSet[Tuple] = _
  private var exhaustedCounter: Int = _

  override def open(): Unit = {
    leftSet = new mutable.HashSet[Tuple]()
    rightSet = new mutable.HashSet[Tuple]()
    exhaustedCounter = 0
  }

  override def close(): Unit = {
    leftSet.clear()
    rightSet.clear()
  }

  override def processTuple(tuple: Tuple, port: Int): Iterator[TupleLike] = {

    // add the tuple to corresponding set
    if (port == 0) leftSet += tuple else rightSet += tuple
    Iterator()

  }

  override def onFinish(port: Int): Iterator[TupleLike] = {
    exhaustedCounter += 1
    if (exhaustedCounter == 2) {
      // both streams are exhausted, take the intersect and return the results
      leftSet.intersect(rightSet).iterator
    } else {
      // only one of the stream is exhausted, continue accepting tuples
      Iterator()
    }
  }
}
