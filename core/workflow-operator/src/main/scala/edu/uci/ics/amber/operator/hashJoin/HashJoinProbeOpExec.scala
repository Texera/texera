package edu.uci.ics.amber.operator.hashJoin

import edu.uci.ics.amber.core.executor.OperatorExecutor
import edu.uci.ics.amber.core.tuple.{Tuple, TupleLike}
import edu.uci.ics.amber.operator.hashJoin.HashJoinOpDesc.HASH_JOIN_INTERNAL_KEY_NAME
import edu.uci.ics.amber.util.JSONUtils.objectMapper

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

    TupleLike(leftTupleFields ++ rightTupleFields)
  }
}

class HashJoinProbeOpExec[K](
    descString: String
) extends OperatorExecutor {

  private val desc: HashJoinOpDesc[K] =
    objectMapper.readValue(descString, classOf[HashJoinOpDesc[K]])
  var buildTableHashMap: mutable.HashMap[K, (ListBuffer[Tuple], Boolean)] = _

  override def open(): Unit = {
    buildTableHashMap = new mutable.HashMap[K, (mutable.ListBuffer[Tuple], Boolean)]()
  }

  override def close(): Unit = {
    buildTableHashMap.clear()
  }

  override def processTuple(tuple: Tuple, port: Int): Iterator[TupleLike] =
    if (port == 0) {
      // Load build hash map
      val key = tuple.getField[K](HASH_JOIN_INTERNAL_KEY_NAME)
      buildTableHashMap.getOrElseUpdate(key, (new ListBuffer[Tuple](), false))._1 += tuple
        .getPartialTuple(
          tuple.getSchema.getAttributeNames.filterNot(n => n == HASH_JOIN_INTERNAL_KEY_NAME)
        )
      Iterator.empty
    } else {
      // Probe phase
      val key = tuple.getField(desc.probeAttributeName).asInstanceOf[K]
      val (matchedTuples, joined) =
        buildTableHashMap.getOrElse(key, (new ListBuffer[Tuple](), false))

      if (matchedTuples.nonEmpty) {
        // Join match found
        buildTableHashMap.put(key, (matchedTuples, true))
        performJoin(tuple, matchedTuples)
      } else if (desc.joinType == JoinType.RIGHT_OUTER || desc.joinType == JoinType.FULL_OUTER) {
        // Handle right and full outer joins without a match
        performRightAntiJoin(tuple)
      } else {
        // No match found
        Iterator.empty
      }
    }

  override def onFinish(port: Int): Iterator[TupleLike] = {
    if (
      port == 1 && (desc.joinType == JoinType.LEFT_OUTER || desc.joinType == JoinType.FULL_OUTER)
    ) {
      // Handle left and full outer joins after input is exhausted
      performLeftAntiJoin
    } else {
      Iterator.empty
    }

  }

  private def performLeftAntiJoin: Iterator[TupleLike] = {
    buildTableHashMap.valuesIterator
      .collect { case (tuples: ListBuffer[Tuple], joined: Boolean) if !joined => tuples }
      .flatMap { tuples =>
        tuples.map { tuple =>
          TupleLike(
            tuple.getSchema.getAttributeNames
              .map(attributeName => attributeName -> tuple.getField(attributeName)): _*
          )
        }
      }
  }

  private def performJoin(
      probeTuple: Tuple,
      matchedTuples: ListBuffer[Tuple]
  ): Iterator[TupleLike] = {
    matchedTuples.iterator.map { buildTuple =>
      JoinUtils.joinTuples(
        buildTuple,
        probeTuple,
        skipAttributeName = Some(desc.probeAttributeName)
      )
    }
  }

  private def performRightAntiJoin(tuple: Tuple): Iterator[TupleLike] =
    Iterator(
      TupleLike(
        tuple.getSchema.getAttributeNames
          .map(attributeName => attributeName -> tuple.getField(attributeName)): _*
      )
    )
}
