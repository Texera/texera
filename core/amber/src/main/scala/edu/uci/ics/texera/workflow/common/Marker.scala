package edu.uci.ics.texera.workflow.common

import scala.collection.mutable

sealed trait Marker {}

final case class EndOfUpstream() extends Marker
final case class EndOfIteration() extends Marker
final case class State(key: String, value: Any) extends Marker
