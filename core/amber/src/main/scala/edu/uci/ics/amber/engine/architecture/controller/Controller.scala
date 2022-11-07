package edu.uci.ics.amber.engine.architecture.controller

import akka.actor.{ActorRef, Address, Cancellable, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.softwaremill.macwire.wire
import com.twitter.util.Future
import edu.uci.ics.amber.clustering.ClusterListener.GetAvailableNodeAddresses
import edu.uci.ics.amber.engine.architecture.common.WorkflowActor
import edu.uci.ics.amber.engine.architecture.controller.ControllerEvent.WorkflowStatusUpdate
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.FatalErrorHandler.FatalError
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.LinkWorkersHandler.LinkWorkers
import edu.uci.ics.amber.engine.architecture.linksemantics.LinkStrategy
import edu.uci.ics.amber.engine.architecture.logging.{
  DeterminantLogger,
  InMemDeterminant,
  ProcessControlMessage
}
import edu.uci.ics.amber.engine.architecture.messaginglayer.NetworkCommunicationActor.{
  NetworkMessage,
  NetworkSenderActorRef,
  RegisterActorRef
}
import edu.uci.ics.amber.engine.architecture.messaginglayer.NetworkInputPort
import edu.uci.ics.amber.engine.architecture.pythonworker.promisehandlers.InitializeOperatorLogicHandler.InitializeOperatorLogic
import edu.uci.ics.amber.engine.architecture.recovery.FIFOStateRecoveryManager
import edu.uci.ics.amber.engine.architecture.scheduling.{
  WorkflowPipelinedRegionsBuilder,
  WorkflowScheduler
}
import edu.uci.ics.amber.engine.architecture.worker.promisehandlers.OpenOperatorHandler.OpenOperator
import edu.uci.ics.amber.engine.architecture.worker.statistics.WorkerState.READY
import edu.uci.ics.amber.engine.common.{Constants, ISourceOperatorExecutor}
import edu.uci.ics.amber.engine.common.{AmberUtils, Constants, ISourceOperatorExecutor}
import edu.uci.ics.amber.engine.common.amberexception.WorkflowRuntimeException
import edu.uci.ics.amber.engine.common.ambermessage.{ControlPayload, WorkflowControlMessage}
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCClient.{ControlInvocation, ReturnInvocation}
import edu.uci.ics.amber.engine.common.virtualidentity.ActorVirtualIdentity
import edu.uci.ics.amber.engine.common.virtualidentity.util.{CLIENT, CONTROLLER}
import edu.uci.ics.amber.error.ErrorUtils.safely
import edu.uci.ics.texera.workflow.operators.udf.pythonV2.PythonUDFOpExecV2

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

object ControllerConfig {
  def default: ControllerConfig =
    ControllerConfig(
      monitoringIntervalMs = Option(Constants.monitoringIntervalInMs),
      skewDetectionIntervalMs = Option(Constants.reshapeSkewDetectionIntervalInMs),
      statusUpdateIntervalMs =
        Option(AmberUtils.amberConfig.getLong("constants.status-update-interval"))
    )
}

final case class ControllerConfig(
    monitoringIntervalMs: Option[Long],
    skewDetectionIntervalMs: Option[Long],
    statusUpdateIntervalMs: Option[Long]
)

object Controller {

  def props(
      workflow: Workflow,
      controllerConfig: ControllerConfig = ControllerConfig.default,
      parentNetworkCommunicationActorRef: NetworkSenderActorRef = NetworkSenderActorRef()
  ): Props =
    Props(
      new Controller(
        workflow,
        controllerConfig,
        parentNetworkCommunicationActorRef
      )
    )
}

class Controller(
    val workflow: Workflow,
    val controllerConfig: ControllerConfig,
    parentNetworkCommunicationActorRef: NetworkSenderActorRef
) extends WorkflowActor(CONTROLLER, parentNetworkCommunicationActorRef) {
  lazy val controlInputPort: NetworkInputPort[ControlPayload] =
    new NetworkInputPort[ControlPayload](this.actorId, this.handleControlPayloadWithTryCatch)
  implicit val ec: ExecutionContext = context.dispatcher
  implicit val timeout: Timeout = 5.seconds
  var statusUpdateAskHandle: Cancellable = _

  override def getLogName: String = "WF" + workflow.getWorkflowId().id + "-CONTROLLER"
  val determinantLogger: DeterminantLogger = logManager.getDeterminantLogger

  def availableNodes: Array[Address] =
    Await
      .result(context.actorSelection("/user/cluster-info") ? GetAvailableNodeAddresses, 5.seconds)
      .asInstanceOf[Array[Address]]

  val workflowScheduler =
    new WorkflowScheduler(
      availableNodes,
      networkCommunicationActor,
      context,
      asyncRPCClient,
      logger,
      workflow
    )

  val rpcHandlerInitializer: ControllerAsyncRPCHandlerInitializer =
    wire[ControllerAsyncRPCHandlerInitializer]

  // register controller itself and client
  networkCommunicationActor.waitUntil(RegisterActorRef(CONTROLLER, self))
  networkCommunicationActor.waitUntil(RegisterActorRef(CLIENT, context.parent))

  def running: Receive = {
    acceptDirectInvocations orElse {
      case NetworkMessage(id, WorkflowControlMessage(from, seqNum, payload)) =>
        controlInputPort.handleMessage(
          this.sender(),
          Constants.unprocessedBatchesCreditLimitPerSender, // Controller is assumed to have enough credits
          id,
          from,
          seqNum,
          payload
        )
      case other =>
        logger.info(s"unhandled message: $other")
    }
  }

  def acceptDirectInvocations: Receive = {
    case invocation: ControlInvocation =>
      this.handleControlPayloadWithTryCatch(CLIENT, invocation)
  }

  def handleControlPayloadWithTryCatch(
      from: ActorVirtualIdentity,
      controlPayload: ControlPayload
  ): Unit = {
    determinantLogger.logDeterminant(ProcessControlMessage(controlPayload, from))
    try {
      controlPayload match {
        // use control input port to pass control messages
        case invocation: ControlInvocation =>
          assert(from.isInstanceOf[ActorVirtualIdentity])
          asyncRPCServer.logControlInvocation(invocation, from)
          asyncRPCServer.receive(invocation, from)
        case ret: ReturnInvocation =>
          asyncRPCClient.logControlReply(ret, from)
          asyncRPCClient.fulfillPromise(ret)
        case other =>
          throw new WorkflowRuntimeException(s"unhandled control message: $other")
      }
    } catch safely {
      case err =>
        // report error to frontend
        asyncRPCClient.sendToClient(FatalError(err))
        // re-throw the error to fail the actor
        throw err
    }
  }

  def recovering: Receive = {
    case d: InMemDeterminant =>
      d match {
        case ProcessControlMessage(controlPayload, from) =>
          handleControlPayloadWithTryCatch(from, controlPayload)
          if (recoveryManager.replayCompleted()) {
            logManager.terminate()
            logStorage.swapTempLog()
            logManager.setupWriter(logStorage.getWriter(false))
            context.become(running)
          } else {
            val inMemDeterminant = recoveryManager.popDeterminant()
            self ! inMemDeterminant
          }
        case otherDeterminant =>
          throw new RuntimeException(
            "Controller cannot handle " + otherDeterminant + " during recovery!"
          )
      }
    case other =>
      logger.info("Ignore during recovery: " + other)
  }

  override def receive: Receive = {
    if (!recoveryManager.replayCompleted()) {
      val fifoStateRecoveryManager = new FIFOStateRecoveryManager(logStorage.getReader)
      val fifoState = fifoStateRecoveryManager.getFIFOState
      controlInputPort.overwriteFIFOState(fifoState)
      val inMemDeterminant = recoveryManager.popDeterminant()
      self ! inMemDeterminant
      recovering
    } else {
      running
    }
  }

  override def postStop(): Unit = {
    if (statusUpdateAskHandle != null) {
      statusUpdateAskHandle.cancel()
    }
    logManager.terminate()
    if (workflow.isCompleted) {
      logStorage.deleteLog()
    }
    logger.info("stopped!")
  }
}
