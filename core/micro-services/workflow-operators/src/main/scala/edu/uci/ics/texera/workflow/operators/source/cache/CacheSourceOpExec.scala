package edu.uci.ics.texera.workflow.operators.source.cache

import com.typesafe.scalalogging.LazyLogging
import edu.uci.ics.amber.core.tuple.TupleLike
import edu.uci.ics.amber.storage.result.SinkStorageReader
import edu.uci.ics.texera.workflow.utils.executor.SourceOperatorExecutor

class CacheSourceOpExec(storage: SinkStorageReader)
    extends SourceOperatorExecutor
    with LazyLogging {

  override def produceTuple(): Iterator[TupleLike] = storage.getAll.iterator

}
