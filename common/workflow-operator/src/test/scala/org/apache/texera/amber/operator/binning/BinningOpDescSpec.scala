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

package org.apache.texera.amber.operator.binning

import org.apache.texera.amber.core.tuple.{Attribute, AttributeType, Schema}
import org.apache.texera.amber.core.workflow.PortIdentity
import org.apache.texera.amber.operator.metadata.OperatorGroupConstants
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BinningOpDescSpec extends AnyFlatSpec with Matchers {

  private val inputSchema = new Schema(
    new Attribute("id", AttributeType.INTEGER),
    new Attribute("age", AttributeType.DOUBLE)
  )

  private def desc(m: BinningMethod = BinningMethod.EQUAL_WIDTH, n: Int = 4): BinningOpDesc = {
    val d = new BinningOpDesc
    d.attribute = "age"
    d.method = m
    d.bins = n
    d
  }

  "BinningOpDesc.operatorInfo" should "advertise the name and the Cleaning group" in {
    val info = (new BinningOpDesc).operatorInfo
    info.userFriendlyName shouldBe "Binning"
    info.operatorGroupName shouldBe OperatorGroupConstants.CLEANING_GROUP
    info.inputPorts should have length 1
    info.outputPorts should have length 1
  }

  // A quantile cut is made at the column's own quantiles, which the last row can
  // still move, so nothing may be emitted until the input ends.
  it should "declare its output port blocking" in {
    (new BinningOpDesc).operatorInfo.outputPorts.head.blocking shouldBe true
  }

  "The output schema" should "append one STRING column named after the source" in {
    val schema = desc().getOutputSchemas(Map(PortIdentity() -> inputSchema))(PortIdentity())
    schema.getAttributeNames shouldBe List("id", "age", "age_bin")
    schema.getAttribute("age_bin").getType shouldBe AttributeType.STRING
  }

  it should "refuse a derived name the input already carries" in {
    val clashing = inputSchema.add("age_bin", AttributeType.STRING)
    a[RuntimeException] should be thrownBy
      desc().getOutputSchemas(Map(PortIdentity() -> clashing))
  }

  "The generated code" should "cut on the count of bins asked for" in {
    desc(BinningMethod.EQUAL_WIDTH, 7).generateStandaloneCode() should include("bins=7")
  }

  // A quantile cut can put two edges in one place where the values repeat, and
  // dropping the duplicate yields fewer bins rather than raising.
  it should "ask the quantile cut to drop a duplicate edge" in {
    val code = desc(BinningMethod.EQUAL_FREQUENCY).generateStandaloneCode()
    code should include("pd.qcut(")
    code should include("""duplicates="drop"""")
    code should include("q=4")
  }

  it should "keep an empty cell empty rather than rendering it as text" in {
    desc().generateStandaloneCode() should include("where(lambda s: s.notna(), None)")
  }

  it should "hold the source and the derived name as escaped literals" in {
    val d = desc()
    d.attribute = "a\"b"
    val code = d.generateStandaloneCode()
    code should include("""in1df.copy()""")
    code should include("""out1df["a\"b"]""")
    code should include("""out1df["a\"b_bin"]""")
  }

  // Both paths make the same call, on frames the two runtimes name differently, so
  // a difference between them is a difference in this operator rather than in pandas.
  "The two renderings" should "make the same pandas call" in {
    val d = desc(BinningMethod.EQUAL_FREQUENCY, 5)
    d.generateStandaloneCode() should include("""pd.qcut(out1df["age"], q=5, duplicates="drop")""")
    d.generatePythonCode() should include("""q=5, duplicates="drop"""")
    d.generatePythonCode() should include("pd.qcut(out[")
  }

  "The platform code" should "be a table operator, since a quantile needs every row" in {
    val code = desc().generatePythonCode()
    code should include("class ProcessTableOperator(UDFTableOperator)")
    code should include("def process_table(")
  }
}
