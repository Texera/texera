package edu.uci.ics.amber.engine.common

import edu.uci.ics.amber.engine.architecture.worker.DataProcessor.{EndOfIteration, StartOfIteration}
import edu.uci.ics.amber.engine.architecture.worker.PauseManager
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCClient
import edu.uci.ics.amber.engine.common.tuple.ITuple
import edu.uci.ics.amber.engine.common.workflow.PortIdentity

trait ISinkOperatorExecutor extends IOperatorExecutor {

  override def processTuple(
      tuple: Either[ITuple, InputExhausted],
      input: Int,
      pauseManager: PauseManager,
      asyncRPCClient: AsyncRPCClient
  ): Iterator[(ITuple, Option[PortIdentity])] = {
    if (tuple.isLeft && tuple.left.get.isInstanceOf[StartOfIteration]) {
      return Iterator.empty
    }
    if (tuple.isLeft && tuple.left.get.isInstanceOf[EndOfIteration]) {
      return Iterator.empty
    }
    consume(tuple, input)
    Iterator.empty
  }

  def consume(tuple: Either[ITuple, InputExhausted], input: Int): Unit
}
