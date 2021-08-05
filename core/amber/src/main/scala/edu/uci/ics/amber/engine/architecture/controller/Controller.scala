package edu.uci.ics.amber.engine.architecture.controller

import akka.actor.{ActorRef, Address, Cancellable, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.softwaremill.macwire.wire
import com.twitter.util.Future
import edu.uci.ics.amber.clustering.ClusterListener.GetAvailableNodeAddresses
import edu.uci.ics.amber.engine.architecture.common.WorkflowActor
import edu.uci.ics.amber.engine.architecture.controller.ControllerEvent.{
  ErrorOccurred,
  WorkflowStatusUpdate
}
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.LinkWorkersHandler.LinkWorkers
import edu.uci.ics.amber.engine.architecture.linksemantics.LinkStrategy
import edu.uci.ics.amber.engine.architecture.messaginglayer.NetworkCommunicationActor.{
  NetworkMessage,
  RegisterActorRef
}
import edu.uci.ics.amber.engine.architecture.messaginglayer.NetworkInputPort
import edu.uci.ics.amber.engine.architecture.pythonworker.promisehandlers.SendPythonUdfHandler.SendPythonUdf
import edu.uci.ics.amber.engine.architecture.worker.statistics.WorkerState.READY
import edu.uci.ics.amber.engine.common.ISourceOperatorExecutor
import edu.uci.ics.amber.engine.common.ambermessage.{ControlPayload, WorkflowControlMessage}
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCClient.{ControlInvocation, ReturnInvocation}
import edu.uci.ics.amber.engine.common.virtualidentity.util.CONTROLLER
import edu.uci.ics.amber.engine.common.virtualidentity.{ActorVirtualIdentity, WorkflowIdentity}
import edu.uci.ics.amber.error.ErrorUtils.safely
import edu.uci.ics.amber.error.WorkflowRuntimeError
import edu.uci.ics.texera.workflow.operators.udf.pythonV2.PythonUDFOpExecV2

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

object ControllerConfig {
  def default: ControllerConfig =
    ControllerConfig(
      statusUpdateIntervalMs = Option(100),
      resultUpdateIntervalMs = Option(1000)
    )
}
final case class ControllerConfig(
    statusUpdateIntervalMs: Option[Long],
    resultUpdateIntervalMs: Option[Long]
)

object Controller {

  def props(
      id: WorkflowIdentity,
      workflow: Workflow,
      eventListener: ControllerEventListener,
      controllerConfig: ControllerConfig = ControllerConfig.default,
      parentNetworkCommunicationActorRef: ActorRef = null
  ): Props =
    Props(
      new Controller(
        id,
        workflow,
        eventListener,
        controllerConfig,
        parentNetworkCommunicationActorRef
      )
    )
}

class Controller(
    val id: WorkflowIdentity,
    val workflow: Workflow,
    val eventListener: ControllerEventListener = ControllerEventListener(),
    val controllerConfig: ControllerConfig,
    parentNetworkCommunicationActorRef: ActorRef
) extends WorkflowActor(CONTROLLER, parentNetworkCommunicationActorRef) {
  lazy val controlInputPort: NetworkInputPort[ControlPayload] =
    new NetworkInputPort[ControlPayload](this.logger, this.handleControlPayloadWithTryCatch)
  implicit val ec: ExecutionContext = context.dispatcher
  implicit val timeout: Timeout = 5.seconds
  val rpcHandlerInitializer: ControllerAsyncRPCHandlerInitializer =
    wire[ControllerAsyncRPCHandlerInitializer]
  var statusUpdateAskHandle: Cancellable = _

  logger.setErrorLogAction(errorLogAction)

  // register controller itself
  networkCommunicationActor ! RegisterActorRef(CONTROLLER, self)

  // build whole workflow
  workflow.build(availableNodes, networkCommunicationActor, context)

  // bring all workers into a ready state
  prepareWorkers()

  def prepareWorkers(): Future[Seq[Unit]] = {

    // send python udf code
    val sendPythonUdfRequests: Seq[Future[Unit]] = workflow.getPythonWorkerToOperatorExec.map {
      case (workerId: ActorVirtualIdentity, pythonOperatorExec: PythonUDFOpExecV2) =>
        asyncRPCClient.send(
          SendPythonUdf(
            pythonOperatorExec.getCode,
            pythonOperatorExec.isInstanceOf[ISourceOperatorExecutor]
          ),
          workerId
        )
    }.toSeq

    // activate all links
    val activateLinkRequests: Seq[Future[Unit]] =
      workflow.getAllLinks.map { link: LinkStrategy =>
        asyncRPCClient.send(
          LinkWorkers(link),
          CONTROLLER
        )
      }.toSeq

    Future
      .collect(
        sendPythonUdfRequests ++ activateLinkRequests
      )
      .onSuccess({ _ =>
        workflow.getAllOperators.foreach(_.setAllWorkerState(READY))
        if (eventListener.workflowStatusUpdateListener != null) {
          eventListener.workflowStatusUpdateListener
            .apply(WorkflowStatusUpdate(workflow.getWorkflowStatus))
        }
        // for testing, report ready state to parent
        context.parent ! ControllerState.Ready
        context.become(running)
        unstashAll()
      })
      .onFailure((x: Throwable) =>
        logger.logError(new WorkflowRuntimeError(x.getMessage, "PythonUDFV2", Map.empty))
      )
  }

  def running: Receive = {
    acceptDirectInvocations orElse {
      case NetworkMessage(id, WorkflowControlMessage(from, seqNum, payload)) =>
        controlInputPort.handleMessage(this.sender(), id, from, seqNum, payload)
      case other =>
        logger.logInfo(s"unhandled message: $other")
    }
  }

  def acceptDirectInvocations: Receive = {
    case invocation: ControlInvocation =>
      this.handleControlPayloadWithTryCatch(CONTROLLER, invocation)
  }

  def handleControlPayloadWithTryCatch(
      from: ActorVirtualIdentity,
      controlPayload: ControlPayload
  ): Unit = {
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
          logger.logError(
            WorkflowRuntimeError(
              s"unhandled control message: $other",
              "ControlInputPort",
              Map.empty
            )
          )
      }
    } catch safely {
      case e =>
        logger.logError(WorkflowRuntimeError(e, identifier.toString))
    }
  }

  def availableNodes: Array[Address] =
    Await
      .result(context.actorSelection("/user/cluster-info") ? GetAvailableNodeAddresses, 5.seconds)
      .asInstanceOf[Array[Address]]

  override def receive: Receive = initializing

  def initializing: Receive = {
    case NetworkMessage(id, WorkflowControlMessage(from, seqNum, payload: ReturnInvocation)) =>
      //process reply messages
      controlInputPort.handleMessage(this.sender(), id, from, seqNum, payload)
    case NetworkMessage(
          id,
          WorkflowControlMessage(CONTROLLER, seqNum, payload)
        ) =>
      //process control messages from self
      controlInputPort.handleMessage(
        this.sender(),
        id,
        CONTROLLER,
        seqNum,
        payload
      )
    case _ =>
      stash() //prevent other messages to be executed until initialized
  }

  override def postStop(): Unit = {
    if (statusUpdateAskHandle != null) {
      statusUpdateAskHandle.cancel()
    }
    logger.logInfo("stopped!")
  }

  private def errorLogAction(err: WorkflowRuntimeError): Unit = {
    if (eventListener.workflowExecutionErrorListener != null) {
      eventListener.workflowExecutionErrorListener.apply(ErrorOccurred(err))
    }
  }

}
