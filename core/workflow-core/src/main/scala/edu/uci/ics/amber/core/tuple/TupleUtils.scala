package edu.uci.ics.amber.core.tuple

import com.fasterxml.jackson.databind.JsonNode
import edu.uci.ics.amber.util.JSONUtils.JSONToMap
import edu.uci.ics.amber.util.JSONUtils.objectMapper
import AttributeTypeUtils.{inferSchemaFromRows, parseField}
import AttributeType.BINARY
import org.bson.Document
import org.bson.types.Binary

import scala.collection.mutable.ArrayBuffer
import com.fasterxml.jackson.databind.node.ObjectNode
import edu.uci.ics.amber.util.JSONUtils

object TupleUtils {

  def tuple2json(schema: Schema, fieldVals: Array[Any]): ObjectNode = {
    val objectNode = JSONUtils.objectMapper.createObjectNode()
    schema.getAttributeNames.foreach { attrName =>
      val valueNode =
        JSONUtils.objectMapper.convertValue(fieldVals(schema.getIndex(attrName)), classOf[JsonNode])
      objectNode.set[ObjectNode](attrName, valueNode)
    }
    objectNode
  }

  def json2tuple(json: String): Tuple = {
    var fieldNames = Set[String]()

    val allFields: ArrayBuffer[Map[String, String]] = ArrayBuffer()

    val root: JsonNode = objectMapper.readTree(json)
    if (root.isObject) {
      val fields: Map[String, String] = JSONToMap(root)
      fieldNames = fieldNames.++(fields.keySet)
      allFields += fields
    }

    val sortedFieldNames = fieldNames.toList

    val attributeTypes = inferSchemaFromRows(allFields.iterator.map(fields => {
      val result = ArrayBuffer[Object]()
      for (fieldName <- sortedFieldNames) {
        if (fields.contains(fieldName)) {
          result += fields(fieldName)
        } else {
          result += null
        }
      }
      result.toArray
    }))

    val schema = Schema(
      sortedFieldNames.indices
        .map(i => new Attribute(sortedFieldNames(i), attributeTypes(i)))
        .toList
    )

    try {
      val fields = scala.collection.mutable.ArrayBuffer.empty[Any]
      val data = JSONToMap(objectMapper.readTree(json))

      for (fieldName <- schema.getAttributeNames) {
        if (data.contains(fieldName)) {
          fields += parseField(data(fieldName), schema.getAttribute(fieldName).getType)
        } else {
          fields += null
        }
      }
      Tuple.builder(schema).addSequentially(fields.toArray).build()
    } catch {
      case e: Exception => throw e
    }
  }

  def document2Tuple(doc: Document, schema: Schema): Tuple = {
    val builder = Tuple.builder(schema)
    schema.getAttributes.foreach(attr =>
      if (attr.getType == BINARY) {
        // special care for converting MongoDB's binary type to byte[] in our schema
        builder.add(attr, doc.get(attr.getName).asInstanceOf[Binary].getData)
      } else {
        builder.add(attr, parseField(doc.get(attr.getName), attr.getType))
      }
    )
    builder.build()
  }

}
