package edu.uci.ics.texera.workflow.common

import edu.uci.ics.texera.workflow.common.tuple.Tuple
import edu.uci.ics.texera.workflow.common.tuple.schema.{Attribute, AttributeType}

object ProgressiveUtils {

  // boolean attribute to indicate insertion / retraction
  // true  indicates insertion  (+)
  // false indicates retraction (-)
  val insertRetractFlagAttr = new Attribute("__internal_is_insertion", AttributeType.BOOLEAN)

  def addInsertionFlag(tuple: Tuple): Tuple = {
    assert(!tuple.getSchema.containsAttribute(insertRetractFlagAttr.getName))
    Tuple.newBuilder.add(insertRetractFlagAttr, true).add(tuple).build
  }

  def addRetractionFlag(tuple: Tuple): Tuple = {
    assert(!tuple.getSchema.containsAttribute(insertRetractFlagAttr.getName))
    Tuple.newBuilder.add(insertRetractFlagAttr, false).add(tuple).build
  }

  def isInsertion(tuple: Tuple): Boolean = {
    if (tuple.getSchema.containsAttribute(insertRetractFlagAttr.getName)) {
      tuple.getField[Boolean](insertRetractFlagAttr.getName)
    } else {
      true
    }
  }

  def getTupleFlagAndValue(tuple: Tuple): (Boolean, Tuple) = {
    (
      isInsertion(tuple),
      Tuple.newBuilder().add(tuple).removeIfExists(insertRetractFlagAttr.getName).build()
    )
  }

}
