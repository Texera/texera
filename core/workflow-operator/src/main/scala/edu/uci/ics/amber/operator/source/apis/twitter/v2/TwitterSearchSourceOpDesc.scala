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

package edu.uci.ics.amber.operator.source.apis.twitter.v2

import com.fasterxml.jackson.annotation.{JsonIgnore, JsonProperty}
import com.kjetland.jackson.jsonSchema.annotations.{
  JsonSchemaDescription,
  JsonSchemaInject,
  JsonSchemaTitle
}
import edu.uci.ics.amber.core.executor.OpExecWithClassName
import edu.uci.ics.amber.core.tuple.{AttributeType, Schema}
import edu.uci.ics.amber.core.workflow.{PhysicalOp, SchemaPropagationFunc}
import edu.uci.ics.amber.operator.metadata.annotations.UIWidget
import edu.uci.ics.amber.operator.source.apis.twitter.TwitterSourceOpDesc
import edu.uci.ics.amber.util.JSONUtils.objectMapper
import edu.uci.ics.amber.core.virtualidentity.{ExecutionIdentity, WorkflowIdentity}

class TwitterSearchSourceOpDesc extends TwitterSourceOpDesc {

  @JsonIgnore
  override val APIName: Option[String] = Some("Search")

  @JsonProperty(required = true)
  @JsonSchemaTitle("Search Query")
  @JsonSchemaDescription("Up to 1024 characters (Limited by Twitter)")
  @JsonSchemaInject(json = UIWidget.UIWidgetTextArea)
  var searchQuery: String = _

  @JsonProperty(required = true, defaultValue = "100")
  @JsonSchemaTitle("Limit")
  @JsonSchemaDescription("Maximum number of tweets to retrieve")
  var limit: Int = _

  override def getPhysicalOp(
      workflowId: WorkflowIdentity,
      executionId: ExecutionIdentity
  ): PhysicalOp =
    // TODO: use multiple workers
    PhysicalOp
      .sourcePhysicalOp(
        workflowId,
        executionId,
        operatorIdentifier,
        OpExecWithClassName(
          "edu.uci.ics.amber.operator.source.apis.twitter.v2.TwitterSearchSourceOpExec",
          objectMapper.writeValueAsString(this)
        )
      )
      .withInputPorts(operatorInfo.inputPorts)
      .withOutputPorts(operatorInfo.outputPorts)
      .withPropagateSchema(
        SchemaPropagationFunc(_ => Map(operatorInfo.outputPorts.head.id -> sourceSchema()))
      )

  override def sourceSchema(): Schema = {

    // twitter schema is hard coded for now. V2 API has changed many fields of the Tweet object.
    // we are also currently depending on redouane59/twittered client library to parse tweet fields.

    Schema()
      .add("id", AttributeType.STRING)
      .add("text", AttributeType.STRING)
      .add("created_at", AttributeType.TIMESTAMP)
      .add("lang", AttributeType.STRING)
      .add("tweet_type", AttributeType.STRING)
      .add("place_id", AttributeType.STRING)
      .add("place_coordinate", AttributeType.STRING)
      .add("in_reply_to_status_id", AttributeType.STRING)
      .add("in_reply_to_user_id", AttributeType.STRING)
      .add("like_count", AttributeType.LONG)
      .add("quote_count", AttributeType.LONG)
      .add("reply_count", AttributeType.LONG)
      .add("retweet_count", AttributeType.LONG)
      .add("hashtags", AttributeType.STRING)
      .add("symbols", AttributeType.STRING)
      .add("urls", AttributeType.STRING)
      .add("mentions", AttributeType.STRING)
      .add("user_id", AttributeType.STRING)
      .add("user_created_at", AttributeType.TIMESTAMP)
      .add("user_name", AttributeType.STRING)
      .add("user_display_name", AttributeType.STRING)
      .add("user_lang", AttributeType.STRING)
      .add("user_description", AttributeType.STRING)
      .add("user_followers_count", AttributeType.LONG)
      .add("user_following_count", AttributeType.LONG)
      .add("user_tweet_count", AttributeType.LONG)
      .add("user_listed_count", AttributeType.LONG)
      .add("user_location", AttributeType.STRING)
      .add("user_url", AttributeType.STRING)
      .add("user_profile_image_url", AttributeType.STRING)
      .add("user_pinned_tweet_id", AttributeType.STRING)
      .add("user_protected", AttributeType.BOOLEAN)
      .add("user_verified", AttributeType.BOOLEAN)
  }
}
