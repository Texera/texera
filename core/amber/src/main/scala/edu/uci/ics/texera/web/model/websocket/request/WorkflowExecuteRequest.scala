package edu.uci.ics.texera.web.model.websocket.request

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import edu.uci.ics.texera.workflow.common.operators.LogicalOp
import edu.uci.ics.texera.workflow.common.workflow.{LogicalLink, WorkflowSettings}

case class ReplayExecutionInfo(
    @JsonDeserialize(contentAs = classOf[java.lang.Long])
    eid: Long,
    interaction: String
)

case class WorkflowExecuteRequest(
    executionName: String,
    engineVersion: String,
    logicalPlan: LogicalPlanPojo,
    replayFromExecution: Option[ReplayExecutionInfo], // contains execution Id, interaction Id.
    workflowSettings: WorkflowSettings
) extends TexeraWebSocketRequest

case class LogicalPlanPojo(
    operators: List[LogicalOp],
    links: List[LogicalLink],
    opsToViewResult: List[String],
    opsToReuseResult: List[String]
)
