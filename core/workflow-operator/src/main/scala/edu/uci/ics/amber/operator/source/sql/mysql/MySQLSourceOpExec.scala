package edu.uci.ics.amber.operator.source.sql.mysql

import edu.uci.ics.amber.core.tuple.{AttributeType, Schema}
import edu.uci.ics.amber.operator.source.sql.SQLSourceOpExec
import edu.uci.ics.amber.operator.source.sql.mysql.MySQLConnUtil.connect

import java.sql._

class MySQLSourceOpExec private[mysql] (
    host: String,
    port: String,
    database: String,
    table: String,
    username: String,
    password: String,
    limit: Option[Long],
    offset: Option[Long],
    progressive: Option[Boolean],
    batchByColumn: Option[String],
    min: Option[String],
    max: Option[String],
    interval: Long,
    keywordSearch: Boolean,
    keywordSearchByColumn: String,
    keywords: String,
    schemaFunc: () => Schema
) extends SQLSourceOpExec(
      table,
      limit,
      offset,
      progressive,
      batchByColumn,
      min,
      max,
      interval,
      keywordSearch,
      keywordSearchByColumn,
      keywords,
      schemaFunc
    ) {

  val FETCH_TABLE_NAMES_SQL =
    "SELECT table_name FROM information_schema.tables WHERE table_schema = ?;"

  @throws[SQLException]
  override def establishConn(): Connection = connect(host, port, database, username, password)

  @throws[RuntimeException]
  override def addFilterConditions(queryBuilder: StringBuilder): Unit = {
    if (keywordSearch && keywordSearchByColumn != null && keywords != null) {
      val columnType = schema.getAttribute(keywordSearchByColumn).getType

      if (columnType == AttributeType.STRING)
        // in sql prepared statement, column name cannot be inserted using PreparedStatement.setString either
        queryBuilder ++= " AND MATCH(" + keywordSearchByColumn + ") AGAINST (? IN BOOLEAN MODE)"
      else
        throw new RuntimeException("Can't do keyword search on type " + columnType.toString)
    }
  }

  @throws[SQLException]
  override protected def loadTableNames(): Unit = {
    val preparedStatement = connection.prepareStatement(FETCH_TABLE_NAMES_SQL)
    preparedStatement.setString(1, database)
    val resultSet = preparedStatement.executeQuery
    while ({
      resultSet.next
    }) {
      tableNames += resultSet.getString(1)
    }
    resultSet.close()
    preparedStatement.close()
  }
}
