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

import edu.uci.ics.amber.core.tuple.{AttributeType, Schema, SchemaEnforceable, Tuple}
import edu.uci.ics.amber.operator.TestOperators
import edu.uci.ics.amber.operator.source.scan.FileAttributeType
import edu.uci.ics.amber.util.JSONUtils.objectMapper
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

class TextInputSourceOpDescSpec extends AnyFlatSpec with BeforeAndAfter {
  var textInputSourceOpDesc: TextInputSourceOpDesc = _

  before {
    textInputSourceOpDesc = new TextInputSourceOpDesc()
  }

  it should "infer schema with single column representing each line of text in normal text scan mode" in {
    val inferredSchema: Schema = textInputSourceOpDesc.sourceSchema()

    assert(inferredSchema.getAttributes.length == 1)
    assert(inferredSchema.getAttribute("line").getType == AttributeType.STRING)
  }

  it should "infer schema with single column representing entire input in outputAsSingleTuple mode" in {
    textInputSourceOpDesc.attributeType = FileAttributeType.SINGLE_STRING
    val inferredSchema: Schema = textInputSourceOpDesc.sourceSchema()

    assert(inferredSchema.getAttributes.length == 1)
    assert(inferredSchema.getAttribute("line").getType == AttributeType.STRING)
  }

  it should "infer schema with user-specified output schema attribute" in {
    textInputSourceOpDesc.attributeType = FileAttributeType.STRING
    val customOutputAttributeName: String = "testing"
    textInputSourceOpDesc.attributeName = customOutputAttributeName
    val inferredSchema: Schema = textInputSourceOpDesc.sourceSchema()

    assert(inferredSchema.getAttributes.length == 1)
    assert(inferredSchema.getAttribute("testing").getType == AttributeType.STRING)
  }

  it should "infer schema with integer attribute type" in {
    textInputSourceOpDesc.attributeType = FileAttributeType.INTEGER
    val inferredSchema: Schema = textInputSourceOpDesc.sourceSchema()

    assert(inferredSchema.getAttributes.length == 1)
    assert(inferredSchema.getAttribute("line").getType == AttributeType.INTEGER)
  }

  it should "read first 5 lines of the input text into corresponding output tuples" in {
    val inputString: String = readFileIntoString(TestOperators.TestTextFilePath)
    textInputSourceOpDesc.attributeType = FileAttributeType.STRING
    textInputSourceOpDesc.textInput = inputString
    textInputSourceOpDesc.fileScanLimit = Option(5)
    val textScanSourceOpExec =
      new TextInputSourceOpExec(objectMapper.writeValueAsString(textInputSourceOpDesc))
    textScanSourceOpExec.open()
    val processedTuple: Iterator[Tuple] = textScanSourceOpExec
      .produceTuple()
      .map(tupleLike =>
        tupleLike
          .asInstanceOf[SchemaEnforceable]
          .enforceSchema(textInputSourceOpDesc.sourceSchema())
      )

    assert(processedTuple.next().getField("line").equals("line1"))
    assert(processedTuple.next().getField("line").equals("line2"))
    assert(processedTuple.next().getField("line").equals("line3"))
    assert(processedTuple.next().getField("line").equals("line4"))
    assert(processedTuple.next().getField("line").equals("line5"))
    assertThrows[java.util.NoSuchElementException](processedTuple.next().getField("line"))
    textScanSourceOpExec.close()
  }

  it should "read first 5 lines of the input text with CRLF separators into corresponding output tuples" in {
    val inputString: String = readFileIntoString(TestOperators.TestCRLFTextFilePath)
    textInputSourceOpDesc.attributeType = FileAttributeType.STRING
    textInputSourceOpDesc.textInput = inputString
    textInputSourceOpDesc.fileScanLimit = Option(5)
    val textScanSourceOpExec =
      new TextInputSourceOpExec(objectMapper.writeValueAsString(textInputSourceOpDesc))
    textScanSourceOpExec.open()
    val processedTuple: Iterator[Tuple] = textScanSourceOpExec
      .produceTuple()
      .map(tupleLike =>
        tupleLike
          .asInstanceOf[SchemaEnforceable]
          .enforceSchema(textInputSourceOpDesc.sourceSchema())
      )

    assert(processedTuple.next().getField("line").equals("line1"))
    assert(processedTuple.next().getField("line").equals("line2"))
    assert(processedTuple.next().getField("line").equals("line3"))
    assert(processedTuple.next().getField("line").equals("line4"))
    assert(processedTuple.next().getField("line").equals("line5"))
    assertThrows[java.util.NoSuchElementException](processedTuple.next().getField("line"))
    textScanSourceOpExec.close()
  }

  it should "read first 5 lines of the input text into a single output tuple" in {
    val inputString: String = readFileIntoString(TestOperators.TestTextFilePath)
    textInputSourceOpDesc.attributeType = FileAttributeType.SINGLE_STRING
    textInputSourceOpDesc.textInput = inputString
    val textScanSourceOpExec =
      new TextInputSourceOpExec(objectMapper.writeValueAsString(textInputSourceOpDesc))
    textScanSourceOpExec.open()
    val processedTuple: Iterator[Tuple] = textScanSourceOpExec
      .produceTuple()
      .map(tupleLike =>
        tupleLike
          .asInstanceOf[SchemaEnforceable]
          .enforceSchema(textInputSourceOpDesc.sourceSchema())
      )

    assert(
      processedTuple
        .next()
        .getField[String]("line")
        .equals("line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\nline10")
    )
    assertThrows[java.util.NoSuchElementException](processedTuple.next().getField("line"))
    textScanSourceOpExec.close()
  }

  it should "read first 5 lines of the input text into corresponding output INTEGER tuples" in {
    val inputString: String = readFileIntoString(TestOperators.TestNumbersFilePath)
    textInputSourceOpDesc.attributeType = FileAttributeType.INTEGER
    textInputSourceOpDesc.textInput = inputString
    textInputSourceOpDesc.fileScanLimit = Option(5)
    val textScanSourceOpExec =
      new TextInputSourceOpExec(objectMapper.writeValueAsString(textInputSourceOpDesc))
    textScanSourceOpExec.open()
    val processedTuple: Iterator[Tuple] = textScanSourceOpExec
      .produceTuple()
      .map(tupleLike =>
        tupleLike
          .asInstanceOf[SchemaEnforceable]
          .enforceSchema(textInputSourceOpDesc.sourceSchema())
      )

    assert(processedTuple.next().getField[Int]("line") == 1)
    assert(processedTuple.next().getField[Int]("line") == 2)
    assert(processedTuple.next().getField[Int]("line") == 3)
    assert(processedTuple.next().getField[Int]("line") == 4)
    assert(processedTuple.next().getField[Int]("line") == 5)
    assertThrows[java.util.NoSuchElementException](processedTuple.next().getField("line"))
    textScanSourceOpExec.close()
  }

  /**
    * Helper function using UTF-8 encoding to read text file
    * into String
    *
    * @param filePath path of input file
    * @return entire file represented as String
    */
  def readFileIntoString(filePath: String): String = {
    val path: Path = Paths.get(filePath)
    new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
  }
}
