package edu.uci.ics.amber.engine.architecture.worker

import com.softwaremill.macwire.wire
import edu.uci.ics.amber.engine.architecture.common.AmberProcessor
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.ConsoleMessageHandler.ConsoleMessageTriggered
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.PortCompletedHandler.PortCompleted
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.WorkerExecutionCompletedHandler.WorkerExecutionCompleted
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.WorkerStateUpdatedHandler.WorkerStateUpdated
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
  FinalizeOperator,
  FinalizePort
}
import edu.uci.ics.amber.engine.architecture.worker.WorkflowWorker.MainThreadDelegateMessage
import edu.uci.ics.amber.engine.architecture.worker.promisehandlers.PauseHandler.PauseWorker
import edu.uci.ics.amber.engine.architecture.worker.statistics.WorkerState.{
  COMPLETED,
  READY,
  RUNNING
}
import edu.uci.ics.amber.engine.architecture.worker.statistics.WorkerStatistics
import edu.uci.ics.amber.engine.common.ambermessage.{
  ChannelMarkerPayload,
  DataFrame,
  DataPayload,
  EndOfUpstream,
  RequireAlignment,
  WorkflowFIFOMessage
}
import edu.uci.ics.amber.engine.common.statetransition.WorkerStateManager
import edu.uci.ics.amber.engine.common.tuple.amber.{SchemaEnforceable, SpecialTupleLike, TupleLike}
import edu.uci.ics.amber.engine.common.virtualidentity.util.{CONTROLLER, SELF}
import edu.uci.ics.amber.engine.common.virtualidentity.{
  ActorVirtualIdentity,
  ChannelIdentity,
  PhysicalOpIdentity
}
import edu.uci.ics.amber.engine.common.workflow.PortIdentity
import edu.uci.ics.amber.engine.common.{IOperatorExecutor, InputExhausted, VirtualIdentityUtils}
import edu.uci.ics.amber.error.ErrorUtils.{mkConsoleMessage, safely}
import edu.uci.ics.texera.workflow.common.tuple.Tuple

import scala.collection.mutable

object DataProcessor {

  case class FinalizePort(portId: PortIdentity, input: Boolean) extends SpecialTupleLike {
    override def fields: Array[Any] = Array("FinalizePort")
  }
  case class FinalizeOperator() extends SpecialTupleLike {
    override def fields: Array[Any] = Array("FinalizeOperator")
  }

  class DPOutputIterator extends Iterator[(TupleLike, Option[PortIdentity])] {
    val queue = new mutable.Queue[(TupleLike, Option[PortIdentity])]
    @transient var outputIter: Iterator[(TupleLike, Option[PortIdentity])] = Iterator.empty

    def setTupleOutput(outputIter: Iterator[(TupleLike, Option[PortIdentity])]): Unit = {
      if (outputIter != null) {
        this.outputIter = outputIter
      } else {
        this.outputIter = Iterator.empty
      }
    }

    override def hasNext: Boolean = outputIter.hasNext || queue.nonEmpty

    override def next(): (TupleLike, Option[PortIdentity]) = {
      if (outputIter.hasNext) {
        outputIter.next()
      } else {
        queue.dequeue()
      }
    }

    def appendSpecialTupleToEnd(tuple: TupleLike): Unit = {
      queue.enqueue((tuple, None))
    }
  }

}

class DataProcessor(
    actorId: ActorVirtualIdentity,
    outputHandler: Either[MainThreadDelegateMessage, WorkflowFIFOMessage] => Unit
) extends AmberProcessor(actorId, outputHandler)
    with Serializable {

  @transient var workerIdx: Int = 0
  @transient var physicalOp: PhysicalOp = _
  @transient var operatorConfig: OperatorConfig = _
  @transient var operator: IOperatorExecutor = _
  @transient var serializationCall: () => Unit = _

  def initOperator(
      workerIdx: Int,
      physicalOp: PhysicalOp,
      operatorConfig: OperatorConfig,
      currentOutputIterator: Iterator[(TupleLike, Option[PortIdentity])]
  ): Unit = {
    this.workerIdx = workerIdx
    this.operator = physicalOp.opExecInitInfo match {
      case OpExecInitInfoWithCode(codeGen) => ??? // TODO: compile and load java/scala operator here
      case OpExecInitInfoWithFunc(opGen) =>
        opGen(workerIdx, physicalOp, operatorConfig)
    }
    this.operatorConfig = operatorConfig
    this.physicalOp = physicalOp

    this.outputIterator.setTupleOutput(currentOutputIterator)
  }

  var outputIterator: DPOutputIterator = new DPOutputIterator()

  var inputBatch: Array[Tuple] = _
  var currentInputIdx: Int = -1
  var currentChannelId: ChannelIdentity = _

  def initTimerService(adaptiveBatchingMonitor: WorkerTimerService): Unit = {
    this.adaptiveBatchingMonitor = adaptiveBatchingMonitor
  }

  @transient var adaptiveBatchingMonitor: WorkerTimerService = _

  def getOperatorId: PhysicalOpIdentity = VirtualIdentityUtils.getPhysicalOpId(actorId)
  def getWorkerIndex: Int = VirtualIdentityUtils.getWorkerIndex(actorId)

  // inner dependencies
  private val initializer = new DataProcessorRPCHandlerInitializer(this)
  val pauseManager: PauseManager = wire[PauseManager]
  val stateManager: WorkerStateManager = new WorkerStateManager()
  val outputManager: OutputManager = new OutputManager(actorId, outputGateway)
  val channelMarkerManager: ChannelMarkerManager = new ChannelMarkerManager(actorId, inputGateway)

  def getQueuedCredit(channelId: ChannelIdentity): Long = {
    inputGateway.getChannel(channelId).getQueuedCredit
  }

  /** provide API for actor to get stats of this operator
    *
    * @return (input tuple count, output tuple count)
    */
  def collectStatistics(): WorkerStatistics =
    statisticsManager.getStatistics(stateManager.getCurrentState, operator)

  /** process currentInputTuple through operator logic.
    * this function is only called by the DP thread
    *
    * @return an iterator of output tuples
    */
  private[this] def processInputTuple(tuple: Either[Tuple, InputExhausted]): Unit = {
    try {
      outputIterator.setTupleOutput(
        operator.processTupleMultiPort(
          tuple,
          this.inputGateway.getChannel(currentChannelId).getPortId.id
        )
      )
      if (tuple.isLeft) {
        statisticsManager.increaseInputTupleCount()
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
    var out: (TupleLike, Option[PortIdentity]) = null
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
        operator.close() // close operator
        adaptiveBatchingMonitor.stopAdaptiveBatching()
        stateManager.transitTo(COMPLETED)
        logger.info(
          s"$operator completed, # of input ports = ${inputGateway.getAllPorts.size}, " +
            s"input tuple count = ${statisticsManager.getInputTupleCount}, " +
            s"output tuple count = ${statisticsManager.getOutputTupleCount}"
        )
        asyncRPCClient.send(WorkerExecutionCompleted(), CONTROLLER)
      case FinalizePort(portId, input) =>
        asyncRPCClient.send(PortCompleted(portId, input), CONTROLLER)
      case schemaEnforceable: SchemaEnforceable =>
        statisticsManager.increaseOutputTupleCount()
        outputPortOpt match {
          case Some(port) =>
            pushTupleToPort(schemaEnforceable, port)
          case None =>
            // push to all output ports if not specified.
            physicalOp.outputPorts.keys.foreach(port => {
              pushTupleToPort(schemaEnforceable, port)
            })
        }
      case other => // skip for now
    }
  }

  private def pushTupleToPort(outputTuple: SchemaEnforceable, port: PortIdentity): Unit = {
    physicalOp.getOutputLinks(port).foreach { out =>
      outputManager.passTupleToDownstream(
        outputTuple,
        out,
        physicalOp.outputPorts(port)._3
      )
    }
  }

  def hasUnfinishedInput: Boolean = inputBatch != null && currentInputIdx + 1 < inputBatch.length

  def hasUnfinishedOutput: Boolean = outputIterator.hasNext

  def continueDataProcessing(): Unit = {
    val dataProcessingStartTime = System.nanoTime()
    if (hasUnfinishedOutput) {
      outputOneTuple()
    } else {
      currentInputIdx += 1
      processInputTuple(Left(inputBatch(currentInputIdx)))
    }
    statisticsManager.increaseDataProcessingTime(System.nanoTime() - dataProcessingStartTime)
  }

  private[this] def initBatch(channelId: ChannelIdentity, batch: Array[Tuple]): Unit = {
    currentChannelId = channelId
    inputBatch = batch
    currentInputIdx = 0
  }

  def getCurrentInputTuple: Tuple = {
    if (inputBatch == null) {
      null
    } else if (inputBatch.isEmpty) {
      null // TODO: create input exhausted
    } else {
      inputBatch(currentInputIdx)
    }
  }

  def processDataPayload(
      channelId: ChannelIdentity,
      dataPayload: DataPayload
  ): Unit = {
    val dataProcessingStartTime = System.nanoTime()
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
        initBatch(channelId, tuples)
        processInputTuple(Left(inputBatch(currentInputIdx)))
      case EndOfUpstream() =>
        val channel = this.inputGateway.getChannel(channelId)
        val portId = channel.getPortId

        this.inputGateway.getPort(portId).channels(channelId) = true

        if (inputGateway.isPortCompleted(portId)) {
          initBatch(channelId, Array.empty)
          processInputTuple(Right(InputExhausted()))
          outputIterator.appendSpecialTupleToEnd(FinalizePort(portId, input = true))
        }
        if (inputGateway.getAllPorts.forall(portId => inputGateway.isPortCompleted(portId))) {
          // TOOPTIMIZE: assuming all the output ports finalize after all input ports are finalized.
          outputGateway
            .getPortIds()
            .foreach(outputPortId =>
              outputIterator.appendSpecialTupleToEnd(FinalizePort(outputPortId, input = false))
            )
          outputIterator.appendSpecialTupleToEnd(FinalizeOperator())
        }
    }
    statisticsManager.increaseDataProcessingTime(System.nanoTime() - dataProcessingStartTime)
  }

  def processChannelMarker(
      channelId: ChannelIdentity,
      marker: ChannelMarkerPayload,
      logManager: ReplayLogManager
  ): Unit = {
    val markerId = marker.id
    val command = marker.commandMapping.get(actorId)
    logger.info(s"receive marker from $channelId, id = ${marker.id}, cmd = ${command}")
    if (marker.markerType == RequireAlignment) {
      pauseManager.pauseInputChannel(EpochMarkerPause(markerId), List(channelId))
    }
    if (channelMarkerManager.isMarkerAligned(channelId, marker)) {
      logManager.markAsReplayDestination(markerId)
      // invoke the control command carried with the epoch marker
      logger.info(s"process marker from $channelId, id = ${marker.id}, cmd = ${command}")
      if (command.isDefined) {
        asyncRPCServer.receive(command.get, channelId.fromWorkerId)
      }
      // if this operator is not the final destination of the marker, pass it downstream
      val downstreamChannelsInScope = marker.scope.filter(_.fromWorkerId == actorId)
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
  def setCurrentOutputIterator(iterator: Iterator[TupleLike]): Unit = {
    outputIterator.setTupleOutput(iterator.map(t => (t, Option.empty)))
  }

}
