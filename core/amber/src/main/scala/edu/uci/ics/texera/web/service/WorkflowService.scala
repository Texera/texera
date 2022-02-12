package edu.uci.ics.texera.web.service

import java.util.concurrent.ConcurrentHashMap
import com.typesafe.scalalogging.LazyLogging
import edu.uci.ics.amber.engine.common.AmberUtils
import edu.uci.ics.texera.Utils.objectMapper
import edu.uci.ics.texera.web.model.websocket.event.{
  TexeraWebSocketEvent,
  WorkflowErrorEvent,
  WorkflowExecutionErrorEvent
}
import edu.uci.ics.texera.web.{
  SubscriptionManager,
  TexeraWebApplication,
  WebsocketInput,
  WorkflowLifecycleManager
}
import edu.uci.ics.texera.web.model.websocket.request.{
  CacheStatusUpdateRequest,
  TexeraWebSocketRequest,
  WorkflowExecuteRequest,
  WorkflowKillRequest
}
import edu.uci.ics.texera.web.resource.WorkflowWebsocketResource
import edu.uci.ics.texera.web.storage.WorkflowStateStore
import edu.uci.ics.texera.workflow.common.WorkflowContext
import edu.uci.ics.texera.workflow.common.storage.OpResultStorage
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.disposables.{CompositeDisposable, Disposable}
import io.reactivex.rxjava3.subjects.{BehaviorSubject, Subject}
import org.jooq.types.UInteger

object WorkflowService {
  private val wIdToWorkflowState = new ConcurrentHashMap[String, WorkflowService]()
  val cleanUpDeadlineInSeconds: Int =
    AmberUtils.amberConfig.getInt("web-server.workflow-state-cleanup-in-seconds")
  def getOrCreate(
      wId: Int,
      uidOpt: Option[UInteger],
      cleanupTimeout: Int = cleanUpDeadlineInSeconds
  ): WorkflowService = {
    var workflowStateId: String = ""
    uidOpt match {
      case Some(user) =>
        workflowStateId = user + "-" + wId
      case None =>
        // use a fixed wid for reconnection
        workflowStateId = "dummy wid"
    }
    wIdToWorkflowState.compute(
      workflowStateId,
      (_, v) => {
        if (v == null) {
          new WorkflowService(uidOpt, wId, cleanupTimeout)
        } else {
          v
        }
      }
    )
  }
}

class WorkflowService(
    uidOpt: Option[UInteger],
    wId: Int,
    cleanUpTimeout: Int
) extends SubscriptionManager
    with LazyLogging {
  // state across execution:
  var opResultStorage: OpResultStorage = new OpResultStorage(
    AmberUtils.amberConfig.getString("storage.mode").toLowerCase
  )
  private val errorSubject = BehaviorSubject.create[TexeraWebSocketEvent]().toSerialized
  val errorHandler: Throwable => Unit = { t =>
    {
      t.printStackTrace()
      errorSubject.onNext(
        WorkflowErrorEvent(generalErrors =
          Map("error" -> (t.getMessage + "\n" + t.getStackTrace.mkString("\n")))
        )
      )
    }
  }
  val wsInput = new WebsocketInput(errorHandler)
  val stateStore = new WorkflowStateStore()
  val resultService: JobResultService =
    new JobResultService(opResultStorage, stateStore)
  val exportService: ResultExportService = new ResultExportService(opResultStorage)
  val operatorCache: WorkflowCacheService =
    new WorkflowCacheService(opResultStorage, stateStore, wsInput)
  var jobService: Option[WorkflowJobService] = None
  val lifeCycleManager: WorkflowLifecycleManager = new WorkflowLifecycleManager(
    s"uid=$uidOpt wid=$wId",
    cleanUpTimeout,
    () => {
      opResultStorage.close()
      WorkflowService.wIdToWorkflowState.remove(wId)
      wsInput.onNext(WorkflowKillRequest(), None)
      unsubscribeAll()
    }
  )

  addSubscription(
    wsInput.subscribe((evt: WorkflowExecuteRequest, uidOpt) => initJobService(evt, uidOpt))
  )

  def connect(onNext: TexeraWebSocketEvent => Unit): Disposable = {
    lifeCycleManager.increaseUserCount()
    val subscriptions = stateStore.getAllStores
      .map(_.getWebsocketEventObservable)
      .map(evtPub =>
        evtPub.subscribe { evts: Iterable[TexeraWebSocketEvent] => evts.foreach(onNext) }
      )
      .toSeq
    val errorSubscription = errorSubject.subscribe { evt: TexeraWebSocketEvent => onNext(evt) }
    new CompositeDisposable(subscriptions :+ errorSubscription: _*)
  }

  def disconnect(): Unit = {
    lifeCycleManager.decreaseUserCount(
      stateStore.jobStateStore.getState.state
    )
  }

  private[this] def createWorkflowContext(request: WorkflowExecuteRequest): WorkflowContext = {
    val jobID: String = String.valueOf(WorkflowWebsocketResource.nextExecutionID.incrementAndGet)
    if (WorkflowCacheService.isAvailable) {
      operatorCache.updateCacheStatus(
        CacheStatusUpdateRequest(
          request.operators,
          request.links,
          request.breakpoints,
          request.cachedOperatorIds
        )
      )
    }
    new WorkflowContext(jobID, uidOpt, wId)
  }

  def initJobService(req: WorkflowExecuteRequest, uidOpt: Option[UInteger]): Unit = {
    if (jobService.isDefined) {
      //unsubscribe all
      jobService.get.unsubscribeAll()
    }
    val job = new WorkflowJobService(
      createWorkflowContext(req),
      stateStore,
      wsInput,
      operatorCache,
      resultService,
      req,
      errorHandler
    )
    lifeCycleManager.registerCleanUpOnStateChange(stateStore)
    jobService = Some(job)
    job.startWorkflow()
  }

  override def unsubscribeAll(): Unit = {
    super.unsubscribeAll()
    jobService.foreach(_.unsubscribeAll())
    operatorCache.unsubscribeAll()
    resultService.unsubscribeAll()
  }

}
