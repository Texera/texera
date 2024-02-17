package edu.uci.ics.texera.web.service

import com.google.protobuf.timestamp.Timestamp
import com.typesafe.scalalogging.LazyLogging
import edu.uci.ics.amber.engine.architecture.controller.Controller.WorkflowRecoveryStatus
import edu.uci.ics.amber.engine.architecture.controller.ControllerEvent.{
  WorkerAssignmentUpdate,
  WorkflowCompleted,
  WorkflowStatusUpdate
}
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.FatalErrorHandler.FatalError
import edu.uci.ics.amber.engine.common.{AmberConfig, VirtualIdentityUtils}
import edu.uci.ics.amber.engine.common.client.AmberClient
import edu.uci.ics.amber.error.ErrorUtils.getStackTraceWithAllCauses
import edu.uci.ics.texera.Utils
import edu.uci.ics.texera.Utils.maptoStatusCode
import edu.uci.ics.texera.web.model.jooq.generated.tables.pojos.WorkflowRuntimeStatistics
import edu.uci.ics.texera.web.model.jooq.generated.tables.daos.WorkflowRuntimeStatisticsDao
import edu.uci.ics.texera.web.{SqlServer, SubscriptionManager}
import edu.uci.ics.texera.web.model.websocket.event.{
  ExecutionDurationUpdateEvent,
  OperatorStatistics,
  OperatorStatisticsUpdateEvent,
  WorkerAssignmentUpdateEvent
}
import edu.uci.ics.texera.web.storage.ExecutionStateStore
import edu.uci.ics.texera.web.storage.ExecutionStateStore.updateWorkflowState
import edu.uci.ics.texera.web.workflowruntimestate.FatalErrorType.EXECUTION_FAILURE
import edu.uci.ics.texera.web.workflowruntimestate.{
  OperatorRuntimeStats,
  OperatorWorkerMapping,
  WorkflowAggregatedState,
  WorkflowFatalError
}
import edu.uci.ics.texera.web.workflowruntimestate.WorkflowAggregatedState.{COMPLETED, FAILED}

import java.time.Instant
import edu.uci.ics.texera.workflow.common.WorkflowContext
import org.jooq.types.{UInteger, ULong}

import java.util

class ExecutionStatsService(
    client: AmberClient,
    stateStore: ExecutionStateStore,
    workflowContext: WorkflowContext
) extends SubscriptionManager
    with LazyLogging {
  final private lazy val context = SqlServer.createDSLContext()
  private val workflowRuntimeStatisticsDao = new WorkflowRuntimeStatisticsDao(context.configuration)

  registerCallbacks()

  addSubscription(
    stateStore.statsStore.registerDiffHandler((oldState, newState) => {
      if (AmberConfig.isUserSystemEnabled) {
        val defaultStats =
          OperatorRuntimeStats(WorkflowAggregatedState.UNINITIALIZED, 0, 0, 0, 0, 0, 0)

        var oldStateInfo = oldState.operatorInfo
        var newStateInfo = newState.operatorInfo

        // Find keys present in newState.operatorInfo but not in oldState.operatorInfo
        val newKeys = newState.operatorInfo.keys.toSet diff oldState.operatorInfo.keys.toSet
        for (key <- newKeys) {
          oldStateInfo = oldStateInfo + (key -> defaultStats)
        }

        // Find keys present in oldState.operatorInfo but not in newState.operatorInfo
        val oldKeys = oldState.operatorInfo.keys.toSet diff newState.operatorInfo.keys.toSet
        for (key <- oldKeys) {
          newStateInfo = newStateInfo + (key -> oldState.operatorInfo(key))
        }

        val result = newStateInfo.keys.map { key =>
          val newStats = newStateInfo(key)
          val oldStats = oldStateInfo(key)
          val res = OperatorRuntimeStats(
            newStats.state,
            newStats.inputCount - oldStats.inputCount,
            newStats.outputCount - oldStats.outputCount,
            newStats.numWorkers,
            newStats.dataProcessingTime - oldStats.dataProcessingTime,
            newStats.controlProcessingTime - oldStats.controlProcessingTime,
            newStats.idleTime - oldStats.idleTime
          )
          (key, res)
        }.toMap

        storeRuntimeStatistics(result)
      }
      // Update operator stats if any operator updates its stat
      if (newState.operatorInfo.toSet != oldState.operatorInfo.toSet) {
        Iterable(
          OperatorStatisticsUpdateEvent(newState.operatorInfo.collect {
            case x =>
              val stats = x._2
              val res = OperatorStatistics(
                Utils.aggregatedStateToString(stats.state),
                stats.inputCount,
                stats.outputCount,
                stats.numWorkers,
                stats.dataProcessingTime,
                stats.controlProcessingTime,
                stats.idleTime
              )
              (x._1, res)
          })
        )
      } else {
        Iterable.empty
      }
    })
  )

  addSubscription(
    stateStore.statsStore.registerDiffHandler((oldState, newState) => {
      // update operators' workers.
      if (newState.operatorWorkerMapping != oldState.operatorWorkerMapping) {
        newState.operatorWorkerMapping
          .map { opToWorkers =>
            WorkerAssignmentUpdateEvent(opToWorkers.operatorId, opToWorkers.workerIds)
          }
      } else {
        Iterable()
      }
    })
  )

  addSubscription(
    stateStore.statsStore.registerDiffHandler((oldState, newState) => {
      // update execution duration.
      if (
        newState.startTimeStamp != oldState.startTimeStamp || newState.endTimeStamp != oldState.endTimeStamp
      ) {
        if (newState.endTimeStamp != 0) {
          Iterable(
            ExecutionDurationUpdateEvent(
              newState.endTimeStamp - newState.startTimeStamp,
              isRunning = false
            )
          )
        } else {
          val currentTime = System.currentTimeMillis()
          Iterable(
            ExecutionDurationUpdateEvent(currentTime - newState.startTimeStamp, isRunning = true)
          )
        }
      } else {
        Iterable()
      }
    })
  )

  private[this] def registerCallbacks(): Unit = {
    registerCallbackOnWorkflowStatusUpdate()
    registerCallbackOnWorkerAssignedUpdate()
    registerCallbackOnWorkflowRecoveryUpdate()
    registerCallbackOnWorkflowComplete()
    registerCallbackOnFatalError()
  }

  private[this] def registerCallbackOnWorkflowStatusUpdate(): Unit = {
    addSubscription(
      client
        .registerCallback[WorkflowStatusUpdate]((evt: WorkflowStatusUpdate) => {
          stateStore.statsStore.updateState { statsStore =>
            statsStore.withOperatorInfo(evt.operatorStatistics)
          }
        })
    )
  }

  private def storeRuntimeStatistics(
      operatorStatistics: scala.collection.immutable.Map[String, OperatorRuntimeStats]
  ): Unit = {
    // Add a try-catch to not produce an error when "workflow_runtime_statistics" table does not exist in MySQL
    try {
      val list: util.ArrayList[WorkflowRuntimeStatistics] =
        new util.ArrayList[WorkflowRuntimeStatistics]()
      for ((operatorId, stat) <- operatorStatistics) {
        val execution = new WorkflowRuntimeStatistics()
        execution.setWorkflowId(UInteger.valueOf(workflowContext.workflowId.id))
        execution.setExecutionId(UInteger.valueOf(workflowContext.executionId.id))
        execution.setOperatorId(operatorId)
        execution.setInputTupleCnt(UInteger.valueOf(stat.inputCount))
        execution.setOutputTupleCnt(UInteger.valueOf(stat.outputCount))
        execution.setStatus(maptoStatusCode(stat.state))
        execution.setDataProcessingTime(ULong.valueOf(stat.dataProcessingTime))
        execution.setControlProcessingTime(ULong.valueOf(stat.controlProcessingTime))
        execution.setIdleTime(ULong.valueOf(stat.idleTime))
        execution.setNumWorkers(UInteger.valueOf(stat.numWorkers))
        list.add(execution)
      }
      workflowRuntimeStatisticsDao.insert(list)
    } catch {
      case err: Throwable => logger.error("error occurred when storing runtime statistics", err)
    }
  }

  private[this] def registerCallbackOnWorkerAssignedUpdate(): Unit = {
    addSubscription(
      client
        .registerCallback[WorkerAssignmentUpdate]((evt: WorkerAssignmentUpdate) => {
          stateStore.statsStore.updateState { statsStore =>
            statsStore.withOperatorWorkerMapping(
              evt.workerMapping
                .map({
                  case (opId, workerIds) => OperatorWorkerMapping(opId, workerIds.toSeq)
                })
                .toSeq
            )
          }
        })
    )
  }

  private[this] def registerCallbackOnWorkflowRecoveryUpdate(): Unit = {
    addSubscription(
      client
        .registerCallback[WorkflowRecoveryStatus]((evt: WorkflowRecoveryStatus) => {
          stateStore.metadataStore.updateState { metadataStore =>
            metadataStore.withIsRecovering(evt.isRecovering)
          }
        })
    )
  }

  private[this] def registerCallbackOnWorkflowComplete(): Unit = {
    addSubscription(
      client
        .registerCallback[WorkflowCompleted]((evt: WorkflowCompleted) => {
          client.shutdown()
          stateStore.statsStore.updateState(stats =>
            stats.withEndTimeStamp(System.currentTimeMillis())
          )
          stateStore.metadataStore.updateState(metadataStore =>
            updateWorkflowState(COMPLETED, metadataStore)
          )
        })
    )
  }

  private[this] def registerCallbackOnFatalError(): Unit = {
    addSubscription(
      client
        .registerCallback[FatalError]((evt: FatalError) => {
          client.shutdown()
          var operatorId = "unknown operator"
          var workerId = ""
          if (evt.fromActor.isDefined) {
            operatorId = VirtualIdentityUtils.getPhysicalOpId(evt.fromActor.get).logicalOpId.id
            workerId = evt.fromActor.get.name
          }
          stateStore.statsStore.updateState(stats =>
            stats.withEndTimeStamp(System.currentTimeMillis())
          )
          stateStore.metadataStore.updateState { metadataStore =>
            logger.error("error occurred in execution", evt.e)
            updateWorkflowState(FAILED, metadataStore).addFatalErrors(
              WorkflowFatalError(
                EXECUTION_FAILURE,
                Timestamp(Instant.now),
                evt.e.toString,
                getStackTraceWithAllCauses(evt.e),
                operatorId,
                workerId
              )
            )
          }
        })
    )
  }
}
