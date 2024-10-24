package edu.uci.ics.amber.operator.sink.storage

import edu.uci.ics.amber.core.tuple.Tuple

trait SinkStorageWriter {
  def open(): Unit

  def close(): Unit

  def putOne(tuple: Tuple): Unit

  def removeOne(tuple: Tuple): Unit

}
