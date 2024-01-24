package edu.uci.ics.amber.engine.architecture.worker

import com.softwaremill.macwire.wire
import edu.uci.ics.amber.engine.architecture.common.AmberProcessor
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.ConsoleMessageHandler.ConsoleMessageTriggered
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.LinkCompletedHandler.LinkCompleted
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.WorkerExecutionCompletedHandler.WorkerExecutionCompleted
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.WorkerExecutionStartedHandler.WorkerStateUpdated
import edu.uci.ics.amber.engine.architecture.deploysemantics.PhysicalOp
import edu.uci.ics.amber.engine.architecture.deploysemantics.layer.{
  OpExecInitInfoWithCode,
  OpExecInitInfoWithFunc
}
import edu.uci.ics.amber.engine.architecture.logreplay.ReplayLogManager
import edu.uci.ics.amber.engine.architecture.messaginglayer.{OutputManager, WorkerTimerService}
import edu.uci.ics.amber.engine.architecture.scheduling.config.OperatorConfig
import edu.uci.ics.amber.engine.architecture.worker.DataProcessor.{
  DPOutputIterator,
  FinalizeLink,
  FinalizeOperator
}
import edu.uci.ics.amber.engine.architecture.worker.promisehandlers.PauseHandler.PauseWorker
import edu.uci.ics.amber.engine.architecture.worker.statistics.WorkerState.{
  COMPLETED,
  PAUSED,
  READY,
  RUNNING
}
import edu.uci.ics.amber.engine.common.ambermessage._
import edu.uci.ics.amber.engine.common.statetransition.WorkerStateManager
import edu.uci.ics.amber.engine.common.tuple.ITuple
import edu.uci.ics.amber.engine.common.virtualidentity.util.{CONTROLLER, SELF, SOURCE_STARTER_OP}
import edu.uci.ics.amber.engine.common.virtualidentity.{ActorVirtualIdentity, PhysicalOpIdentity}
import edu.uci.ics.amber.engine.common.workflow.{PhysicalLink, PortIdentity}
import edu.uci.ics.amber.engine.common.{IOperatorExecutor, InputExhausted, VirtualIdentityUtils}
import edu.uci.ics.amber.error.ErrorUtils.{mkConsoleMessage, safely}

import scala.collection.mutable

object DataProcessor {

  class SpecialDataTuple extends ITuple {
    override def length: Int = 0

    override def get(i: Int): Any = null

    override def toArray(): Array[Any] = Array.empty

    override def inMemSize: Long = 0
  }
  case class FinalizeLink(link: PhysicalLink) extends SpecialDataTuple
  case class FinalizeOperator() extends SpecialDataTuple

  class DPOutputIterator extends Iterator[(ITuple, Option[PortIdentity])] {
    val queue = new mutable.Queue[(ITuple, Option[PortIdentity])]
    @transient var outputIter: Iterator[(ITuple, Option[PortIdentity])] = Iterator.empty

    def setTupleOutput(outputIter: Iterator[(ITuple, Option[PortIdentity])]): Unit = {
      if (outputIter != null) {
        this.outputIter = outputIter
      } else {
        this.outputIter = Iterator.empty
      }
    }

    override def hasNext: Boolean = outputIter.hasNext || queue.nonEmpty

    override def next(): (ITuple, Option[PortIdentity]) = {
      if (outputIter.hasNext) {
        outputIter.next()
      } else {
        queue.dequeue()
      }
    }

    def appendSpecialTupleToEnd(tuple: ITuple): Unit = {
      queue.enqueue((tuple, None))
    }
  }

}

class DataProcessor(
    actorId: ActorVirtualIdentity,
    outputHandler: WorkflowFIFOMessage => Unit
) extends AmberProcessor(actorId, outputHandler)
    with Serializable {

  @transient var workerIdx: Int = 0
  @transient var physicalOp: PhysicalOp = _
  @transient var operatorConfig: OperatorConfig = _
  @transient var operator: IOperatorExecutor = _

  def initOperator(
      workerIdx: Int,
      physicalOp: PhysicalOp,
      operatorConfig: OperatorConfig,
      currentOutputIterator: Iterator[(ITuple, Option[PortIdentity])]
  ): Unit = {
    this.workerIdx = workerIdx
    this.operator = physicalOp.opExecInitInfo match {
      case OpExecInitInfoWithCode(codeGen) => ??? // TODO: compile and load java/scala operator here
      case OpExecInitInfoWithFunc(opGen) =>
        opGen(workerIdx, physicalOp, operatorConfig)
    }
    this.operatorConfig = operatorConfig
    this.physicalOp = physicalOp
    this.upstreamLinkStatus.setAllUpstreamLinks(
      if (physicalOp.isSourceOperator) {
        Set(
          PhysicalLink(SOURCE_STARTER_OP, PortIdentity(), physicalOp.id, PortIdentity())
        ) // special case for source operator
      } else {
        physicalOp.getInputLinks().toSet
      }
    )
    this.outputIterator.setTupleOutput(currentOutputIterator)
  }

  var outputIterator: DPOutputIterator = new DPOutputIterator()

  var operatorOpened: Boolean = false
  var inputBatch: Array[ITuple] = _
  var currentInputIdx: Int = -1
  var currentBatchChannel: ChannelID = _

  def initTimerService(adaptiveBatchingMonitor: WorkerTimerService): Unit = {
    this.adaptiveBatchingMonitor = adaptiveBatchingMonitor
  }

  @transient var adaptiveBatchingMonitor: WorkerTimerService = _

  def getOperatorId: PhysicalOpIdentity = VirtualIdentityUtils.getPhysicalOpId(actorId)
  def getWorkerIndex: Int = VirtualIdentityUtils.getWorkerIndex(actorId)

  // inner dependencies
  private val initializer = new DataProcessorRPCHandlerInitializer(this)
  // 1. pause manager
  val pauseManager: PauseManager = wire[PauseManager]
  // 2. breakpoint manager
  val breakpointManager: BreakpointManager = new BreakpointManager(asyncRPCClient)
  // 3. upstream links
  val upstreamLinkStatus: UpstreamLinkStatus = wire[UpstreamLinkStatus]
  // 4. state manager
  val stateManager: WorkerStateManager = new WorkerStateManager()
  // 5. batch producer
  val outputManager: OutputManager =
    new OutputManager(actorId, outputGateway)
  // 6. epoch manager
  val channelMarkerManager: ChannelMarkerManager = new ChannelMarkerManager(actorId, inputGateway)

  // dp thread stats:
  protected var inputTupleCount = 0L
  protected var outputTupleCount = 0L

  def registerInput(identifier: ActorVirtualIdentity, input: PhysicalLink): Unit = {
    upstreamLinkStatus.registerInput(identifier, input)
  }

  def getQueuedCredit(channel: ChannelID): Long = {
    inputGateway.getChannel(channel).getQueuedCredit
  }

  private def getInputPortId(workerId: ActorVirtualIdentity): PortIdentity = {
    val inputLink = upstreamLinkStatus.getInputLink(workerId)
    if (inputLink.fromOpId == SOURCE_STARTER_OP) PortIdentity() // special case for source operator
    else if (!physicalOp.getInputLinks().contains(inputLink)) PortIdentity()
    else inputLink.toPortId
  }

  def onInterrupt(): Unit = {
    adaptiveBatchingMonitor.pauseAdaptiveBatching()
  }

  def onContinue(): Unit = {
    adaptiveBatchingMonitor.resumeAdaptiveBatching()
  }

  /** provide API for actor to get stats of this operator
    *
    * @return (input tuple count, output tuple count)
    */
  def collectStatistics(): (Long, Long) = (inputTupleCount, outputTupleCount)

  /** process currentInputTuple through operator logic.
    * this function is only called by the DP thread
    *
    * @return an iterator of output tuples
    */
  private[this] def processInputTuple(tuple: Either[ITuple, InputExhausted]): Unit = {
    try {
      outputIterator.setTupleOutput(
        operator.processTuple(
          tuple,
          getInputPortId(currentBatchChannel.from).id,
          pauseManager,
          asyncRPCClient
        )
      )
      if (tuple.isLeft) {
        inputTupleCount += 1
      }
    } catch safely {
      case e =>
        // forward input tuple to the user and pause DP thread
        handleOperatorException(e)
    }
  }

  /** transfer one tuple from iterator to downstream.
    * this function is only called by the DP thread
    */
  private[this] def outputOneTuple(): Unit = {
    adaptiveBatchingMonitor.startAdaptiveBatching()
    var out: (ITuple, Option[PortIdentity]) = null
    try {
      out = outputIterator.next()
    } catch safely {
      case e =>
        // invalidate current output tuple
        out = null
        // also invalidate outputIterator
        outputIterator.setTupleOutput(Iterator.empty)
        // forward input tuple to the user and pause DP thread
        handleOperatorException(e)
    }
    if (out == null) return

    val (outputTuple, outputPortOpt) = out

    if (outputTuple == null) return

    outputTuple match {
      case FinalizeOperator() =>
        outputManager.emitEndOfUpstream()
        // Send Completed signal to worker actor.
        logger.info(
          s"$operator completed, outputted = $outputTupleCount"
        )
        operator.close() // close operator
        adaptiveBatchingMonitor.stopAdaptiveBatching()
        stateManager.transitTo(COMPLETED)
        asyncRPCClient.send(WorkerExecutionCompleted(), CONTROLLER)
      case FinalizeLink(link) =>
        logger.info(s"process FinalizeLink message")
        if (link != null && link.fromOpId != SOURCE_STARTER_OP) {
          asyncRPCClient.send(LinkCompleted(link), CONTROLLER)
        }
      case _ =>
        if (breakpointManager.evaluateTuple(outputTuple)) {
          pauseManager.pause(UserPause)
          adaptiveBatchingMonitor.pauseAdaptiveBatching()
          stateManager.transitTo(PAUSED)
        } else {
          outputTupleCount += 1
          // println(s"send output $outputTuple at step $totalValidStep")
          val outLinks = physicalOp.getOutputLinks(outputPortOpt)
          outLinks.foreach(link => outputManager.passTupleToDownstream(outputTuple, link))
        }
    }
  }

  def hasUnfinishedInput: Boolean = inputBatch != null && currentInputIdx + 1 < inputBatch.length

  def hasUnfinishedOutput: Boolean = outputIterator.hasNext

  def continueDataProcessing(): Unit = {
    if (hasUnfinishedOutput) {
      outputOneTuple()
    } else {
      currentInputIdx += 1
      processInputTuple(Left(inputBatch(currentInputIdx)))
    }
  }

  private[this] def initBatch(channel: ChannelID, batch: Array[ITuple]): Unit = {
    currentBatchChannel = channel
    inputBatch = batch
    currentInputIdx = 0
  }

  def getCurrentInputTuple: ITuple = {
    if (inputBatch == null) {
      null
    } else if (inputBatch.isEmpty) {
      ITuple("Input Exhausted")
    } else {
      inputBatch(currentInputIdx)
    }
  }

  def processDataPayload(
      channel: ChannelID,
      dataPayload: DataPayload
  ): Unit = {
    dataPayload match {
      case DataFrame(tuples) =>
        stateManager.conditionalTransitTo(
          READY,
          RUNNING,
          () => {
            asyncRPCClient.send(
              WorkerStateUpdated(stateManager.getCurrentState),
              CONTROLLER
            )
          }
        )
        initBatch(channel, tuples)
        processInputTuple(Left(inputBatch(currentInputIdx)))
      case EndOfUpstream() =>
        val currentLink = upstreamLinkStatus.getInputLink(channel.from)
        upstreamLinkStatus.markWorkerEOF(channel.from)
        if (upstreamLinkStatus.isLinkEOF(currentLink)) {
          initBatch(channel, Array.empty)
          processInputTuple(Right(InputExhausted()))
          logger.info(
            s"$currentLink completed, append FinalizeLink message"
          )
          outputIterator.appendSpecialTupleToEnd(FinalizeLink(currentLink))
        }
        if (upstreamLinkStatus.isAllEOF) {
          logger.info(
            s"operator completed, append FinalizeOperator message"
          )
          outputIterator.appendSpecialTupleToEnd(FinalizeOperator())
        }
    }
  }

  def processChannelMarker(
      channelId: ChannelID,
      marker: ChannelMarkerPayload,
      logManager: ReplayLogManager
  ): Unit = {
    val markerId = marker.id
    val command = marker.commandMapping.get(actorId)
    logger.info(s"receive marker from $channelId, id = ${marker.id}, cmd = ${command}")
    if (marker.markerType == RequireAlignment) {
      pauseManager.pauseInputChannel(EpochMarkerPause(markerId), List(channelId))
    }
    if (channelMarkerManager.isMarkerAligned(upstreamLinkStatus, channelId, marker)) {
      logManager.markAsReplayDestination(markerId)
      // invoke the control command carried with the epoch marker
      logger.info(s"process marker from $channelId, id = ${marker.id}, cmd = ${command}")
      if (command.isDefined) {
        asyncRPCServer.receive(command.get, channelId.from)
      }
      // if this operator is not the final destination of the marker, pass it downstream
      val downstreamChannelsInScope = marker.scope.filter(_.from == actorId)
      if (downstreamChannelsInScope.nonEmpty) {
        outputManager.flush(Some(downstreamChannelsInScope))
        outputGateway.getActiveChannels.foreach { activeChannelId =>
          if (downstreamChannelsInScope.contains(activeChannelId)) {
            logger.info(
              s"send marker to $activeChannelId, id = ${marker.id}, cmd = ${command}"
            )
            outputGateway.sendTo(activeChannelId, marker)
          }
        }
      }
      // unblock input channels
      if (marker.markerType == RequireAlignment) {
        pauseManager.resume(EpochMarkerPause(markerId))
      }
    }
  }

  private[this] def handleOperatorException(e: Throwable): Unit = {
    asyncRPCClient.send(
      ConsoleMessageTriggered(mkConsoleMessage(actorId, e)),
      CONTROLLER
    )
    logger.warn(e.getLocalizedMessage + "\n" + e.getStackTrace.mkString("\n"))
    // invoke a pause in-place
    asyncRPCServer.execute(PauseWorker(), SELF)
  }

  /**
    * Called by skewed worker in Reshape when it has received the tuples from the helper
    * and is ready to output tuples.
    * The call comes from AcceptMutableStateHandler.
    *
    * @param iterator
    */
  def setCurrentOutputIterator(iterator: Iterator[ITuple]): Unit = {
    outputIterator.setTupleOutput(iterator.map(t => (t, Option.empty)))
  }

}
