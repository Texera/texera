package edu.uci.ics.texera.workflow.operators.source.mysql

import edu.uci.ics.texera.workflow.common.metadata.{
  OperatorGroupConstants,
  OperatorInfo,
  OutputPort
}
import edu.uci.ics.texera.workflow.operators.source.mysql.MySQLConnUtil.connect
import edu.uci.ics.texera.workflow.operators.source.{SQLSourceOpDesc, SQLSourceOpExecConfig}

import java.sql.{Connection, SQLException}
import java.util.Collections.singletonList
import scala.jdk.CollectionConverters.asScalaBuffer

class MySQLSourceOpDesc extends SQLSourceOpDesc {

  override def operatorExecutor =
    new SQLSourceOpExecConfig(
      this.operatorIdentifier,
      (worker: Any) =>
        new MySQLSourceOpExec(
          this.querySchema,
          host,
          port,
          database,
          table,
          username,
          password,
          limit,
          offset,
          column,
          keywords,
          progressive,
          batchByColumn,
          interval
        )
    )

  override def operatorInfo: OperatorInfo =
    OperatorInfo(
      "MySQL Source",
      "Read data from a MySQL instance",
      OperatorGroupConstants.SOURCE_GROUP,
      List.empty,
      asScalaBuffer(singletonList(OutputPort(""))).toList
    )

  @throws[SQLException]
  override def establishConn: Connection = connect(host, port, database, username, password)

  override def updatePort(): Unit = port = if (port.trim().equals("default")) "3306" else port

}
