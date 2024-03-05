package edu.uci.ics.texera.workflow.operators.source.cache

import com.typesafe.scalalogging.LazyLogging
import edu.uci.ics.amber.engine.common.ISourceOperatorExecutor
import edu.uci.ics.amber.engine.common.tuple.amber.TupleLike
import edu.uci.ics.texera.workflow.operators.sink.storage.SinkStorageReader

class CacheSourceOpExec(storage: SinkStorageReader)
    extends ISourceOperatorExecutor
    with LazyLogging {

  override def produceTuple(): Iterator[TupleLike] = storage.getAll.iterator

  override def open(): Unit = {}

  override def close(): Unit = {}
}
