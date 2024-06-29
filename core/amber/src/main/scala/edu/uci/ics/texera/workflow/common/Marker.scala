package edu.uci.ics.texera.workflow.common

import edu.uci.ics.amber.engine.common.SerializedState
import edu.uci.ics.amber.engine.common.tuple.amber.SpecialTupleLike

import scala.collection.mutable

sealed trait Marker

final case class StartOfUpstream() extends Marker

final case class EndOfUpstream() extends Marker


final case class State() extends Marker {
  val list: mutable.Map[String, String] = mutable.HashMap()

  def add(key: String, value: Object): Unit = {
    list.put(key, SerializedState.fromObjectToString(value))
  }

  def get(key: String): Object = {
    //SerializedState.stringToObject(list.getOrElse(key, ""))
    list.get(key) match {
      case Some(value) => SerializedState.stringToObject(value)
      case None => null
    }
  }
}

