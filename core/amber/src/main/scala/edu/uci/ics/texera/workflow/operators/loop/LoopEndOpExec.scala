package edu.uci.ics.texera.workflow.operators.loop

import edu.uci.ics.amber.engine.architecture.worker.DataProcessor.{EndOfIteration, StartOfIteration}
import edu.uci.ics.amber.engine.architecture.worker.PauseManager
import edu.uci.ics.amber.engine.common.InputExhausted
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCClient
import edu.uci.ics.amber.engine.common.tuple.ITuple
import edu.uci.ics.amber.engine.common.virtualidentity.ActorVirtualIdentity
import edu.uci.ics.amber.engine.common.workflow.PortIdentity
import edu.uci.ics.texera.workflow.common.operators.OperatorExecutor
import edu.uci.ics.texera.workflow.common.tuple.Tuple

import scala.collection.mutable

class LoopEndOpExec(val workerId: ActorVirtualIdentity) extends OperatorExecutor {
  var iteration = 0
  var buffer = new mutable.ArrayBuffer[(ITuple, Option[PortIdentity])]

  override def open(): Unit = {}

  override def close(): Unit = {}

  override def processTuple(
                             tuple: Either[ITuple, InputExhausted],
                             input: Int,
                             pauseManager: PauseManager,
                             asyncRPCClient: AsyncRPCClient
                           ): Iterator[(ITuple, Option[PortIdentity])] = {
    tuple match {
      case Left(t) =>
        t match {
          case t: StartOfIteration =>
            Iterator((EndOfIteration(t.workerId, workerId), None))
          case t =>
            buffer.append((t, None))
            Iterator()
        }

      case Right(_) => buffer.iterator
    }
  }

  override def processTexeraTuple(
      tuple: Either[Tuple, InputExhausted],
      input: Int,
      pauseManager: PauseManager,
      asyncRPCClient: AsyncRPCClient
  ): Iterator[Tuple] = ???
}
