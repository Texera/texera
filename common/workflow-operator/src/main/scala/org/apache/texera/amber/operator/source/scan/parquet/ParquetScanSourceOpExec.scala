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

package org.apache.texera.amber.operator.source.scan.parquet

import org.apache.parquet.example.data.Group
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter
import org.apache.parquet.hadoop.ParquetFileReader
import org.apache.parquet.io.{ColumnIOFactory, LocalInputFile}
import org.apache.parquet.schema.LogicalTypeAnnotation.{
  DateLogicalTypeAnnotation,
  StringLogicalTypeAnnotation,
  TimeUnit,
  TimestampLogicalTypeAnnotation
}
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName
import org.apache.parquet.schema.MessageType
import org.apache.texera.amber.core.executor.SourceOperatorExecutor
import org.apache.texera.amber.core.storage.DocumentFactory
import org.apache.texera.amber.core.tuple.TupleLike
import org.apache.texera.amber.util.JSONUtils.objectMapper

import java.net.URI
import java.sql.Timestamp
import java.time.{Instant, LocalDate, LocalDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit.{MICROSECONDS, MILLISECONDS, NANOSECONDS}
import scala.jdk.CollectionConverters._

class ParquetScanSourceOpExec(descString: String) extends SourceOperatorExecutor {
  private val desc: ParquetScanSourceOpDesc =
    objectMapper.readValue(descString, classOf[ParquetScanSourceOpDesc])
  private var reader: Option[ParquetFileReader] = None

  override def open(): Unit = {
    val file = DocumentFactory.openReadonlyDocument(new URI(desc.fileName.get)).asFile()
    reader = Some(ParquetFileReader.open(new LocalInputFile(file.toPath)))
  }

  override def produceTuple(): Iterator[TupleLike] = {
    val fileReader = reader.get
    val messageType = fileReader.getFooter.getFileMetaData.getSchema
    val columns = messageType.getFields.asScala.toVector

    // One row group at a time: the format stores rows in groups and a reader
    // that asked for all of them at once would hold the whole file in memory,
    // which is the thing a columnar format is chosen to avoid.
    val rows: Iterator[TupleLike] = Iterator
      .continually(fileReader.readNextRowGroup())
      .takeWhile(_ != null)
      .flatMap { pages =>
        val recordReader = new ColumnIOFactory()
          .getColumnIO(messageType)
          .getRecordReader(pages, new GroupRecordConverter(messageType))
        (0L until pages.getRowCount).iterator.map { _ =>
          val group = recordReader.read()
          TupleLike(columns.indices.map(i => readField(group, i, messageType)): _*)
        }
      }

    val afterOffset = rows.drop(desc.offset.getOrElse(0))
    desc.limit.fold(afterOffset)(afterOffset.take)
  }

  /** One cell, as the Texera type [[ParquetSchemaMapping]] said the column is. */
  private def readField(group: Group, index: Int, messageType: MessageType): Any = {
    // An optional column that was not written for this row repeats zero times.
    // Parquet has no "null value": absence is the null.
    if (group.getFieldRepetitionCount(index) == 0) return null
    val primitive = messageType.getType(index).asPrimitiveType()
    primitive.getPrimitiveTypeName match {
      case PrimitiveTypeName.BOOLEAN => group.getBoolean(index, 0)
      case PrimitiveTypeName.FLOAT   => group.getFloat(index, 0).toDouble
      case PrimitiveTypeName.DOUBLE  => group.getDouble(index, 0)
      case PrimitiveTypeName.INT32 =>
        val raw = group.getInteger(index, 0)
        primitive.getLogicalTypeAnnotation match {
          // A DATE is a count of days, and Texera's nearest column is a moment.
          // Midnight of that day, in the same UTC the file counts from.
          case _: DateLogicalTypeAnnotation =>
            Timestamp.valueOf(LocalDate.ofEpochDay(raw.toLong).atStartOfDay)
          case _ => raw
        }
      case PrimitiveTypeName.INT64 =>
        val raw = group.getLong(index, 0)
        primitive.getLogicalTypeAnnotation match {
          case annotation: TimestampLogicalTypeAnnotation =>
            // A Texera TIMESTAMP carries no zone, so the count from the epoch is
            // read with UTC arithmetic and the wall clock it lands on is the
            // whole of the value. `new Timestamp(millis)` would instead shift it
            // by whatever zone the machine running the workflow is set to, and
            // the exported script, which reads the same file with pandas, would
            // disagree by exactly that offset.
            Timestamp.valueOf(
              LocalDateTime.ofInstant(
                Instant.ofEpochMilli(toMillis(raw, annotation.getUnit)),
                ZoneOffset.UTC
              )
            )
          case _ => raw
        }
      case PrimitiveTypeName.BINARY =>
        primitive.getLogicalTypeAnnotation match {
          case _: StringLogicalTypeAnnotation => group.getBinary(index, 0).toStringUsingUTF8
          case _                              => group.getBinary(index, 0).getBytes
        }
      case PrimitiveTypeName.FIXED_LEN_BYTE_ARRAY | PrimitiveTypeName.INT96 =>
        group.getBinary(index, 0).getBytes
    }
  }

  /** A timestamp in whatever unit the file counts in, as milliseconds. */
  private def toMillis(value: Long, unit: TimeUnit): Long =
    unit match {
      case TimeUnit.MILLIS => value
      case TimeUnit.MICROS => MILLISECONDS.convert(value, MICROSECONDS)
      case TimeUnit.NANOS  => MILLISECONDS.convert(value, NANOSECONDS)
    }

  override def close(): Unit = reader.foreach(_.close())
}
