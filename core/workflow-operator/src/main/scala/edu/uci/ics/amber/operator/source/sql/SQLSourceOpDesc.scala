/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package edu.uci.ics.amber.operator.source.sql

import com.fasterxml.jackson.annotation.{JsonProperty, JsonPropertyDescription}
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.kjetland.jackson.jsonSchema.annotations.{JsonSchemaInject, JsonSchemaTitle}
import edu.uci.ics.amber.core.tuple.{Attribute, AttributeType, Schema}
import edu.uci.ics.amber.operator.metadata.annotations.{
  AutofillAttributeName,
  BatchByColumn,
  EnablePresets,
  UIWidget
}
import edu.uci.ics.amber.operator.source.SourceOperatorDescriptor

import java.sql._

abstract class SQLSourceOpDesc extends SourceOperatorDescriptor {

  @EnablePresets
  @JsonProperty(required = true)
  @JsonSchemaTitle("Host")
  var host: String = _

  @EnablePresets
  @JsonProperty(required = true, defaultValue = "default")
  @JsonSchemaTitle("Port")
  @JsonPropertyDescription("A port number or 'default'")
  var port: String = _

  @EnablePresets
  @JsonProperty(required = true)
  @JsonSchemaTitle("Database")
  var database: String = _

  @EnablePresets
  @JsonProperty(required = true)
  @JsonSchemaTitle("Table Name")
  var table: String = _

  @EnablePresets
  @JsonProperty(required = true)
  @JsonSchemaTitle("Username")
  var username: String = _

  @JsonProperty(required = true)
  @JsonSchemaTitle("Password")
  @JsonSchemaInject(json = UIWidget.UIWidgetPassword)
  var password: String = _

  @JsonProperty()
  @JsonSchemaTitle("Limit")
  @JsonPropertyDescription("max output count")
  @JsonDeserialize(contentAs = classOf[java.lang.Long])
  var limit: Option[Long] = None

  @JsonProperty()
  @JsonSchemaTitle("Offset")
  @JsonPropertyDescription("starting point of output")
  @JsonDeserialize(contentAs = classOf[java.lang.Long])
  var offset: Option[Long] = None

  @JsonProperty(defaultValue = "false")
  @JsonSchemaTitle("Keyword Search?")
  @JsonDeserialize(contentAs = classOf[java.lang.Boolean])
  @JsonSchemaInject(json = """{"toggleHidden" : ["keywordSearchByColumn", "keywords"]}""")
  var keywordSearch: Option[Boolean] = Option(false)

  @JsonProperty()
  @JsonSchemaTitle("Keyword Search Column")
  @JsonDeserialize(contentAs = classOf[java.lang.String])
  @AutofillAttributeName
  var keywordSearchByColumn: Option[String] = None

  @JsonProperty()
  @JsonSchemaTitle("Keywords to Search")
  @JsonDeserialize(contentAs = classOf[java.lang.String])
  @JsonSchemaInject(json = UIWidget.UIWidgetTextArea)
  var keywords: Option[String] = None

  @JsonProperty(defaultValue = "false")
  @JsonSchemaTitle("Progressive?")
  @JsonDeserialize(contentAs = classOf[java.lang.Boolean])
  @JsonSchemaInject(json = """{"toggleHidden" : ["batchByColumn", "min", "max", "interval"]}""")
  var progressive: Option[Boolean] = Option(false)

  @JsonProperty()
  @JsonSchemaTitle("Batch by Column")
  @JsonDeserialize(contentAs = classOf[java.lang.String])
  @AutofillAttributeName
  var batchByColumn: Option[String] = None

  @JsonProperty(defaultValue = "auto")
  @JsonSchemaTitle("Min")
  @JsonDeserialize(contentAs = classOf[java.lang.String])
  @BatchByColumn
  var min: Option[String] = None

  @JsonProperty(defaultValue = "auto")
  @JsonSchemaTitle("Max")
  @JsonDeserialize(contentAs = classOf[java.lang.String])
  @BatchByColumn
  var max: Option[String] = None

  @JsonProperty(defaultValue = "1000000000")
  @JsonSchemaTitle("Batch by Interval")
  @BatchByColumn
  var interval = 0L

  override def sourceSchema(): Schema = querySchema

  // needs to define getters for sub classes to override Jackson Annotations
  def getKeywords: Option[String] = keywords

  /**
    * Establish a connection with the database server base on the info provided by the user
    * query the MetaData of the table and generate a Tuple.schema accordingly
    * the "switch" code block shows how SQL data types are mapped to Texera AttributeTypes
    *
    * @return Schema
    */
  private def querySchema: Schema = {
    if (
      this.host == null || this.port == null || this.database == null
      || this.table == null || this.username == null || this.password == null
    ) {
      return null
    }

    updatePort()
    try {
      val attributes = scala.collection.mutable.ListBuffer[Attribute]()
      val connection = establishConn
      connection.setReadOnly(true)
      val databaseMetaData = connection.getMetaData
      val columns = databaseMetaData.getColumns(null, null, this.table, null)
      while (columns.next()) {
        val columnName = columns.getString("COLUMN_NAME")
        val datatype = columns.getInt("DATA_TYPE")

        // Map JDBC data types to AttributeType
        val attributeType = datatype match {
          case Types.TINYINT | // -6 Types.TINYINT
              Types.SMALLINT | // 5 Types.SMALLINT
              Types.INTEGER => // 4 Types.INTEGER
            AttributeType.INTEGER
          case Types.FLOAT | // 6 Types.FLOAT
              Types.REAL | // 7 Types.REAL
              Types.DOUBLE | // 8 Types.DOUBLE
              Types.NUMERIC => // 3 Types.NUMERIC
            AttributeType.DOUBLE
          case Types.BIT | // -7 Types.BIT
              Types.BOOLEAN => // 16 Types.BOOLEAN
            AttributeType.BOOLEAN
          case Types.BINARY => // -2 Types.BINARY
            AttributeType.BINARY
          case Types.DATE | // 91 Types.DATE
              Types.TIME | // 92 Types.TIME
              Types.LONGVARCHAR | // -1 Types.LONGVARCHAR
              Types.CHAR | // 1 Types.CHAR
              Types.VARCHAR | // 12 Types.VARCHAR
              Types.NULL | // 0 Types.NULL
              Types.OTHER => // 1111 Types.OTHER
            AttributeType.STRING
          case Types.BIGINT => // -5 Types.BIGINT
            AttributeType.LONG
          case Types.TIMESTAMP => // 93 Types.TIMESTAMP
            AttributeType.TIMESTAMP
          case _ =>
            throw new RuntimeException(
              this.getClass.getSimpleName + ": unknown data type: " + datatype
            )
        }

        // Add the attribute to the list
        attributes += new Attribute(columnName, attributeType)
      }
      connection.close()
      Schema(attributes.toList)
    } catch {
      case e @ (_: SQLException | _: ClassCastException) =>
        throw new RuntimeException(
          this.getClass.getSimpleName + " failed to connect to the database. " + e.getMessage
        )
    }
  }

  @throws[SQLException]
  protected def establishConn: Connection = null

  protected def updatePort(): Unit
}
