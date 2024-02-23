package edu.uci.ics.texera.workflow.common.operators

import edu.uci.ics.amber.engine.architecture.worker.DataProcessor.{EndOfIteration, StartOfIteration}
import edu.uci.ics.amber.engine.architecture.worker.PauseManager
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCClient
import edu.uci.ics.amber.engine.common.{IOperatorExecutor, InputExhausted}
import edu.uci.ics.amber.engine.common.tuple.ITuple
import edu.uci.ics.amber.engine.common.workflow.PortIdentity
import edu.uci.ics.texera.workflow.common.tuple.Tuple

trait OperatorExecutor extends IOperatorExecutor {

  override def processTuple(
      tuple: Either[ITuple, InputExhausted],
      input: Int,
      pauseManager: PauseManager,
      asyncRPCClient: AsyncRPCClient
  ): Iterator[(ITuple, Option[PortIdentity])] = {
    if (tuple.isLeft && tuple.left.get.isInstanceOf[StartOfIteration]) {
      return Iterator((tuple.left.get, Option.empty))
    }
    if (tuple.isLeft && tuple.left.get.isInstanceOf[EndOfIteration]) {
      return Iterator((tuple.left.get, Option.empty))
    }
    processTexeraTuple(
      tuple.asInstanceOf[Either[Tuple, InputExhausted]],
      input,
      pauseManager,
      asyncRPCClient
    ).map(t => (t, Option.empty))
  }

  def processTexeraTuple(
      tuple: Either[Tuple, InputExhausted],
      input: Int,
      pauseManager: PauseManager,
      asyncRPCClient: AsyncRPCClient
  ): Iterator[Tuple]

}
