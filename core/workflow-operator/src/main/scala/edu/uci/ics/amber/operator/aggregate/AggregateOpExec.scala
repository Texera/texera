package edu.uci.ics.amber.operator.aggregate

import edu.uci.ics.amber.core.executor.OperatorExecutor
import edu.uci.ics.amber.core.tuple.{Tuple, TupleLike}
import edu.uci.ics.amber.util.JSONUtils.objectMapper

import scala.collection.mutable

/**
  * AggregateOpExec performs aggregation operations on input tuples, optionally grouping them by specified keys.
  */
class AggregateOpExec(descString: String) extends OperatorExecutor {
  private val desc: AggregateOpDesc = objectMapper.readValue(descString, classOf[AggregateOpDesc])
  private var keyedPartialAggregates: mutable.HashMap[List[Object], List[Object]] = _
  private var distributedAggregations: List[DistributedAggregation[Object]] = _

  override def open(): Unit = {
    keyedPartialAggregates = new mutable.HashMap[List[Object], List[Object]]()
    distributedAggregations = null
  }

  override def close(): Unit = {
    keyedPartialAggregates.clear()
    distributedAggregations = null
  }

  override def processTuple(tuple: Tuple, port: Int): Iterator[TupleLike] = {

    // Initialize distributedAggregations if it's not yet initialized
    if (distributedAggregations == null) {
      distributedAggregations = desc.aggregations.map(agg =>
        agg.getAggFunc(tuple.getSchema.getAttribute(agg.attribute).getType)
      )
    }

    // Construct the group key
    val key = desc.groupByKeys.map(tuple.getField[Object])

    // Get or initialize the partial aggregate for the key
    val partialAggregates =
      keyedPartialAggregates.getOrElseUpdate(key, distributedAggregations.map(_.init()))

    // Update the partial aggregates with the current tuple
    val updatedAggregates = (distributedAggregations zip partialAggregates).map {
      case (aggregation, partial) => aggregation.iterate(partial, tuple)
    }

    keyedPartialAggregates(key) = updatedAggregates
    Iterator.empty

  }

  override def onFinish(port: Int): Iterator[TupleLike] = {
    // Finalize aggregation for all keys and produce the result
    keyedPartialAggregates.iterator.map {
      case (key, partials) =>
        val finalAggregates = partials.zipWithIndex.map {
          case (partial, index) => distributedAggregations(index).finalAgg(partial)
        }
        TupleLike(key ++ finalAggregates)
    }
  }
}
