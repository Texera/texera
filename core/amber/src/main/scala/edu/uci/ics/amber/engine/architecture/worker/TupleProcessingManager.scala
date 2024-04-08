package edu.uci.ics.amber.engine.architecture.worker

import edu.uci.ics.amber.engine.architecture.worker.DataProcessor.{FinalizeExecutor, FinalizePort}
import edu.uci.ics.amber.engine.architecture.worker.TupleProcessingManager.{
  OutputTupleIterator,
  InputTupleIterator
}
import edu.uci.ics.amber.engine.common.tuple.amber.TupleLike
import edu.uci.ics.amber.engine.common.virtualidentity.ActorVirtualIdentity
import edu.uci.ics.amber.engine.common.workflow.PortIdentity
import edu.uci.ics.texera.workflow.common.tuple.Tuple

import scala.collection.{AbstractIterator, mutable}

object TupleProcessingManager {
  class InputTupleIterator extends AbstractIterator[(Tuple, PortIdentity)] {
    private var inputBatch: Array[Tuple] = Array.empty
    private var currentPortId: Option[PortIdentity] = None

    // Set the batch of tuples for processing. Resets the iterator to the start of the new batch.
    def setBatch(portId: PortIdentity, batch: Array[Tuple]): Unit = {
      currentPortId = Some(portId)
      inputBatch = batch
      currentIndex = 0 // Reset index to the start for the new batch
    }

    private var currentIndex: Int = 0

    // Retrieves the current tuple without advancing the index.
    def getCurrentTuple: Option[Tuple] = {
      if (inputBatch.nonEmpty && currentIndex > 0 && currentIndex <= inputBatch.length) {
        Some(inputBatch(currentIndex - 1))
      } else {
        None
      }
    }

    // Check if there are more tuples in the batch to process.
    override def hasNext: Boolean = currentIndex < inputBatch.length

    // Advances to the next tuple in the batch and returns it.
    override def next(): (Tuple, PortIdentity) = {
      if (!hasNext) throw new NoSuchElementException("No more tuples in the batch")
      val tuple = inputBatch(currentIndex)
      currentIndex += 1
      (tuple, currentPortId.getOrElse(throw new IllegalStateException("Port ID is not set")))
    }
  }
  class OutputTupleIterator extends AbstractIterator[(TupleLike, Option[PortIdentity])] {
    val queue = new mutable.ListBuffer[(TupleLike, Option[PortIdentity])]
    @transient private var internalIter: Iterator[(TupleLike, Option[PortIdentity])] =
      Iterator.empty

    def setInternalIter(outputIter: Iterator[(TupleLike, Option[PortIdentity])]): Unit = {
      this.internalIter = Option(outputIter).getOrElse(Iterator.empty)
    }

    def getInternalIter: Iterator[(TupleLike, Option[PortIdentity])] = internalIter

    override def hasNext: Boolean = internalIter.hasNext || queue.nonEmpty

    override def next(): (TupleLike, Option[PortIdentity]) = {
      if (internalIter.hasNext) {
        internalIter.next()
      } else {
        queue.remove(0)
      }
    }

    def appendSpecialTupleToEnd(tuple: TupleLike): Unit = {
      queue.append((tuple, None))
    }
  }
}
class TupleProcessingManager(val actorId: ActorVirtualIdentity) {

  val inputIterator: InputTupleIterator = new InputTupleIterator()

  val outputIterator: OutputTupleIterator = new OutputTupleIterator()

  def finalizeOutput(portIds: Set[PortIdentity]): Unit = {
    portIds
      .foreach(outputPortId =>
        outputIterator.appendSpecialTupleToEnd(FinalizePort(outputPortId, input = false))
      )
    outputIterator.appendSpecialTupleToEnd(FinalizeExecutor())
  }
}
