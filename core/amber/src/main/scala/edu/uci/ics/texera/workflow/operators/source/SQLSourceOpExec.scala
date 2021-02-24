package edu.uci.ics.texera.workflow.operators.source

import edu.uci.ics.texera.workflow.common.operators.source.SourceOperatorExecutor
import edu.uci.ics.texera.workflow.common.tuple.Tuple
import edu.uci.ics.texera.workflow.common.tuple.schema.AttributeType._
import edu.uci.ics.texera.workflow.common.tuple.schema.{Attribute, Schema}

import java.sql._
import scala.collection.Iterator
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._
import scala.util.control.Breaks.{break, breakable}

abstract class SQLSourceOpExec(
    // source configs
    schema: Schema,
    table: String,
    var curLimit: Option[Long],
    var curOffset: Option[Long],
    column: Option[String],
    keywords: Option[String],
    // progressiveness related
    progressive: Boolean,
    batchByColumn: Option[String],
    interval: Long
) extends SourceOperatorExecutor {

  // connection and query related
  val tableNames: ArrayBuffer[String] = ArrayBuffer()
  val batchByAttribute: Option[Attribute] =
    if (progressive) Option(schema.getAttribute(batchByColumn.get)) else None
  var connection: Connection = _
  var curQuery: Option[PreparedStatement] = None
  var curResultSet: Option[ResultSet] = None
  var curLowerBound: Number = _
  var upperBound: Number = _
  var cachedTuple: Option[Tuple] = None
  var querySent: Boolean = false

  /**
    * A generator of a Texera.Tuple, which converted from a SQL row
    * @return Iterator[Tuple]
    */
  override def produceTexeraTuple(): Iterator[Tuple] = {
    new Iterator[Tuple]() {
      override def hasNext: Boolean = {
        cachedTuple match {
          // if existing Tuple in cache, means there exist next Tuple.
          case Some(_) => true
          case None    =>
            // cache the next Tuple
            cachedTuple = Option(next)
            cachedTuple.isDefined
        }

      }

      /**
        * Fetch the next row from resultSet, parse it into Texera.Tuple and return.
        * - If resultSet is exhausted, send the next query until no more queries are available.
        * - If no more queries, return null.
        *
        * @return Texera.Tuple
        */
      @throws[SQLException]
      override def next: Tuple = {

        // if has the next Tuple in cache, return it and clear the cache
        cachedTuple.foreach(tuple => {
          cachedTuple = None
          return tuple
        })

        // otherwise, send query to fetch for the next Tuple

        curResultSet match {
          case Some(resultSet) =>
            if (resultSet.next()) {

              // manually skip until the offset position in order to adapt to progressive batches
              curOffset.fold()(offset => {
                if (offset > 0) {
                  curOffset = Option(offset - 1)
                  return next
                }
              })

              // construct Texera.Tuple from the next result.
              val tuple = buildTupleFromRow

              // update the limit in order to adapt to progressive batches
              curLimit.fold()(limit => {
                if (limit > 0) {
                  curLimit = Option(limit - 1)
                }
              })
              tuple
            } else {
              // close the current resultSet and query
              curResultSet.foreach(resultSet => resultSet.close())
              curQuery.foreach(query => query.close())
              curResultSet = None
              curQuery = None
              next
            }
          case None =>
            curQuery = getNextQuery
            curQuery match {
              case Some(query) =>
                curResultSet = Option(query.executeQuery)
                next
              case None =>
                curResultSet = None
                null
            }
        }

      }
    }
  }

  /**
    * Build a Texera.Tuple from a row of curResultSet
    *
    * @return the new Texera.Tuple
    */
  @throws[SQLException]
  private def buildTupleFromRow: Tuple = {
    val tupleBuilder = Tuple.newBuilder

    for (attr <- schema.getAttributes.asScala) {
      breakable {
        val columnName = attr.getName
        val columnType = attr.getType
        val value = curResultSet.get.getString(columnName)

        if (value == null) {
          // add the field as null
          tupleBuilder.add(attr, null)
          break
        }

        // otherwise, transform the type of the value
        columnType match {
          case INTEGER =>
            tupleBuilder.add(attr, value.toInt)
          case LONG =>
            tupleBuilder.add(attr, value.toLong)
          case DOUBLE =>
            tupleBuilder.add(attr, value.toDouble)
          case STRING =>
            tupleBuilder.add(attr, value)
          case BOOLEAN =>
            tupleBuilder.add(attr, !(value == "0"))
          case TIMESTAMP =>
            tupleBuilder.add(attr, Timestamp.valueOf(value))
          case ANY | _ =>
            throw new RuntimeException("Unhandled attribute type: " + columnType)
        }
      }
    }
    tupleBuilder.build
  }

  /**
    * Get the next query.
    * - If progressive mode is enabled, this method will be invoked
    * many times, each yielding the next mini query.
    * - If progressive mode is not enabled, this method will be invoked
    * only once, returning the one giant query.
    * @throws SQLException all possible exceptions from JDBC
    * @return a PreparedStatement to be filled with values.
    */
  @throws[SQLException]
  private def getNextQuery: Option[PreparedStatement] = {
    if (hasNextQuery) {
      val nextQuery = generateSqlQuery
      nextQuery match {
        case Some(query) =>
          val preparedStatement = connection.prepareStatement(query)
          var curIndex = 1

          // fill up the keywords
          if (column.isDefined && keywords.isDefined) {
            preparedStatement.setString(curIndex, keywords.get)
            curIndex += 1
          }

          // fill up limit
          curLimit match {
            case Some(limit) =>
              if (limit > 0) preparedStatement.setLong(curIndex, limit)
              curIndex += 1
            case None =>
          }

          // fill up offset if progressive mode is not enabled
          if (!progressive)
            curOffset match {
              case Some(offset) =>
                preparedStatement.setLong(curIndex, offset)
              case None =>
            }

          Option(preparedStatement)
        case None => None
      }
    } else None
  }

  /**
    * Checks if there is a next query.
    * - This is mostly used for progressive mode: if the lower bound is
    * not yet reached upper bound, it will have next query.
    * - If it is not progressive mode, this method will return false when
    * invoked the second time. Which means there is only one query.
    * @throws RuntimeException if the given batchByAttribute's type is
    *                          not supported to be incremental.
    * @return A boolean value whether there exists the next query or not.
    */
  @throws[RuntimeException]
  private def hasNextQuery: Boolean = {
    batchByAttribute match {
      case Some(attribute) =>
        attribute.getType match {
          case INTEGER | LONG | TIMESTAMP =>
            curLowerBound.longValue <= upperBound.longValue
          case DOUBLE =>
            curLowerBound.doubleValue <= upperBound.doubleValue
          case STRING | ANY | BOOLEAN | _ =>
            throw new RuntimeException("Unexpected type: " + attribute.getType)
        }
      case None =>
        val hasNextQuery = !querySent
        querySent = true
        hasNextQuery
    }
  }

  /**
    * generate sql query string using the info provided by user. One of following
    * select * from TableName where 1 = 1 AND MATCH (ColumnName) AGAINST ( ? IN BOOLEAN MODE) LIMIT ?;
    * select * from TableName where 1 = 1 AND MATCH (ColumnName) AGAINST ( ? IN BOOLEAN MODE);
    * select * from TableName where 1 = 1 LIMIT ? ;
    * select * from TableName where 1 = 1;
    *
    * with an optional appropriate batchByColumn sliding window,
    * e.g. create_at >= '2017-01-14 03:47:59.0' AND create_at < '2017-01-15 03:47:59.0'
    *
    * Or a fixed offset [OFFSET ?] to be added if not progressive.
    *
    * @throws RuntimeException if the given batchByAttribute's type is
    *                          not supported to be incremental.
    * @return string of sql query
    */
  @throws[RuntimeException]
  private def generateSqlQuery: Option[String] = {
    // in sql prepared statement, table name cannot be inserted using PreparedStatement.setString
    // so it has to be inserted here during sql query generation
    // table has to be verified to be existing in the given schema.
    val queryBuilder = new StringBuilder

    // Add base SELECT * with true condition
    // TODO: add more selection conditions, including alias
    addBaseSelect(queryBuilder)

    // add keyword search if applicable
    if (column.isDefined && keywords.isDefined)
      addKeywordSearch(queryBuilder)

    // add sliding window if progressive mode is enabled
    if (progressive) addBatchSlidingWindow(queryBuilder)

    // add limit if provided
    if (curLimit.isDefined) {
      if (curLimit.get > 0) addLimit(queryBuilder)
      else
        // there should be no more queries as limit is equal or less than 0
        return None
    }

    // add fixed offset if not progressive
    if (!progressive && curOffset.isDefined) addOffset(queryBuilder)

    // end
    terminateSQL(queryBuilder)

    Option(queryBuilder.result())
  }

  private def terminateSQL(queryBuilder: StringBuilder): Unit = {
    queryBuilder ++= ";"
  }

  private def addOffset(queryBuilder: StringBuilder): Unit = {
    queryBuilder ++= " OFFSET ?"
  }

  private def addLimit(queryBuilder: StringBuilder): Unit = {
    queryBuilder ++= " LIMIT ?"
  }

  private def addBaseSelect(queryBuilder: StringBuilder): Unit = {
    queryBuilder ++= "\n" + "SELECT * FROM " + table + " where 1 = 1"
  }

  /**
    * Add sliding window SQL statement, based on the batchByAttribute.
    * Supported types:
    *   - Long, Int: simple incremental by interval, which is a Long value.
    *   - Timestamp: convert to Long type, same as Long.
    *   - Double: incremental by interval, faction part stays the same.
    *
    * There will be a lower bound and upper bound for each sliding window.
    *
    * The last window would be [lower, upper], while the other windows will
    * be [lower, nextLower)
    *
    * @param queryBuilder the target query builder
    * @throws RuntimeException if the given batchByAttribute's type is
    *                          not supported to be incremental.
    */
  @throws[RuntimeException]
  private def addBatchSlidingWindow(queryBuilder: StringBuilder): Unit = {
    var nextLowerBound: Number = null
    var isLastBatch = false

    batchByAttribute match {
      case Some(attribute) =>
        attribute.getType match {
          case INTEGER | LONG | TIMESTAMP =>
            nextLowerBound = curLowerBound.longValue + interval
            isLastBatch = nextLowerBound.longValue >= upperBound.longValue
          case DOUBLE =>
            nextLowerBound = curLowerBound.doubleValue + interval
            isLastBatch = nextLowerBound.doubleValue >= upperBound.doubleValue
          case BOOLEAN | STRING | ANY | _ =>
            throw new RuntimeException("Unexpected type: " + attribute.getType)
        }
        queryBuilder ++= " AND " + attribute.getName + " >= '" + batchAttributeToString(
          curLowerBound
        ) +
          "'" + " AND " + attribute.getName +
          (if (isLastBatch)
             " <= '" + batchAttributeToString(upperBound)
           else
             " < '" + batchAttributeToString(nextLowerBound)) +
          "'"
      case None =>
        throw new RuntimeException(
          "no valid batchByColumn to iterate: " + batchByColumn.getOrElse("")
        )
    }
    curLowerBound = nextLowerBound
  }

  /**
    * Convert the Number value to a String to be concatenate to SQL.
    *
    * @param value a Number, contains the value to be converted.
    * @throws RuntimeException if the given batchByAttribute's type is not supported.
    * @return a String of that value
    */
  @throws[RuntimeException]
  private def batchAttributeToString(value: Number): String = {
    batchByAttribute match {
      case Some(attribute) =>
        attribute.getType match {
          case LONG | INTEGER | DOUBLE =>
            String.valueOf(value)
          case TIMESTAMP =>
            new Timestamp(value.longValue).toString
          case BOOLEAN | STRING | ANY | _ =>
            throw new RuntimeException("Unexpected type: " + attribute.getType)
        }
      case None =>
        throw new RuntimeException(
          "No valid batchByColumn to iterate: " + batchByColumn.getOrElse("")
        )
    }

  }

  /**
    * Establish a connection to the database server and load statistics for constructing future queries.
    * - tableNames, to check if the input tableName exists on the database server, to prevent SQL injection.
    * - batchColumnBoundaries, to be used to split mini queries, if progressive mode is enabled.
    *
    * @throws SQLException all possible exceptions from JDBC
    * @throws RuntimeException if the provided table does not exist.
    */
  @throws[SQLException]
  @throws[RuntimeException]
  override def open(): Unit = {
    connection = establishConn()

    // load user table names from the given database
    loadTableNames()
    // validates the input table name
    if (!tableNames.contains(table))
      throw new RuntimeException("Can't find the given table `" + table + "`.")
    // load for batch column value boundaries used to split mini queries
    if (progressive) loadBatchColumnBoundaries()
  }

  /**
    * Load the lower bound and upper bound of the batchByColumn. Those
    * bounds will be used in progressive mode to determine mini-queries.
    *
    * @throws SQLException all possible exceptions from JDBC
    */
  @throws[SQLException]
  private def loadBatchColumnBoundaries(): Unit = {
    batchByAttribute match {
      case Some(attribute) =>
        if (attribute.getName.nonEmpty) {
          upperBound = getBatchByBoundary("MAX").getOrElse(0)
          curLowerBound = getBatchByBoundary("MIN").getOrElse(0)
        }
      case None =>
    }
  }

  /**
    * Fetch for a numeric value of the boundary of the batchByColumn.
    * @param side either "MAX" or "MIN" for boundary
    * @throws SQLException all possible exceptions from JDBC
    * @return a numeric value, could be Int, Long or Double
    */
  @throws[SQLException]
  private def getBatchByBoundary(side: String): Option[Number] = {
    batchByAttribute match {
      case Some(attribute) =>
        var result: Number = null
        val preparedStatement = connection.prepareStatement(
          "SELECT " + side + "(" + attribute.getName + ") FROM " + table + ";"
        )
        val resultSet = preparedStatement.executeQuery
        resultSet.next
        schema.getAttribute(attribute.getName).getType match {
          case INTEGER =>
            result = resultSet.getInt(1)

          case LONG =>
            result = resultSet.getLong(1)

          case TIMESTAMP =>
            result = resultSet.getTimestamp(1).getTime

          case DOUBLE =>
            result = resultSet.getDouble(1)

          case BOOLEAN =>
          case STRING  =>
          case ANY     =>
          case _ =>
            throw new IllegalStateException("Unexpected value: " + attribute.getType)
        }
        resultSet.close()
        preparedStatement.close()
        Option(result)
      case None => None
    }
  }

  /**
    * close resultSet, preparedStatement and connection
    * @throws SQLException all possible exceptions from JDBC
    */
  @throws[SQLException]
  override def close(): Unit = {

    curResultSet.foreach(resultSet => resultSet.close())

    curQuery.foreach(query => query.close())

    if (connection != null) connection.close()
  }

  /**
    * Establishes the connection to database.
    * @throws SQLException all possible exceptions from JDBC
    * @return a SQL connection over JDBC
    */
  @throws[SQLException]
  protected def establishConn(): Connection

  /**
    * Fetch all table names from the given database. This is used to
    * check the input table name to prevent from SQL injection.
    * @throws SQLException all possible exceptions from JDBC
    */
  @throws[SQLException]
  protected def loadTableNames(): Unit

  @throws[RuntimeException]
  protected def addKeywordSearch(queryBuilder: StringBuilder): Unit
}
