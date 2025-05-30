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

package edu.uci.ics.amber.operator.source.scan.arrow

import edu.uci.ics.amber.core.executor.SourceOperatorExecutor
import edu.uci.ics.amber.core.storage.DocumentFactory
import edu.uci.ics.amber.core.tuple.TupleLike
import edu.uci.ics.amber.util.ArrowUtils
import edu.uci.ics.amber.util.JSONUtils.objectMapper
import org.apache.arrow.memory.RootAllocator
import org.apache.arrow.vector.VectorSchemaRoot
import org.apache.arrow.vector.ipc.ArrowFileReader

import java.net.URI
import java.nio.file.{Files, StandardOpenOption}

class ArrowSourceOpExec(
    descString: String
) extends SourceOperatorExecutor {
  private val desc: ArrowSourceOpDesc =
    objectMapper.readValue(descString, classOf[ArrowSourceOpDesc])
  private var reader: Option[ArrowFileReader] = None
  private var root: Option[VectorSchemaRoot] = None
  private var allocator: Option[RootAllocator] = None

  override def open(): Unit = {
    try {
      val file = DocumentFactory.openReadonlyDocument(new URI(desc.fileName.get)).asFile()
      val alloc = new RootAllocator()
      allocator = Some(alloc)
      val channel = Files.newByteChannel(file.toPath, StandardOpenOption.READ)
      val arrowReader = new ArrowFileReader(channel, alloc)
      val vectorRoot = arrowReader.getVectorSchemaRoot
      reader = Some(arrowReader)
      root = Some(vectorRoot)
    } catch {
      case e: Exception =>
        close() // Ensure resources are closed in case of an error
        throw new RuntimeException("Failed to open Arrow source", e)
    }
  }

  override def produceTuple(): Iterator[TupleLike] = {
    val rowIterator = new Iterator[TupleLike] {
      private var currentIndex = 0
      private var currentBatchIndex = 0

      override def hasNext: Boolean = {
        if (root.exists(_.getRowCount > currentIndex)) {
          true
        } else {
          reader.exists(arrowReader => {
            val hasMoreBatches = arrowReader.loadNextBatch()
            if (hasMoreBatches) {
              currentIndex = 0
              currentBatchIndex += 1
              true
            } else {
              false
            }
          })
        }
      }

      override def next(): TupleLike = {
        root.map { vectorSchemaRoot =>
          val tuple = ArrowUtils.getTexeraTuple(currentIndex, vectorSchemaRoot)
          currentIndex += 1
          tuple
        }.get
      }
    }

    var tupleIterator = rowIterator.drop(desc.offset.getOrElse(0))
    if (desc.limit.isDefined) tupleIterator = tupleIterator.take(desc.limit.get)
    tupleIterator
  }

  override def close(): Unit = {
    reader.foreach(_.close())
    root.foreach(_.close())
    allocator.foreach(_.close())
  }
}
