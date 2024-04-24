package edu.uci.ics.amber.engine.common.storage.file
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{SerializerProvider, JsonSerializer}

/**
  * FileTreeNodeSerializer ensures that the FileTreeNode is serialized in the certain json format when passing to frontend
  */
class FileTreeNodeSerializer extends JsonSerializer[FileTreeNode] {

  override def serialize(
      value: FileTreeNode,
      gen: JsonGenerator,
      serializers: SerializerProvider
  ): Unit = {
    gen.writeStartObject()
    gen.writeStringField("path", value.getRelativePath.toString)

    val isFile = value.getChildren.isEmpty
    gen.writeBooleanField("isFile", isFile)

    if (!isFile) {
      gen.writeFieldName("children")
      gen.writeStartArray()
      value.getChildren.foreach { child =>
        serialize(child, gen, serializers) // Recursively serialize children
      }
      gen.writeEndArray()
    }
    gen.writeEndObject()
  }
}
