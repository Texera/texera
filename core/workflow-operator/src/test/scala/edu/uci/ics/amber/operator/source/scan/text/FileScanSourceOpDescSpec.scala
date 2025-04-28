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

package edu.uci.ics.amber.operator.source.scan.text

import edu.uci.ics.amber.core.storage.FileResolver
import edu.uci.ics.amber.core.tuple.{AttributeType, Schema, SchemaEnforceable, Tuple}
import edu.uci.ics.amber.operator.TestOperators
import edu.uci.ics.amber.operator.source.scan.{
  FileAttributeType,
  FileDecodingMethod,
  FileScanSourceOpDesc,
  FileScanSourceOpExec
}
import edu.uci.ics.amber.util.JSONUtils.objectMapper
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec

class FileScanSourceOpDescSpec extends AnyFlatSpec with BeforeAndAfter {

  var fileScanSourceOpDesc: FileScanSourceOpDesc = _

  before {
    fileScanSourceOpDesc = new FileScanSourceOpDesc()
    fileScanSourceOpDesc.setResolvedFileName(FileResolver.resolve(TestOperators.TestTextFilePath))
    fileScanSourceOpDesc.fileEncoding = FileDecodingMethod.UTF_8
  }

  it should "infer schema with single column representing each line of text in normal text scan mode" in {
    val inferredSchema: Schema = fileScanSourceOpDesc.sourceSchema()

    assert(inferredSchema.getAttributes.length == 1)
    assert(inferredSchema.getAttribute("line").getType == AttributeType.STRING)
  }

  it should "infer schema with single column representing entire file in outputAsSingleTuple mode" in {
    fileScanSourceOpDesc.attributeType = FileAttributeType.SINGLE_STRING
    val inferredSchema: Schema = fileScanSourceOpDesc.sourceSchema()

    assert(inferredSchema.getAttributes.length == 1)
    assert(inferredSchema.getAttribute("line").getType == AttributeType.STRING)
  }

  it should "infer schema with user-specified output schema attribute" in {
    fileScanSourceOpDesc.attributeType = FileAttributeType.STRING
    val customOutputAttributeName: String = "testing"
    fileScanSourceOpDesc.attributeName = customOutputAttributeName
    val inferredSchema: Schema = fileScanSourceOpDesc.sourceSchema()

    assert(inferredSchema.getAttributes.length == 1)
    assert(inferredSchema.getAttribute("testing").getType == AttributeType.STRING)
  }

  it should "infer schema with integer attribute type" in {
    fileScanSourceOpDesc.attributeType = FileAttributeType.INTEGER
    val inferredSchema: Schema = fileScanSourceOpDesc.sourceSchema()

    assert(inferredSchema.getAttributes.length == 1)
    assert(inferredSchema.getAttribute("line").getType == AttributeType.INTEGER)
  }

  it should "read first 5 lines of the input text file into corresponding output tuples" in {
    fileScanSourceOpDesc.attributeType = FileAttributeType.STRING
    fileScanSourceOpDesc.fileScanLimit = Option(5)
    val FileScanSourceOpExec =
      new FileScanSourceOpExec(objectMapper.writeValueAsString(fileScanSourceOpDesc))
    FileScanSourceOpExec.open()
    val processedTuple: Iterator[Tuple] = FileScanSourceOpExec
      .produceTuple()
      .map(tupleLike =>
        tupleLike.asInstanceOf[SchemaEnforceable].enforceSchema(fileScanSourceOpDesc.sourceSchema())
      )

    assert(processedTuple.next().getField("line").equals("line1"))
    assert(processedTuple.next().getField("line").equals("line2"))
    assert(processedTuple.next().getField("line").equals("line3"))
    assert(processedTuple.next().getField("line").equals("line4"))
    assert(processedTuple.next().getField("line").equals("line5"))
    assertThrows[java.util.NoSuchElementException](processedTuple.next().getField("line"))
    FileScanSourceOpExec.close()
  }

  it should "read first 5 lines of the input text file with CRLF separators into corresponding output tuples" in {
    fileScanSourceOpDesc.setResolvedFileName(
      FileResolver.resolve(TestOperators.TestCRLFTextFilePath)
    )
    fileScanSourceOpDesc.attributeType = FileAttributeType.STRING
    fileScanSourceOpDesc.fileScanLimit = Option(5)
    val FileScanSourceOpExec =
      new FileScanSourceOpExec(objectMapper.writeValueAsString(fileScanSourceOpDesc))
    FileScanSourceOpExec.open()
    val processedTuple: Iterator[Tuple] = FileScanSourceOpExec
      .produceTuple()
      .map(tupleLike =>
        tupleLike.asInstanceOf[SchemaEnforceable].enforceSchema(fileScanSourceOpDesc.sourceSchema())
      )

    assert(processedTuple.next().getField("line").equals("line1"))
    assert(processedTuple.next().getField("line").equals("line2"))
    assert(processedTuple.next().getField("line").equals("line3"))
    assert(processedTuple.next().getField("line").equals("line4"))
    assert(processedTuple.next().getField("line").equals("line5"))
    assertThrows[java.util.NoSuchElementException](processedTuple.next().getField("line"))
    FileScanSourceOpExec.close()
  }

  it should "read first 5 lines of the input text file into a single output tuple" in {
    fileScanSourceOpDesc.attributeType = FileAttributeType.SINGLE_STRING
    val FileScanSourceOpExec =
      new FileScanSourceOpExec(objectMapper.writeValueAsString(fileScanSourceOpDesc))
    FileScanSourceOpExec.open()
    val processedTuple: Iterator[Tuple] = FileScanSourceOpExec
      .produceTuple()
      .map(tupleLike =>
        tupleLike.asInstanceOf[SchemaEnforceable].enforceSchema(fileScanSourceOpDesc.sourceSchema())
      )

    assert(
      processedTuple
        .next()
        .getField("line")
        .equals("line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\nline10")
    )
    assertThrows[java.util.NoSuchElementException](processedTuple.next().getField("line"))
    FileScanSourceOpExec.close()
  }

  it should "read first 5 lines of the input text into corresponding output INTEGER tuples" in {
    fileScanSourceOpDesc.setResolvedFileName(
      FileResolver.resolve(TestOperators.TestNumbersFilePath)
    )
    fileScanSourceOpDesc.attributeType = FileAttributeType.INTEGER
    fileScanSourceOpDesc.fileScanLimit = Option(5)
    val FileScanSourceOpExec =
      new FileScanSourceOpExec(objectMapper.writeValueAsString(fileScanSourceOpDesc))
    FileScanSourceOpExec.open()
    val processedTuple: Iterator[Tuple] = FileScanSourceOpExec
      .produceTuple()
      .map(tupleLike =>
        tupleLike.asInstanceOf[SchemaEnforceable].enforceSchema(fileScanSourceOpDesc.sourceSchema())
      )

    assert(processedTuple.next().getField[Int]("line") == 1)
    assert(processedTuple.next().getField[Int]("line") == 2)
    assert(processedTuple.next().getField[Int]("line") == 3)
    assert(processedTuple.next().getField[Int]("line") == 4)
    assert(processedTuple.next().getField[Int]("line") == 5)
    assertThrows[java.util.NoSuchElementException](processedTuple.next().getField("line"))
    FileScanSourceOpExec.close()
  }

  it should "read first 5 lines of the input text file with US_ASCII encoding" in {
    fileScanSourceOpDesc.setResolvedFileName(
      FileResolver.resolve(TestOperators.TestCRLFTextFilePath)
    )
    fileScanSourceOpDesc.fileEncoding = FileDecodingMethod.ASCII
    fileScanSourceOpDesc.attributeType = FileAttributeType.STRING
    fileScanSourceOpDesc.fileScanLimit = Option(5)
    val FileScanSourceOpExec =
      new FileScanSourceOpExec(objectMapper.writeValueAsString(fileScanSourceOpDesc))
    FileScanSourceOpExec.open()
    val processedTuple: Iterator[Tuple] = FileScanSourceOpExec
      .produceTuple()
      .map(tupleLike =>
        tupleLike.asInstanceOf[SchemaEnforceable].enforceSchema(fileScanSourceOpDesc.sourceSchema())
      )

    assert(processedTuple.next().getField("line").equals("line1"))
    assert(processedTuple.next().getField("line").equals("line2"))
    assert(processedTuple.next().getField("line").equals("line3"))
    assert(processedTuple.next().getField("line").equals("line4"))
    assert(processedTuple.next().getField("line").equals("line5"))
    assertThrows[java.util.NoSuchElementException](processedTuple.next().getField("line"))
    FileScanSourceOpExec.close()
  }

}
