package edu.uci.ics.texera.workflow.operators.controlBlock.loop

import edu.uci.ics.amber.engine.common.executor.OperatorExecutor
import edu.uci.ics.amber.engine.common.model.State
import edu.uci.ics.amber.engine.common.model.tuple.{AttributeType, Tuple, TupleLike}

import scala.collection.mutable

class LoopStartOpExec(iteration: Int) extends OperatorExecutor {
  private val data = new mutable.ArrayBuffer[Tuple]
  private var currentIteration = 0

  def produceStateOnIterationStart(): State = {
    currentIteration += 1
    val state = State(passToAllDownstream = true)
    state.add("currentIteration", currentIteration, AttributeType.INTEGER)
    state
  }

  def produceTupleOnIterationStart(): Iterator[TupleLike] = {
    //data.iterator
    Iterator.empty
  }

  def checkCondition(): Boolean = {
    iteration>currentIteration
  }

  override def processTuple(tuple: Tuple, port: Int): Iterator[TupleLike] = {
    data.append(tuple)
    Iterator.empty
  }

  override def onFinish(port: Int): Iterator[TupleLike] = {
    currentIteration += 1
    data.iterator
  }
}
