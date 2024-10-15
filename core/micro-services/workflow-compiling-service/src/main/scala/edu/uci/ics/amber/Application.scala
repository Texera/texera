package edu.uci.ics.amber

import edu.uci.ics.amber.core.tuple.{Attribute, AttributeType, Schema, Tuple}

object Application {
  def main(args: Array[String]): Unit = {

    val stringAttribute = new Attribute("col-string", AttributeType.STRING)
    val integerAttribute = new Attribute("col-int", AttributeType.INTEGER)
    val boolAttribute = new Attribute("col-bool", AttributeType.BOOLEAN)

    val inputSchema =
      Schema.builder().add(stringAttribute).add(integerAttribute).add(boolAttribute).build()
    val inputTuple = Tuple
      .builder(inputSchema)
      .add(integerAttribute, 1)
      .add(stringAttribute, "string-attr")
      .add(boolAttribute, true)
      .build()

    val outputSchema = Schema.builder().add(stringAttribute).add(integerAttribute).build()
    val outputTuple = Tuple.builder(outputSchema).add(inputTuple, false).build()
    println(outputTuple)
  }
}
