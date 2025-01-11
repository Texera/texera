package edu.uci.ics.amber.core.storage.result

import com.mongodb.client.MongoCursor
import com.mongodb.client.model.Sorts
import edu.uci.ics.amber.core.storage.StorageConfig
import edu.uci.ics.amber.core.storage.model.{BufferedItemWriter, VirtualDocument}
import edu.uci.ics.amber.core.storage.util.mongo.{MongoCollectionManager, MongoDatabaseManager}
import org.bson.Document

import java.net.URI
import java.util.Date
import scala.collection.mutable

/**
  * MongoDocument provides an implementation of VirtualDocument for MongoDB.
  * It supports various operations like read, write, and remove on a MongoDB collection.
  * @param id the identifier for the MongoDB collection.
  * @param toDocument a function that converts an item of type T to a MongoDB Document.
  * @param fromDocument a function that converts a MongoDB Document to an item of type T.
  * @tparam T the type of data items stored in the document.
  */
class MongoDocument[T >: Null <: AnyRef](
    id: String,
    var toDocument: T => Document,
    var fromDocument: Document => T
) extends VirtualDocument[T] {

  var previousCount: mutable.Map[String, Int] = mutable.Map()
  var previousNumStats: mutable.Map[String, Map[String, Double]] = mutable.Map()
  var previousDateStats: mutable.Map[String, Map[String, Date]] = mutable.Map()
  var previousCatStats: mutable.Map[String, Map[String, Int]] = mutable.Map()
  var previousReachedLimit: Boolean = false

  /**
    * The batch size for committing items to the MongoDB collection.
    */
  val commitBatchSize: Int = StorageConfig.mongodbBatchSize

  /**
    * Drops the existing MongoDB collection with the given identifier.
    */
  MongoDatabaseManager.dropCollection(id)

  /**
    * Lazy initialization of the MongoDB collection manager.
    */
  @transient lazy val collectionMgr: MongoCollectionManager = MongoDatabaseManager.getCollection(id)

  /**
    * This method is not supported for MongoDocument.
    * @throws UnsupportedOperationException always thrown when this method is called.
    * @return nothing, this method always throws an exception.
    */
  override def getURI: URI =
    throw new UnsupportedOperationException("getURI is not supported for MongoDocument")

  /**
    * Remove the MongoDB collection.
    */
  override def clear(): Unit = MongoDatabaseManager.dropCollection(id)

  /**
    * Return a buffered item writer for the MongoDB collection.
    * @return a new instance of MongoDBBufferedItemWriter.
    */
  override def writer(writerIdentifier: String): BufferedItemWriter[T] = {
    new MongoDBBufferedItemWriter[T](
      commitBatchSize,
      id,
      toDocument
    )
  }

  /**
    * Create an iterator that wraps a MongoCursor and converts each Document to an item of type T.
    * @param cursor the MongoCursor to wrap.
    * @return an iterator of items of type T.
    */
  private[this] def mkTIterator(cursor: MongoCursor[Document]): Iterator[T] = {
    new Iterator[T] {
      override def hasNext: Boolean = cursor.hasNext

      override def next(): T = {
        fromDocument(cursor.next())
      }
    }.iterator
  }

  /**
    * Get an iterator that iterates over all items in the MongoDB collection.
    * @return an iterator of items.
    */
  override def get(): Iterator[T] = {
    val cursor = collectionMgr.accessDocuments.sort(Sorts.ascending("_id")).cursor()
    mkTIterator(cursor)
  }

  /**
    * Get an iterator of items in the specified range.
    * @param from the starting index (inclusive).
    * @param until the ending index (exclusive).
    * @return an iterator of items in the specified range.
    */
  override def getRange(from: Int, until: Int): Iterator[T] = {
    val cursor =
      collectionMgr.accessDocuments
        .sort(Sorts.ascending("_id"))
        .limit(until - from)
        .skip(from)
        .cursor()
    mkTIterator(cursor)
  }

  /**
    * Get an iterator of items starting after the specified offset.
    * @param offset the starting index (exclusive).
    * @return an iterator of items starting after the specified offset.
    */
  override def getAfter(offset: Int): Iterator[T] = {
    val cursor = collectionMgr.accessDocuments.sort(Sorts.ascending("_id")).skip(offset).cursor()
    mkTIterator(cursor)
  }

  /**
    * Get the item at the specified index.
    * @param i the index of the item.
    * @return the item at the specified index.
    * @throws RuntimeException if the index is out of bounds.
    */
  override def getItem(i: Int): T = {
    val cursor =
      collectionMgr.accessDocuments
        .sort(Sorts.ascending("_id"))
        .limit(1)
        .skip(i)
        .cursor()

    if (!cursor.hasNext) {
      throw new RuntimeException(f"Index $i out of bounds")
    }
    fromDocument(cursor.next())
  }

  /**
    * Get the count of items in the MongoDB collection.
    * @return the number of items.
    */
  override def getCount: Long = {
    collectionMgr.getCount
  }

  def getNumericColStats: Map[String, Map[String, Double]] = {
    val offset: Int = previousCount.getOrElse("numeric_offset", 0)
    val currentResult: Map[String, Map[String, Double]] =
      collectionMgr.calculateNumericStats(offset)

    currentResult.keys.foreach(field => {
      val (prevMin, prevMax, prevMean) =
        (
          previousNumStats.getOrElse(field, Map("min" -> Double.MaxValue))("min"),
          previousNumStats.getOrElse(field, Map("max" -> Double.MinValue))("max"),
          previousNumStats.getOrElse(field, Map("mean" -> 0.0))("mean")
        )

      val (minValue, maxValue, meanValue, count) =
        (
          currentResult(field)("min"),
          currentResult(field)("max"),
          currentResult(field)("mean"),
          currentResult(field)("count")
        )

      val newMin = Math.min(prevMin, minValue)
      val newMax = Math.max(prevMax, maxValue)
      val newMean = (prevMean * offset + meanValue * count) / (offset + count)

      previousNumStats.update(field, Map("min" -> newMin, "max" -> newMax, "mean" -> newMean))
      previousCount.update("numeric_offset", offset + count.toInt)
    })

    previousNumStats.toMap
  }

  def getDateColStats: Map[String, Map[String, Date]] = {
    val offset: Int = previousCount.getOrElse("date_offset", 0)
    val currentResult: Map[String, Map[String, Any]] = collectionMgr.calculateDateStats(offset)

    currentResult.keys.foreach(field => {
      val (prevMin, prevMax) =
        (
          previousDateStats
            .getOrElse(field, Map("min" -> new java.util.Date(Long.MaxValue)))("min"),
          previousDateStats.getOrElse(field, Map("max" -> new java.util.Date(Long.MinValue)))("max")
        )

      val (minValue: java.util.Date, maxValue: java.util.Date, count: Int) =
        (currentResult(field)("min"), currentResult(field)("max"), currentResult(field)("count"))

      val newMin: java.util.Date = if (minValue.before(prevMin)) minValue else prevMin
      val newMax: java.util.Date = if (maxValue.after(prevMax)) maxValue else prevMax

      previousDateStats.update(field, Map("min" -> newMin, "max" -> newMax))
      previousCount.update("date_offset", offset + count)
    })

    previousDateStats.toMap
  }

  def getCategoricalStats: Map[String, Map[String, Any]] = {
    val offset: Int = previousCount.getOrElse("category_offset", 0)
    val (currentResult: Map[String, Map[String, Int]], ifReachedLimit: Boolean) =
      collectionMgr.calculateCategoricalStats(offset)
    val result: mutable.Map[String, Map[String, Any]] = mutable.Map()

    currentResult.keys.foreach(field => {
      val fieldResult: mutable.Map[String, Any] = mutable.Map()
      val count: Int = currentResult(field).values.sum
      val newCatCounts: mutable.Map[String, Int] = mutable.Map()

      currentResult(field).keys.foreach(category => {
        try {
          val prevCatCounts: Int = previousCatStats(field)(category)
          newCatCounts.put(category, prevCatCounts + currentResult(field)(category))
        } catch {
          case e: NoSuchElementException =>
            newCatCounts.put(category, 0 + currentResult(field)(category))
        }
      })

      previousCatStats.update(field, newCatCounts.toMap)
      previousCount.update("category_offset", offset + count)
      previousReachedLimit = ifReachedLimit || previousReachedLimit

      val top2 = previousCatStats(field).toSeq.sortBy(-_._2).take(2).map(_._1)
      top2.size match {
        case 2 =>
          fieldResult("firstCat") = if (top2.head != null) top2.head else "NULL"
          fieldResult("secondCat") = if (top2(1) != null) top2(1) else "NULL"
          val first =
            (previousCatStats(field)(top2.head).toDouble / (offset + count).toDouble) * 100
          val second =
            (previousCatStats(field)(top2(1)).toDouble / (offset + count).toDouble) * 100
          fieldResult("firstPercent") = first
          fieldResult("secondPercent") = second
          fieldResult("other") = 100 - first - second
          fieldResult("reachedLimit") = if (previousReachedLimit) 1 else 0
        case 1 =>
          fieldResult("firstCat") = if (top2.head != null) top2.head else "NULL"
          fieldResult("secondCat") = ""
          fieldResult("firstPercent") =
            (previousCatStats(field)(top2.head).toDouble / (offset + count).toDouble) * 100
          fieldResult("secondPercent") = 0
          fieldResult("other") = 0
          fieldResult("reachedLimit") = if (previousReachedLimit) 1 else 0
        case _ => None
      }

      if (fieldResult.nonEmpty) result(field) = fieldResult.toMap
    })

    result.toMap
  }
}
