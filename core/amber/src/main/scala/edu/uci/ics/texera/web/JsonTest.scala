package edu.uci.ics.texera.web

import edu.uci.ics.amber.engine.common.{AmberRuntime, SerializedState}

import scala.collection.mutable

object JsonTest {

  def main(args: Array[String]): Unit = {
    AmberRuntime.startActorWorker(None)
    val testObjs = Array(
      Map(1 -> "123", 3 -> "1231234"),
      mutable.HashMap[String, Any]("name" -> "peter", "mail" -> "peter@uci.edu", "grade" -> 4.0),
      Array(1,2,3, 4.0, "good", "bad", 8)
    )
    testObjs.foreach{
      obj =>
        val strRepr = SerializedState.fromObjectToString(obj)
        val objRepr = SerializedState.stringToObject(strRepr)
        objRepr match {
          case value: Array[_] =>
            println(value.mkString(","))
          case _ =>
            println(objRepr)
        }
    }
  }
}

class JsonTest {}
