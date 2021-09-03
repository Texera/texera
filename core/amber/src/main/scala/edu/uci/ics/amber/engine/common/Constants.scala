package edu.uci.ics.amber.engine.common

import scala.concurrent.duration._

object Constants {
  val defaultBatchSize: Int = AmberUtils.amberConfig.getInt("constants.default-batch-size")
  // time interval for logging queue sizes
  val loggingQueueSizeInterval: Int =
    AmberUtils.amberConfig.getInt("constants.logging-queue-size-interval")

  // Non constants: TODO: move out from Constants
  var numWorkerPerNode: Int = AmberUtils.amberConfig.getInt("constants.num-worker-per-node")
  var dataVolumePerNode: Int = AmberUtils.amberConfig.getInt("constants.data-volume-per-node")
  var currentWorkerNum = 0
  var currentDataSetNum = 0
  var masterNodeAddr: Option[String] = None
  var defaultTau: FiniteDuration = 10.milliseconds

  var storage: String = AmberUtils.amberConfig.getString("constants.storage")
  var mongodbUrl: String = AmberUtils.amberConfig.getString("constants.mongodb-url")
  var mongodbDatabaseName: String =
    AmberUtils.amberConfig.getString("constants.mongodb-database-name")
}
