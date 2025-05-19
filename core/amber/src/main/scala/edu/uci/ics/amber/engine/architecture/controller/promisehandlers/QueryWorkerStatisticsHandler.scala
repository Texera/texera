/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package edu.uci.ics.amber.engine.architecture.controller.promisehandlers

import com.twitter.util.Future
import edu.uci.ics.amber.engine.architecture.controller.{
  ControllerAsyncRPCHandlerInitializer,
  ExecutionStatsUpdate
}
import edu.uci.ics.amber.engine.architecture.rpc.controlcommands.{
  AsyncRPCContext,
  EmptyRequest,
  QueryStatisticsRequest
}
import edu.uci.ics.amber.engine.architecture.rpc.controlreturns.EmptyReturn
import edu.uci.ics.amber.util.VirtualIdentityUtils

/** Get statistics from all the workers
  *
  * possible sender: controller(by statusUpdateAskHandle)
  */
trait QueryWorkerStatisticsHandler {
  this: ControllerAsyncRPCHandlerInitializer =>

  override def controllerInitiateQueryStatistics(
      msg: QueryStatisticsRequest,
      ctx: AsyncRPCContext
  ): Future[EmptyReturn] = {
    // Determine which workers to query
    val workers = if (msg.filterByWorkers.nonEmpty) {
      msg.filterByWorkers
    } else {
      cp.workflowExecution.getAllRegionExecutions
        .flatMap(_.getAllOperatorExecutions.map(_._2))
        .flatMap(_.getWorkerIds)
    }

    // For each worker, query both statistics and table profile in parallel
    val requests = workers.map { workerId =>
      val workerExecution =
        cp.workflowExecution
          .getLatestOperatorExecution(VirtualIdentityUtils.getPhysicalOpId(workerId))
          .getWorkerExecution(workerId)

      val statFut = workerInterface.queryStatistics(EmptyRequest(), workerId)
      val profileFut = workerInterface.queryTableProfile(EmptyRequest(), workerId)

      for {
        statResp <- statFut
        profileResp <- profileFut
      } yield {
        workerExecution.setState(statResp.metrics.workerState)
        workerExecution.setStats(statResp.metrics.workerStatistics)
        workerExecution.setTableProfile(profileResp.tableProfiles)
      }
    }

    Future
      .collect(requests.toSeq)
      .map(_ =>
        sendToClient(
          ExecutionStatsUpdate(
            cp.workflowExecution.getAllRegionExecutionsStats,
            cp.workflowExecution.getAllRegionExecutionTableProfiles
          )
        )
      )

    EmptyReturn()
  }

}
