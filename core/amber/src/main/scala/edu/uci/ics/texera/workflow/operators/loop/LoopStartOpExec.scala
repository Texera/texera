package edu.uci.ics.texera.workflow.operators.loop

import edu.uci.ics.amber.engine.architecture.worker.DataProcessor.{
  EndOfIteration,
  FinalizeOperator,
  StartOfIteration
}
import edu.uci.ics.amber.engine.architecture.worker.PauseManager
import edu.uci.ics.amber.engine.common.InputExhausted
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCClient
import edu.uci.ics.amber.engine.common.tuple.ITuple
import edu.uci.ics.amber.engine.common.virtualidentity.ActorVirtualIdentity
import edu.uci.ics.amber.engine.common.workflow.PortIdentity
import edu.uci.ics.texera.workflow.common.operators.OperatorExecutor
import edu.uci.ics.texera.workflow.common.tuple.Tuple
import edu.uci.ics.texera.workflow.common.tuple.schema.Schema

import scala.collection.mutable

class LoopStartOpExec(
    val outputSchema: Schema,
    val workerId: ActorVirtualIdentity
) extends OperatorExecutor {
  var iteration = 0
  var data = new mutable.ArrayBuffer[ITuple]
  var buffer = new mutable.ArrayBuffer[ITuple]
  override def processTuple(
      tuple: Either[ITuple, InputExhausted],
      input: Int,
      pauseManager: PauseManager,
      asyncRPCClient: AsyncRPCClient
  ): Iterator[(ITuple, Option[PortIdentity])] = {
    input match {
      case 0 =>
        tuple match {
          case Left(t) =>
            t match {
              case t: StartOfIteration =>
                {
                  if (iteration == buffer.length) {
                    return Iterator((FinalizeOperator(), None))
                  }
                  iteration += 1
                  if (outputSchema.containsAttribute("Iteration")) {
                    data.iterator.map(dt => (
                      Tuple.newBuilder(outputSchema).add(outputSchema.getAttribute("Iteration"), iteration-1)
                        .add(dt.asInstanceOf[Tuple])
                        .add(buffer(iteration-1).asInstanceOf[Tuple]).build
                      ,
                      None
                    ))
                  } else {
                    data.iterator.map(t => (t, None))
                  }
                } ++ Iterator((StartOfIteration(workerId), None))

              case t =>
                buffer.append(t)
                Iterator.empty
            }
          case Right(_) =>
            Iterator((StartOfIteration(workerId), None))
        }
      case 1 =>
        tuple match {
          case Left(t)  => data.append(t)
          case Right(_) =>
        }
        Iterator.empty
    }
  }

  override def open(): Unit = {}

  override def close(): Unit = {}

  override def processTexeraTuple(
      tuple: Either[Tuple, InputExhausted],
      input: Int,
      pauseManager: PauseManager,
      asyncRPCClient: AsyncRPCClient
  ): Iterator[Tuple] = ???
}
