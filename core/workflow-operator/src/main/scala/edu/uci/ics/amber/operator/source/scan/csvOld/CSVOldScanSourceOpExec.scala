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

package edu.uci.ics.amber.operator.source.scan.csvOld

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import edu.uci.ics.amber.core.executor.SourceOperatorExecutor
import edu.uci.ics.amber.core.storage.DocumentFactory
import edu.uci.ics.amber.core.tuple.{Attribute, AttributeTypeUtils, Schema, TupleLike}
import edu.uci.ics.amber.util.JSONUtils.objectMapper

import java.net.URI
import scala.collection.compat.immutable.ArraySeq

class CSVOldScanSourceOpExec private[csvOld] (
    descString: String
) extends SourceOperatorExecutor {
  val desc: CSVOldScanSourceOpDesc =
    objectMapper.readValue(descString, classOf[CSVOldScanSourceOpDesc])
  var reader: CSVReader = _
  var rows: Iterator[Seq[String]] = _
  val schema: Schema = desc.sourceSchema()
  override def produceTuple(): Iterator[TupleLike] = {

    val tuples = rows
      .map(fields =>
        try {
          val parsedFields: Array[Any] = AttributeTypeUtils.parseFields(
            fields.toArray,
            schema.getAttributes
              .map((attr: Attribute) => attr.getType)
              .toArray
          )
          TupleLike(ArraySeq.unsafeWrapArray(parsedFields): _*)
        } catch {
          case _: Throwable => null
        }
      )
      .filter(tuple => tuple != null)

    if (desc.limit.isDefined)
      tuples.take(desc.limit.get)
    else {
      tuples
    }
  }

  override def open(): Unit = {
    implicit object CustomFormat extends DefaultCSVFormat {
      override val delimiter: Char = desc.customDelimiter.get.charAt(0)
    }
    val filePath = DocumentFactory.openReadonlyDocument(new URI(desc.fileName.get)).asFile().toPath
    reader = CSVReader.open(filePath.toString, desc.fileEncoding.getCharset.name())(CustomFormat)
    // skip line if this worker reads the start of a file, and the file has a header line
    val startOffset = desc.offset.getOrElse(0) + (if (desc.hasHeader) 1 else 0)
    rows = reader.iterator.drop(startOffset)
  }

  override def close(): Unit = {
    if (reader != null) {
      reader.close()
    }
  }
}
