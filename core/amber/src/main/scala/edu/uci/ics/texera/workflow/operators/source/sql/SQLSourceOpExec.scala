package edu.uci.ics.texera.workflow.operators.source.sql

import edu.uci.ics.amber.engine.common.executor.SourceOperatorExecutor
import edu.uci.ics.amber.engine.common.model.tuple.AttributeTypeUtils.{parseField, parseTimestamp}
import edu.uci.ics.amber.engine.common.model.tuple.{
  Attribute,
  AttributeType,
  Schema,
  Tuple,
  TupleLike
}

import java.sql._
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks.{break, breakable}

abstract class SQLSourceOpExec(
    // source configs
    table: String,
    var curLimit: Option[Long],
    var curOffset: Option[Long],
    // progressiveness related
    progressive: Option[Boolean],
    batchByColumn: Option[String],
    min: Option[String],
    max: Option[String],
    interval: Long,
    // filter conditions:
    keywordSearch: Boolean,
    keywordSearchByColumn: String,
    keywords: String,
    schemaFunc: () => Schema
) extends SourceOperatorExecutor {

  // connection and query related
  var schema: Schema = _
  val tableNames: ArrayBuffer[String] = ArrayBuffer()
  var batchByAttribute: Option[Attribute] = None
  var connection: Connection = _
  private var curQuery: Option[PreparedStatement] = None
  private var curResultSet: Option[ResultSet] = None
  private var curLowerBound: Number = _
  private var upperBound: Number = _
  var cachedTuple: Option[Tuple] = None
  private var querySent: Boolean = false

  /**
    * A generator of a Tuple, which converted from a SQL row
    * @return Iterator[TupleLike]
    */
  override def produceTuple(): Iterator[TupleLike] = {
    new Iterator[TupleLike]() {
      override def hasNext: Boolean = {
        cachedTuple match {
          // if existing Tuple in cache, means there exist next Tuple.
          case Some(_) => true
          case None    =>
            // cache the next Tuple
            cachedTuple = Option(next())
            cachedTuple.isDefined
        }

      }

      /**
        * Fetch the next row from resultSet, parse it into Tuple and return.
        * - If resultSet is exhausted, send the next query until no more queries are available.
        * - If no more queries, return null.
        *
        * @throws SQLException all possible exceptions from JDBC
        * @return Tuple
        */
      @throws[SQLException]
      override def next(): Tuple = {

        // if has the next Tuple in cache, return it and clear the cache
        cachedTuple.foreach(tuple => {
          cachedTuple = None
          return tuple
        })

        // otherwise, send query to fetch for the next Tuple

        while (true) {
          breakable {

            curResultSet match {
              case Some(resultSet) =>
                if (resultSet.next()) {

                  // manually skip until the offset position in order to adapt to progressive batches
                  curOffset.foreach(offset => {
                    if (offset > 0) {
                      curOffset = Option(offset - 1)
                      break()
                    }
                  })

                  // construct Tuple from the next result.
                  val tuple = buildTupleFromRow

                  if (tuple == null)
                    break()

                  // update the limit in order to adapt to progressive batches
                  curLimit.foreach(limit => {
                    if (limit > 0) {
                      curLimit = Option(limit - 1)
                    }
                  })
                  return tuple
                } else {
                  // close the current resultSet and query
                  curResultSet.foreach(resultSet => resultSet.close())
                  curQuery.foreach(query => query.close())
                  curResultSet = None
                  curQuery = None
                  break()
                }
              case None =>
                curQuery = getNextQuery
                curQuery match {
                  case Some(query) =>
                    curResultSet = Option(query.executeQuery)
                    break()
                  case None =>
                    curResultSet = None
                    return null
                }
            }

          }
        }
        null
      }

    }
  }

  /**
    * Establish a connection to the database server and load statistics for constructing future queries.
    * - tableNames, to check if the input tableName exists on the database server, to prevent SQL injection.
    * - batchColumnBoundaries, to be used to split mini queries, if progressive mode is enabled.
    *
    * @throws SQLException all possible exceptions from JDBC
    */
  @throws[SQLException]
  override def open(): Unit = {
    schema = schemaFunc()
    batchByAttribute =
      if (progressive.getOrElse(false)) Option(schema.getAttribute(batchByColumn.get)) else None
    connection = establishConn()

    // load user table names from the given database
    loadTableNames()
    // validates the input table name
    if (!tableNames.contains(table))
      throw new RuntimeException("Can't find the given table `" + table + "`.")
    // load for batch column value boundaries used to split mini queries
    if (progressive.getOrElse(false)) initBatchColumnBoundaries()
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
    * Build a Tuple from a row of curResultSet
    *
    * @return the new Tuple
    * @throws SQLException all possible exceptions from JDBC
    */
  @throws[SQLException]
  protected def buildTupleFromRow: Tuple = {
    val tupleBuilder = Tuple.builder(schema)

    for (attr <- schema.getAttributes) {

      breakable {
        val columnName = attr.getName
        val columnType = attr.getType
        val value = curResultSet.get.getObject(columnName)

        if (value == null) {
          // add the field as null
          tupleBuilder.add(attr, null)
          break()
        }

        // otherwise, transform the type of the value
        tupleBuilder.add(attr, parseField(value, columnType))

      }
    }
    tupleBuilder.build()
  }

  /**
    * Checks if there is a next query.
    * - This is mostly used for progressive mode: if the lower bound is
    * not yet reached upper bound, it will have next query.
    * - If it is not progressive mode, this method will return false when
    * invoked the second time. Which means there is only one query.
    * @throws IllegalArgumentException if the given batchByAttribute's type is
    *                          not supported to be incremental.
    * @return A boolean value whether there exists the next query or not.
    */
  @throws[IllegalArgumentException]
  protected def hasNextQuery: Boolean = {
    batchByAttribute match {
      case Some(attribute) =>
        attribute.getType match {
          case AttributeType.INTEGER | AttributeType.LONG | AttributeType.TIMESTAMP =>
            curLowerBound.longValue <= upperBound.longValue
          case AttributeType.DOUBLE =>
            curLowerBound.doubleValue <= upperBound.doubleValue
          case AttributeType.STRING | AttributeType.ANY | AttributeType.BOOLEAN | _ =>
            throw new IllegalArgumentException("Unexpected type: " + attribute.getType)
        }
      case None =>
        val hasNextQuery = !querySent
        querySent = true
        hasNextQuery
    }
  }

  protected def terminateSQL(queryBuilder: StringBuilder): Unit = {
    queryBuilder ++= ";"
  }

  protected def addOffset(queryBuilder: StringBuilder): Unit = {
    queryBuilder ++= " OFFSET ?"
  }

  protected def addLimit(queryBuilder: StringBuilder): Unit = {
    queryBuilder ++= " LIMIT ?"
  }

  protected def addBaseSelect(queryBuilder: StringBuilder): Unit = {
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
    * @throws IllegalArgumentException if the given batchByAttribute's type is
    *                          not supported to be incremental.
    */
  @throws[IllegalArgumentException]
  protected def addBatchSlidingWindow(queryBuilder: StringBuilder): Unit = {
    var nextLowerBound: Number = null
    var isLastBatch = false

    batchByAttribute match {
      case Some(attribute) =>
        attribute.getType match {
          case AttributeType.INTEGER | AttributeType.LONG | AttributeType.TIMESTAMP =>
            nextLowerBound = curLowerBound.longValue + interval
            isLastBatch = nextLowerBound.longValue >= upperBound.longValue
          case AttributeType.DOUBLE =>
            nextLowerBound = curLowerBound.doubleValue + interval
            isLastBatch = nextLowerBound.doubleValue >= upperBound.doubleValue
          case AttributeType.BOOLEAN | AttributeType.STRING | AttributeType.ANY | _ =>
            throw new IllegalArgumentException("Unexpected type: " + attribute.getType)
        }
        queryBuilder ++= " AND " + attribute.getName +
          " >= " + batchAttributeToString(curLowerBound) +
          " AND " + attribute.getName +
          (if (isLastBatch)
             " <= " + batchAttributeToString(upperBound)
           else
             " < " + batchAttributeToString(nextLowerBound))
      case None =>
        throw new IllegalArgumentException(
          "no valid batchByColumn to iterate: " + batchByColumn.getOrElse("")
        )
    }
    curLowerBound = nextLowerBound
  }

  /**
    * Convert the Number value to a String to be concatenate to SQL.
    *
    * @param value a Number, contains the value to be converted.
    * @throws IllegalArgumentException when the batchByAttribute is missing or the type is unexpected
    * @return a String of that value
    */
  @throws[IllegalArgumentException]
  protected def batchAttributeToString(value: Number): String = {
    batchByAttribute match {
      case Some(attribute) =>
        attribute.getType match {
          case AttributeType.LONG | AttributeType.INTEGER | AttributeType.DOUBLE =>
            String.valueOf(value)
          case AttributeType.TIMESTAMP =>
            "'" + new Timestamp(value.longValue).toString + "'"
          case AttributeType.BOOLEAN | AttributeType.STRING | AttributeType.ANY | _ =>
            throw new IllegalArgumentException("Unexpected type: " + attribute.getType)
        }
      case None =>
        throw new IllegalArgumentException(
          "No valid batchByColumn to iterate: " + batchByColumn.getOrElse("")
        )
    }

  }

  /**
    * Fetch for a numeric value of the boundary of the batchByColumn.
    * @param side either "MAX" or "MIN" for boundary
    * @throws IllegalArgumentException if the batchByAttribute type is unexpected
    * @return a numeric value, could be Int, Long or Double
    */
  @throws[IllegalArgumentException]
  protected def fetchBatchByBoundary(side: String): Number = {
    batchByAttribute match {
      case Some(attribute) =>
        var result: Number = null
        val preparedStatement = connection.prepareStatement(
          "SELECT " + side + "(" + attribute.getName + ") FROM " + table + ";"
        )
        val resultSet = preparedStatement.executeQuery
        resultSet.next
        schema.getAttribute(attribute.getName).getType match {
          case AttributeType.INTEGER =>
            result = resultSet.getInt(1)
          case AttributeType.LONG =>
            result = resultSet.getLong(1)
          case AttributeType.TIMESTAMP =>
            result = resultSet.getTimestamp(1).getTime
          case AttributeType.DOUBLE =>
            result = resultSet.getDouble(1)
          case AttributeType.BOOLEAN | AttributeType.STRING | AttributeType.ANY | _ =>
            throw new IllegalStateException("Unexpected value: " + attribute.getType)
        }
        resultSet.close()
        preparedStatement.close()
        result

      case None => 0
    }
  }

  /**
    * Establishes the connection to database.
    * @throws SQLException all possible exceptions from JDBC
    * @return a SQL connection over JDBC
    */
  @throws[SQLException]
  protected def establishConn(): Connection = null

  /**
    * Fetch all table names from the given database. This is used to
    * check the input table name to prevent from SQL injection.
    * @throws SQLException all possible exceptions from JDBC
    */
  @throws[SQLException]
  protected def loadTableNames(): Unit

  protected def addFilterConditions(queryBuilder: StringBuilder): Unit

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
    * @throws IllegalArgumentException if the given batchByAttribute's type is
    *                          not supported to be incremental.
    * @return string of sql query
    */
  @throws[IllegalArgumentException]
  protected def generateSqlQuery: Option[String] = {
    // in sql prepared statement, table name cannot be inserted using PreparedStatement.setString
    // so it has to be inserted here during sql query generation
    // table has to be verified to be existing in the given schema.
    val queryBuilder = new StringBuilder

    // Add base SELECT * with true condition
    // TODO: add more selection conditions, including alias
    addBaseSelect(queryBuilder)

    // add filter conditions if applicable
    addFilterConditions(queryBuilder)

    // add sliding window if progressive mode is enabled
    if (progressive.getOrElse(false) && batchByColumn.isDefined && interval > 0L)
      addBatchSlidingWindow(queryBuilder)

    // add limit if provided
    if (curLimit.isDefined) {
      if (curLimit.get > 0) addLimit(queryBuilder)
      else
        // there should be no more queries as limit is equal or less than 0
        return None
    }

    // add fixed offset if not progressive
    if (!progressive.getOrElse(false) && curOffset.isDefined) addOffset(queryBuilder)

    // end
    terminateSQL(queryBuilder)

    Option(queryBuilder.result())
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
          if (keywordSearch && keywordSearchByColumn != null && keywords != null) {
            preparedStatement.setString(curIndex, keywords)
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
          if (!progressive.getOrElse(false))
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
    * Load the lower bound and upper bound of the batchByColumn. Those
    * bounds will be used in progressive mode to determine mini-queries.
    *
    * @throws SQLException all possible exceptions from JDBC
    * @throws IllegalArgumentException if the batchByAttribute is missing or the type is unexpected
    */
  @throws[SQLException]
  @throws[IllegalArgumentException]
  private def initBatchColumnBoundaries(): Unit = {
    // TODO: add interval
    if (batchByAttribute.isDefined && min.isDefined && max.isDefined) {

      if (min.get.equalsIgnoreCase("auto")) curLowerBound = fetchBatchByBoundary("MIN")
      else
        batchByAttribute.get.getType match {
          case AttributeType.TIMESTAMP => curLowerBound = parseTimestamp(min.get).getTime
          case AttributeType.LONG      => curLowerBound = min.get.toLong
          case _ =>
            throw new IllegalArgumentException(s"Unsupported type ${batchByAttribute.get.getType}")
        }

      if (max.get.equalsIgnoreCase("auto")) upperBound = fetchBatchByBoundary("MAX")
      else
        batchByAttribute.get.getType match {
          case AttributeType.TIMESTAMP => upperBound = parseTimestamp(max.get).getTime
          case AttributeType.LONG      => upperBound = max.get.toLong
          case _ =>
            throw new IllegalArgumentException(s"Unsupported type ${batchByAttribute.get.getType}")
        }
    } else {
      throw new IllegalArgumentException(
        s"Missing required progressive configuration, $batchByAttribute, $min or $max."
      )
    }
  }
}