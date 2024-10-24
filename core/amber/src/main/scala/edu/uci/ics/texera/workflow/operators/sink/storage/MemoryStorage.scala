package edu.uci.ics.texera.workflow.operators.sink.storage

import edu.uci.ics.amber.engine.common.model.tuple.{Schema, Tuple}

import scala.collection.mutable.ArrayBuffer

class MemoryStorage extends SinkStorageReader with SinkStorageWriter {

  private val results = new ArrayBuffer[Tuple]()

  override def getAll: Iterable[Tuple] =
    synchronized {
      results
    }

  override def putOne(tuple: Tuple): Unit =
    synchronized {
      results += tuple
    }

  override def removeOne(tuple: Tuple): Unit =
    synchronized {
      results -= tuple
    }

  override def getAllAfter(offset: Int): Iterable[Tuple] =
    synchronized {
      results.slice(offset, results.size)
    }

  override def clear(): Unit =
    synchronized {
      results.clear()
    }

  override def open(): Unit = {}

  override def close(): Unit = {}

  override def getStorageWriter: SinkStorageWriter = this

  override def getRange(from: Int, to: Int): Iterable[Tuple] =
    synchronized {
      results.slice(from, to)
    }

  override def getCount: Long = results.length

  override def getSchema: Schema = schema

  override def setSchema(schema: Schema): Unit = {
    this.schema = schema
  }
}
