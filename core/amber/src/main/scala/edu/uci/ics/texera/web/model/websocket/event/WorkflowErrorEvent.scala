package edu.uci.ics.texera.web.model.websocket.event

import edu.uci.ics.amber.engine.common.WorkflowFatalError

case class WorkflowErrorEvent(
    fatalErrors: Seq[WorkflowFatalError]
) extends TexeraWebSocketEvent
