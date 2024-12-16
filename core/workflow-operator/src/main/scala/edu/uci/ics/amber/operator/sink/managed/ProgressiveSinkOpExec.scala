package edu.uci.ics.amber.operator.sink.managed

import edu.uci.ics.amber.core.executor.SinkOperatorExecutor
import edu.uci.ics.amber.core.storage.model.BufferedItemWriter
import edu.uci.ics.amber.core.storage.result.ResultStorage
import edu.uci.ics.amber.core.tuple.{Tuple, TupleLike}
import edu.uci.ics.amber.operator.sink.{IncrementalOutputMode, ProgressiveUtils}
import edu.uci.ics.amber.util.JSONUtils.objectMapper
import edu.uci.ics.amber.virtualidentity.{OperatorIdentity, WorkflowIdentity}
import edu.uci.ics.amber.workflow.PortIdentity

class ProgressiveSinkOpExec(
    descString: String,
    workflowIdentity: WorkflowIdentity
) extends SinkOperatorExecutor {
  private val desc : ProgressiveSinkOpDesc = objectMapper.readValue(descString, classOf[ProgressiveSinkOpDesc])
  val writer: BufferedItemWriter[Tuple] =
    ResultStorage.getOpResultStorage(workflowIdentity).get(OperatorIdentity(desc.getUpstreamId.get.id)).writer()

  override def open(): Unit = {
    writer.open()
  }

  override def consumeTuple(
      tuple: Tuple,
      input: Int
  ): Unit = {
    desc.outputMode match {
      case IncrementalOutputMode.SET_SNAPSHOT => updateSetSnapshot(tuple)
      case IncrementalOutputMode.SET_DELTA    => writer.putOne(tuple)
    }
  }

  private def updateSetSnapshot(deltaUpdate: Tuple): Unit = {
    val (isInsertion, tupleValue) = ProgressiveUtils.getTupleFlagAndValue(deltaUpdate)

    if (isInsertion) {
      writer.putOne(tupleValue)
    } else {
      writer.removeOne(tupleValue)
    }
  }

  override def onFinishMultiPort(port: Int): Iterator[(TupleLike, Option[PortIdentity])] = {
    writer.close()
    Iterator.empty
  }

  override def processTuple(tuple: Tuple, port: Int): Iterator[TupleLike] = Iterator.empty
}
