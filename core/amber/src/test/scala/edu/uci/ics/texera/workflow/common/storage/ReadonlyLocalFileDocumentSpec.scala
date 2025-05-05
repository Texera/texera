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

package edu.uci.ics.amber.engine.common.storage

import edu.uci.ics.amber.core.storage.DocumentFactory
import edu.uci.ics.amber.core.storage.model.ReadonlyVirtualDocument
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayOutputStream, File, InputStream}
import java.nio.file.Files
import scala.util.Using

class ReadonlyLocalFileDocumentSpec extends AnyFlatSpec with Matchers with BeforeAndAfter {

  var tempFile: File = _
  var fileDocument: ReadonlyVirtualDocument[_] = _

  val initialContent =
    "Initial Content\nsome more content to make the text longer\nadf\t\ttestteset"

  before {
    // Create a temporary file with initial content
    tempFile = File.createTempFile("test", ".txt")
    Files.write(tempFile.toPath, initialContent.getBytes)
    fileDocument = DocumentFactory.openReadonlyDocument(tempFile.toURI)
  }

  after {
    // Delete the temporary file
    Files.deleteIfExists(tempFile.toPath)
  }

  private def readAllBytes(inputStream: InputStream): Array[Byte] = {
    val buffer = new ByteArrayOutputStream()
    val data = new Array[Byte](1024)
    var nRead = 0
    while ({
      nRead = inputStream.read(data, 0, data.length)
      nRead != -1
    }) {
      buffer.write(data, 0, nRead)
    }
    buffer.flush()
    buffer.toByteArray
  }

  "ReadonlyLocalFileDocument" should "correctly report its URI" in {
    fileDocument.getURI should be(tempFile.toURI)
  }

  it should "allow reading from the file" in {
    val content = Using(fileDocument.asInputStream()) { inStream =>
      new String(readAllBytes(inStream))
    }.getOrElse(fail("Failed to read from the file"))

    content should equal(initialContent)
  }

  it should "return the file itself when asFile is called" in {
    fileDocument.asFile() should be(tempFile)
  }

  it should "throw NotImplementedError for unsupported getItem method" in {
    intercept[NotImplementedError] {
      fileDocument.getItem(0)
    }.getMessage should include("getItem is not supported")
  }

  it should "throw NotImplementedError for unsupported get method" in {
    intercept[NotImplementedError] {
      fileDocument.get()
    }.getMessage should include("get is not supported")
  }

  it should "throw NotImplementedError for unsupported getRange method" in {
    intercept[NotImplementedError] {
      fileDocument.getRange(0, 1)
    }.getMessage should include("getRange is not supported")
  }

  it should "throw NotImplementedError for unsupported getAfter method" in {
    intercept[NotImplementedError] {
      fileDocument.getAfter(0)
    }.getMessage should include("getAfter is not supported")
  }

  it should "throw NotImplementedError for unsupported getCount method" in {
    intercept[NotImplementedError] {
      fileDocument.getCount
    }.getMessage should include("getCount is not supported")
  }
}
