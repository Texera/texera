package edu.uci.ics.texera.web

import edu.uci.ics.texera.web.model.websocket.event.TexeraWebSocketEvent
import edu.uci.ics.texera.web.service.WorkflowService
import javax.websocket.Session
import rx.lang.scala.subscriptions.CompositeSubscription
import rx.lang.scala.{Observer, Subscription}

import scala.collection.mutable

class SessionState(session: Session) {
  private val observer: Observer[TexeraWebSocketEvent] = new WebsocketSubscriber(session)
  private var currentWorkflowState: Option[WorkflowService] = None

  def getCurrentWorkflowState: Option[WorkflowService] = currentWorkflowState

  def unsubscribe(): Unit = {
    if (currentWorkflowState.isDefined) {
      currentWorkflowState.get.disconnect(observer)
      currentWorkflowState = None
    }
  }

  def subscribe(workflowService: WorkflowService): Unit = {
    unsubscribe()
    currentWorkflowState = Some(workflowService)
    workflowService.connect(observer)
  }
}
