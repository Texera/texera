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

package edu.uci.ics.amber.operator.source.sql.asterixdb

import com.fasterxml.jackson.annotation.{
  JsonIgnoreProperties,
  JsonProperty,
  JsonPropertyDescription
}
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.kjetland.jackson.jsonSchema.annotations.{JsonSchemaInject, JsonSchemaTitle}
import edu.uci.ics.amber.core.executor.OpExecWithClassName
import edu.uci.ics.amber.core.tuple.{Attribute, AttributeType, Schema}
import edu.uci.ics.amber.core.workflow.{PhysicalOp, SchemaPropagationFunc}
import edu.uci.ics.amber.core.virtualidentity.{ExecutionIdentity, WorkflowIdentity}
import edu.uci.ics.amber.core.workflow.OutputPort
import edu.uci.ics.amber.operator.filter.FilterPredicate
import edu.uci.ics.amber.operator.metadata.annotations.{
  AutofillAttributeName,
  AutofillAttributeNameList,
  UIWidget
}
import edu.uci.ics.amber.operator.metadata.{OperatorGroupConstants, OperatorInfo}
import edu.uci.ics.amber.operator.source.sql.SQLSourceOpDesc
import edu.uci.ics.amber.operator.source.sql.asterixdb.AsterixDBConnUtil.{
  fetchDataTypeFields,
  queryAsterixDB
}
import edu.uci.ics.amber.util.JSONUtils.objectMapper
import kong.unirest.json.JSONObject

@JsonIgnoreProperties(value = Array("username", "password"))
class AsterixDBSourceOpDesc extends SQLSourceOpDesc {

  @JsonProperty(defaultValue = "false")
  @JsonSchemaTitle("Geo Search?")
  @JsonDeserialize(contentAs = classOf[java.lang.Boolean])
  @JsonSchemaInject(json = """{"toggleHidden" : ["geoSearchByColumns", "geoSearchBoundingBox"]}""")
  var geoSearch: Option[Boolean] = Option(false)

  @JsonProperty()
  @JsonSchemaTitle("Geo Search By Columns")
  @JsonPropertyDescription(
    "column(s) to check if any of them is in the bounding box below"
  )
  @AutofillAttributeNameList
  // TODO: set it to one column in the future since it implicitly adds OR semantics
  var geoSearchByColumns: List[String] = List.empty

  @JsonProperty()
  @JsonSchemaTitle("Geo Search Bounding Box")
  @JsonPropertyDescription(
    "at least 2 entries should be provided to form a bounding box. format of each entry: long, lat"
  )
  var geoSearchBoundingBox: List[String] = List.empty

  @JsonProperty(defaultValue = "false")
  @JsonSchemaTitle("Regex Search?")
  @JsonDeserialize(contentAs = classOf[java.lang.Boolean])
  @JsonSchemaInject(json = """{"toggleHidden" : ["regexSearchByColumn", "regex"]}""")
  var regexSearch: Option[Boolean] = Option(false)

  @JsonProperty()
  @JsonSchemaTitle("Regex Search By Column")
  @JsonDeserialize(contentAs = classOf[java.lang.String])
  @AutofillAttributeName
  var regexSearchByColumn: Option[String] = None

  @JsonProperty()
  @JsonSchemaTitle("Regex to Search")
  @JsonDeserialize(contentAs = classOf[java.lang.String])
  @JsonSchemaInject(json = UIWidget.UIWidgetTextArea)
  var regex: Option[String] = None

  @JsonProperty(defaultValue = "false")
  @JsonSchemaTitle("Filter Condition?")
  @JsonDeserialize(contentAs = classOf[java.lang.Boolean])
  @JsonSchemaInject(json = """{"toggleHidden" : ["predicates"]}""")
  var filterCondition: Option[Boolean] = Option(false)

  @JsonProperty(value = "predicates", required = false)
  @JsonPropertyDescription("multiple predicates in OR")
  var filterPredicates: List[FilterPredicate] = List()

  @JsonProperty()
  @JsonSchemaTitle("Keywords to Search")
  @JsonDeserialize(contentAs = classOf[java.lang.String])
  @JsonSchemaInject(json = UIWidget.UIWidgetTextArea)
  @JsonPropertyDescription(
    "\"['hello', 'world'], {'mode':'any'}\" OR \"['hello', 'world'], {'mode':'all'}\""
  )
  override def getKeywords: Option[String] = super.getKeywords

  override def getPhysicalOp(
      workflowId: WorkflowIdentity,
      executionId: ExecutionIdentity
  ): PhysicalOp =
    PhysicalOp
      .sourcePhysicalOp(
        workflowId,
        executionId,
        this.operatorIdentifier,
        OpExecWithClassName(
          "edu.uci.ics.amber.operator.source.sql.asterixdb.AsterixDBSourceOpExec",
          objectMapper.writeValueAsString(this)
        )
      )
      .withInputPorts(operatorInfo.inputPorts)
      .withOutputPorts(operatorInfo.outputPorts)
      .withPropagateSchema(
        SchemaPropagationFunc(_ => Map(operatorInfo.outputPorts.head.id -> sourceSchema()))
      )

  override def operatorInfo: OperatorInfo =
    OperatorInfo(
      "AsterixDB Source",
      "Read data from a AsterixDB instance",
      OperatorGroupConstants.DATABASE_GROUP,
      inputPorts = List.empty,
      outputPorts = List(OutputPort())
    )

  override def updatePort(): Unit = port = if (port.trim().equals("default")) "19002" else port

  override def sourceSchema(): Schema = {
    if (this.host == null || this.port == null || this.database == null || this.table == null) {
      return null
    }

    updatePort()

    // Query dataset's Datatype from Metadata.`Datatype`
    val datasetDataType = queryAsterixDB(
      host,
      port,
      s"SELECT DatatypeName FROM Metadata.`Dataset` ds where ds.`DatasetName`='$table';",
      format = "JSON"
    ).get.next().asInstanceOf[JSONObject].getString("DatatypeName")

    // Query field types from Metadata.`Datatype`
    val fields = fetchDataTypeFields(datasetDataType, "", host, port)

    // Collect attributes by sorting field names and mapping them to Attribute instances
    val attributes = fields.keys.toList.sorted.map { key =>
      new Attribute(key, attributeTypeFromAsterixDBType(fields(key)))
    }
    Schema(attributes)
  }

  private def attributeTypeFromAsterixDBType(inputType: String): AttributeType =
    inputType match {
      case "boolean"           => AttributeType.BOOLEAN
      case "int32"             => AttributeType.INTEGER
      case "int64"             => AttributeType.LONG
      case "float" | "double"  => AttributeType.DOUBLE
      case "datetime" | "date" => AttributeType.TIMESTAMP
      case "string" | _        => AttributeType.STRING
    }
}
