package edu.uci.ics.texera.workflow.operators.visualization.urlviz

import edu.uci.ics.amber.engine.common.InputExhausted
import edu.uci.ics.texera.workflow.common.operators.OperatorExecutor
import edu.uci.ics.texera.workflow.common.tuple.Tuple
import edu.uci.ics.texera.workflow.common.tuple.schema.{AttributeType, Schema}

/**
  * URL Visualization operator to render any given URL link
  */
class UrlVizOpExec(
    urlContentAttrName: String,
    outputSchema: Schema
) extends OperatorExecutor {

  override def open(): Unit = {}

  override def close(): Unit = {}

  override def processTuple(
      tuple: Either[Tuple, InputExhausted],
      port: Int
  ): Iterator[Tuple] =
    tuple match {
      case Left(tuple) =>
        val iframe =
          s"""<!DOCTYPE html>
              |<html lang="en">
              |<body>
              |  <div class="modal-body">
              |    <iframe src="${tuple.getField(urlContentAttrName)}" frameborder="0"
              |       style="height:100vh; width:100%; border:none;">
              |    </iframe>
              |  </div>
              |</body>
              |</html>""".stripMargin
        Iterator(
          Tuple
            .newBuilder(outputSchema)
            .add("html-content", AttributeType.STRING, iframe)
            .build()
        )

      case Right(_) => Iterator()
    }
}
