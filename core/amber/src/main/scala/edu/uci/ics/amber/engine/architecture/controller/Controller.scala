package edu.uci.ics.amber.engine.architecture.controller

import akka.actor.{ActorRef, Address, Cancellable, PoisonPill, Props}
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
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.KillWorkflowHandler.KillWorkflow
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.LinkWorkersHandler.LinkWorkers
import edu.uci.ics.amber.engine.architecture.linksemantics.LinkStrategy
import edu.uci.ics.amber.engine.architecture.messaginglayer.NetworkCommunicationActor.{
  NetworkMessage,
  RegisterActorRef
}
import edu.uci.ics.amber.engine.architecture.messaginglayer.NetworkInputPort
import edu.uci.ics.amber.engine.architecture.pythonworker.promisehandlers.InitializeOperatorLogicHandler.InitializeOperatorLogic
import edu.uci.ics.amber.engine.architecture.worker.statistics.WorkerState.READY
import edu.uci.ics.amber.engine.common.ISourceOperatorExecutor
import edu.uci.ics.amber.engine.common.amberexception.WorkflowRuntimeException
import edu.uci.ics.amber.engine.common.ambermessage.{ControlPayload, WorkflowControlMessage}
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCClient.{ControlInvocation, ReturnInvocation}
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCServer
import edu.uci.ics.amber.engine.common.virtualidentity.util.CONTROLLER
import edu.uci.ics.amber.engine.common.virtualidentity.{ActorVirtualIdentity, WorkflowIdentity}
import edu.uci.ics.amber.error.ErrorUtils.safely
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
      workflow: Workflow,
      eventListener: ControllerEventListener,
      controllerConfig: ControllerConfig = ControllerConfig.default,
      parentNetworkCommunicationActorRef: ActorRef = null
  ): Props =
    Props(
      new Controller(
        workflow,
        eventListener,
        controllerConfig,
        parentNetworkCommunicationActorRef
      )
    )
}

class Controller(
    val workflow: Workflow,
    val eventListener: ControllerEventListener = ControllerEventListener(),
    val controllerConfig: ControllerConfig,
    parentNetworkCommunicationActorRef: ActorRef
) extends WorkflowActor(CONTROLLER, parentNetworkCommunicationActorRef) {
  lazy val controlInputPort: NetworkInputPort[ControlPayload] =
    new NetworkInputPort[ControlPayload](this.actorId, this.handleControlPayloadWithTryCatch)
  implicit val ec: ExecutionContext = context.dispatcher
  implicit val timeout: Timeout = 5.seconds
  val rpcHandlerInitializer: ControllerAsyncRPCHandlerInitializer =
    wire[ControllerAsyncRPCHandlerInitializer]
  var statusUpdateAskHandle: Cancellable = _

  def availableNodes: Array[Address] =
    Await
      .result(context.actorSelection("/user/cluster-info") ? GetAvailableNodeAddresses, 5.seconds)
      .asInstanceOf[Array[Address]]

  // register controller itself
  networkCommunicationActor ! RegisterActorRef(CONTROLLER, self)

  // build whole workflow
  workflow.build(availableNodes, networkCommunicationActor, context)

  // bring all workers into a ready state
  prepareWorkers()

  def prepareWorkers(): Future[Seq[Unit]] = {

    // initialize python udf code
    val initializeOperatorLogicRequests: Seq[Future[Unit]] =
      workflow.getPythonWorkerToOperatorExec.map {
        case (workerId: ActorVirtualIdentity, pythonOperatorExec: PythonUDFOpExecV2) =>
          asyncRPCClient.send(
            InitializeOperatorLogic(
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
        initializeOperatorLogicRequests ++ activateLinkRequests
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
      .onFailure((err: Throwable) => {
        logger.error("Failure when sending Python UDF code", err)
        // report error to frontend
        if (eventListener.workflowExecutionErrorListener != null) {
          eventListener.workflowExecutionErrorListener.apply(ErrorOccurred(err))
        }

        // shutdown the system
        asyncRPCServer.execute(KillWorkflow(), CONTROLLER)
      })
  }

  def running: Receive = {
    acceptDirectInvocations orElse {
      case NetworkMessage(id, WorkflowControlMessage(from, seqNum, payload)) =>
        controlInputPort.handleMessage(this.sender(), id, from, seqNum, payload)
      case other =>
        logger.info(s"unhandled message: $other")
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
          throw new WorkflowRuntimeException(s"unhandled control message: $other")
      }
    } catch safely {
      case err =>
        // report error to frontend
        if (eventListener.workflowExecutionErrorListener != null) {
          eventListener.workflowExecutionErrorListener.apply(ErrorOccurred(err))
        }

        // re-throw the error to fail the actor
        throw err
    }
  }

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
    logger.info("stopped!")
  }
}
