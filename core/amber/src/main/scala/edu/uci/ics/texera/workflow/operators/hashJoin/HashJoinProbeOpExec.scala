package edu.uci.ics.texera.workflow.operators.hashJoin

import edu.uci.ics.amber.engine.common.InputExhausted
import edu.uci.ics.amber.engine.common.tuple.amber.TupleLike
import edu.uci.ics.texera.workflow.common.operators.OperatorExecutor
import edu.uci.ics.texera.workflow.common.tuple.Tuple

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object JoinUtils {
  def joinTuples(
      leftTuple: Tuple,
      rightTuple: Tuple,
      skipAttributeName: Option[String] = None
  ): TupleLike = {
    val leftAttributeNames = leftTuple.getSchema.getAttributeNames
    val rightAttributeNames = rightTuple.getSchema.getAttributeNames.filterNot(name =>
      skipAttributeName.isDefined && name == skipAttributeName.get
    )
    // Create a Map from leftTuple's fields
    val leftTupleFields: Map[String, Any] = leftAttributeNames
      .map(name => name -> leftTuple.getField(name))
      .toMap

    // Create a Map from rightTuple's fields, renaming conflicts
    val rightTupleFields = rightAttributeNames
      .map { name =>
        var newName = name
        while (
          leftAttributeNames.contains(newName) || rightAttributeNames
            .filter(attrName => name != attrName)
            .contains(newName)
        ) {
          newName = s"$newName#@1"
        }
        newName -> rightTuple.getField[Any](name)
      }

    TupleLike((leftTupleFields ++ rightTupleFields).toSeq: _*)
  }
}
class HashJoinProbeOpExec[K](
    probeAttributeName: String,
    joinType: JoinType
) extends OperatorExecutor {
  var currentTuple: Tuple = _

  var buildTableHashMap: mutable.HashMap[K, (ListBuffer[Tuple], Boolean)] = _

  override def processTuple(
      tuple: Either[Tuple, InputExhausted],
      port: Int
  ): Iterator[TupleLike] =
    tuple match {
      case Left(tuple) if port == 0 =>
        // Load build hash map
        buildTableHashMap(tuple.getField("key")) = (tuple.getField("value"), false)
        Iterator.empty

      case Left(tuple) =>
        // Probe phase
        val key = tuple.getField(probeAttributeName).asInstanceOf[K]
        val (matchedTuples, joined) =
          buildTableHashMap.getOrElse(key, (new ListBuffer[Tuple](), false))

        if (matchedTuples.nonEmpty) {
          // Join match found
          buildTableHashMap.put(key, (matchedTuples, true))
          performJoin(tuple, matchedTuples)
        } else if (joinType == JoinType.RIGHT_OUTER || joinType == JoinType.FULL_OUTER) {
          // Handle right and full outer joins without a match
          performRightAntiJoin(tuple)
        } else {
          // No match found
          Iterator.empty
        }

      case Right(_)
          if port != 0 && (joinType == JoinType.LEFT_OUTER || joinType == JoinType.FULL_OUTER) =>
        // Handle left and full outer joins after input is exhausted
        performLeftAntiJoin

      case _ =>
        // Default case for all other conditions
        Iterator.empty
    }

  private def performLeftAntiJoin: Iterator[TupleLike] = {
    buildTableHashMap.valuesIterator
      .collect { case (tuples: ListBuffer[Tuple], joined: Boolean) if !joined => tuples }
      .flatMap { tuples =>
        tuples.map { tuple =>
          TupleLike(
            tuple.getSchema.getAttributeNames
              .map(attributeName => attributeName -> tuple.getField(attributeName))
              .toSeq: _*
          )
        }
      }
  }

  private def performJoin(
      probeTuple: Tuple,
      matchedTuples: ListBuffer[Tuple]
  ): Iterator[TupleLike] = {
    matchedTuples.iterator.map { buildTuple =>
      JoinUtils.joinTuples(buildTuple, probeTuple, skipAttributeName = Some(probeAttributeName))
    }
  }

  private def performRightAntiJoin(tuple: Tuple): Iterator[TupleLike] =
    Iterator(
      TupleLike(
        tuple.getSchema.getAttributeNames
          .map(attributeName => attributeName -> tuple.getField(attributeName))
          .toSeq: _*
      )
    )

  override def open(): Unit = {
    buildTableHashMap = new mutable.HashMap[K, (mutable.ListBuffer[Tuple], Boolean)]()
  }

  override def close(): Unit = {
    buildTableHashMap.clear()
  }

}
