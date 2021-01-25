package edu.uci.ics.amber.engine.architecture.messaginglayer

import edu.uci.ics.amber.engine.architecture.worker.neo.WorkerInternalQueue
import com.softwaremill.macwire.wire
import edu.uci.ics.amber.engine.architecture.worker.neo.WorkerInternalQueue.{
  EndMarker,
  EndOfAllMarker,
  InputTuple,
  SenderChangeMarker
}
import edu.uci.ics.amber.engine.common.ambermessage.WorkerMessage.{DataFrame, EndOfUpstream}
import edu.uci.ics.amber.engine.common.ambertag.OperatorIdentifier
import edu.uci.ics.amber.engine.common.ambertag.neo.VirtualIdentity.WorkerActorVirtualIdentity
import edu.uci.ics.amber.engine.common.tuple.ITuple
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec

class BatchToTupleConverterSpec extends AnyFlatSpec with MockFactory {
  private val mockInternalQueue = mock[WorkerInternalQueue]
  private val fakeID = WorkerActorVirtualIdentity("testReceiver")

  "tuple producer" should "break batch into tuples and output" in {
    val batchToTupleConverter = wire[BatchToTupleConverter]
    val inputBatch = DataFrame(Array.fill(4)(ITuple(1, 2, 3, 5, "9.8", 7.6)))
    inSequence {
      (mockInternalQueue.appendElement _).expects(
        SenderChangeMarker(OperatorIdentifier("test", "test"))
      )
      inputBatch.frame.foreach { i =>
        (mockInternalQueue.appendElement _).expects(InputTuple(i))
      }
      (mockInternalQueue.appendElement _).expects(EndMarker())
      (mockInternalQueue.appendElement _).expects(EndOfAllMarker())
    }
    batchToTupleConverter.registerInput(fakeID, OperatorIdentifier("test", "test"))
    batchToTupleConverter.processDataPayload(fakeID, Iterable(inputBatch))
    batchToTupleConverter.processDataPayload(fakeID, Iterable(EndOfUpstream()))
  }

  "tuple producer" should "be aware of upstream change" in {
    val batchToTupleConverter = wire[BatchToTupleConverter]
    val inputBatchFromUpstream1 = DataFrame(Array.fill(4)(ITuple(1, 2, 3, 5, "9.8", 7.6)))
    val inputBatchFromUpstream2 = DataFrame(Array.fill(4)(ITuple(2, 3, 4, 5, "6.7", 8.9)))
    inSequence {
      (mockInternalQueue.appendElement _).expects(
        SenderChangeMarker(OperatorIdentifier("test", "op0"))
      )
      inputBatchFromUpstream1.frame.foreach { i =>
        (mockInternalQueue.appendElement _).expects(InputTuple(i))
      }
      (mockInternalQueue.appendElement _).expects(
        SenderChangeMarker(OperatorIdentifier("test", "op1"))
      )
      inputBatchFromUpstream2.frame.foreach { i =>
        (mockInternalQueue.appendElement _).expects(InputTuple(i))
      }
      (mockInternalQueue.appendElement _).expects(EndMarker())
      (mockInternalQueue.appendElement _).expects(
        SenderChangeMarker(OperatorIdentifier("test", "op0"))
      )
      (mockInternalQueue.appendElement _).expects(EndMarker())
      (mockInternalQueue.appendElement _).expects(EndOfAllMarker())
    }
    val first = WorkerActorVirtualIdentity("first upstream")
    val second = WorkerActorVirtualIdentity("second upstream")
    batchToTupleConverter.registerInput(first, OperatorIdentifier("test", "op0"))
    batchToTupleConverter.registerInput(second, OperatorIdentifier("test", "op1"))
    batchToTupleConverter.processDataPayload(first, Iterable(inputBatchFromUpstream1))
    batchToTupleConverter.processDataPayload(
      second,
      Iterable(inputBatchFromUpstream2, EndOfUpstream())
    )
    batchToTupleConverter.processDataPayload(first, Iterable(EndOfUpstream()))

  }

}
