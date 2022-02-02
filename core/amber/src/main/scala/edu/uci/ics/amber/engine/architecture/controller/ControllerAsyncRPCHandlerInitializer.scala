package edu.uci.ics.amber.engine.architecture.controller

import akka.actor.{ActorContext, Cancellable}
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.QueryWorkerStatisticsHandler.ControllerInitiateQueryStatistics
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.MonitoringHandler.ControllerInitiateMonitoring
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.SkewDetectionHandler.ControllerInitiateSkewDetection
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers._
import edu.uci.ics.amber.engine.architecture.messaginglayer.NetworkOutputPort
import edu.uci.ics.amber.engine.common.{AmberLogging, Constants}
import edu.uci.ics.amber.engine.common.ambermessage.ControlPayload
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCClient.ControlInvocation
import edu.uci.ics.amber.engine.common.rpc.{
  AsyncRPCClient,
  AsyncRPCHandlerInitializer,
  AsyncRPCServer
}
import edu.uci.ics.amber.engine.common.virtualidentity.ActorVirtualIdentity

import scala.concurrent.duration.{DurationInt, FiniteDuration, MILLISECONDS}

class ControllerAsyncRPCHandlerInitializer(
    val actorContext: ActorContext,
    val actorId: ActorVirtualIdentity,
    val controlOutputPort: NetworkOutputPort[ControlPayload],
    val workflow: Workflow,
    val controllerConfig: ControllerConfig,
    source: AsyncRPCClient,
    receiver: AsyncRPCServer
) extends AsyncRPCHandlerInitializer(source, receiver)
    with AmberLogging
    with LinkWorkersHandler
    with AssignBreakpointHandler
    with WorkerExecutionCompletedHandler
    with WorkerExecutionStartedHandler
    with LocalBreakpointTriggeredHandler
    with LocalOperatorExceptionHandler
    with PauseHandler
    with QueryWorkerStatisticsHandler
    with ResumeHandler
    with StartWorkflowHandler
    with LinkCompletedHandler
    with FatalErrorHandler
    with PythonPrintHandler
    with RetryWorkflowHandler
    with ModifyLogicHandler
    with EvaluatePythonExpressionHandler
    with MonitoringHandler
    with SkewDetectionHandler {

  var statusUpdateAskHandle: Option[Cancellable] = None
  var monitoringHandle: Option[Cancellable] = None
  var skewDetectionHandle: Option[Cancellable] = None

  def enableStatusUpdate(): Unit = {
    if (controllerConfig.statusUpdateIntervalMs.nonEmpty && statusUpdateAskHandle.isEmpty) {
      statusUpdateAskHandle = Option(
        actorContext.system.scheduler.scheduleAtFixedRate(
          0.milliseconds,
          FiniteDuration.apply(controllerConfig.statusUpdateIntervalMs.get, MILLISECONDS),
          actorContext.self,
          ControlInvocation(
            AsyncRPCClient.IgnoreReplyAndDoNotLog,
            ControllerInitiateQueryStatistics()
          )
        )(actorContext.dispatcher)
      )
    }
  }

  def enableMonitoring(): Unit = {
    if (
      Constants.monitoringEnabled && controllerConfig.monitoringIntervalMs.nonEmpty && monitoringHandle.isEmpty
    ) {
      monitoringHandle = Option(
        actorContext.system.scheduler.scheduleAtFixedRate(
          0.milliseconds,
          FiniteDuration.apply(controllerConfig.monitoringIntervalMs.get, MILLISECONDS),
          actorContext.self,
          ControlInvocation(
            AsyncRPCClient.IgnoreReplyAndDoNotLog,
            ControllerInitiateMonitoring()
          )
        )(actorContext.dispatcher)
      )
    }
  }

  def enableSkewHandling(): Unit = {
    if (
      Constants.reshapeSkewHandlingEnabled && controllerConfig.skewDetectionIntervalMs.nonEmpty && skewDetectionHandle.isEmpty
    ) {
      skewDetectionHandle = Option(
        actorContext.system.scheduler.scheduleAtFixedRate(
          5000.milliseconds,
          FiniteDuration.apply(controllerConfig.skewDetectionIntervalMs.get, MILLISECONDS),
          actorContext.self,
          ControlInvocation(
            AsyncRPCClient.IgnoreReplyAndDoNotLog,
            ControllerInitiateSkewDetection()
          )
        )(actorContext.dispatcher)
      )
    }
  }

  def disableStatusUpdate(): Unit = {
    if (statusUpdateAskHandle.nonEmpty) {
      statusUpdateAskHandle.get.cancel()
      statusUpdateAskHandle = Option.empty
    }
  }

  def disableMonitoring(): Unit = {
    if (monitoringHandle.nonEmpty) {
      monitoringHandle.get.cancel()
      monitoringHandle = Option.empty
    }
  }

  def disableSkewHandling(): Unit = {
    if (skewDetectionHandle.nonEmpty) {
      skewDetectionHandle.get.cancel()
      skewDetectionHandle = Option.empty
    }
  }

}
