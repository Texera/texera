package edu.uci.ics.texera.web.resource.dashboard.admin.execution

import edu.uci.ics.texera.web.SqlServer
import edu.uci.ics.texera.web.auth.SessionUser
import edu.uci.ics.texera.web.model.jooq.generated.Tables._
import edu.uci.ics.texera.web.resource.dashboard.admin.execution.AdminExecutionResource._
import io.dropwizard.auth.Auth
import org.jooq.Field
import org.jooq.impl.DSL
import org.jooq.types.UInteger

import javax.annotation.security.RolesAllowed
import javax.ws.rs._
import javax.ws.rs.core.MediaType
import scala.jdk.CollectionConverters.IterableHasAsScala

/**
  * This file handles various request related to saved-executions.
  */

object AdminExecutionResource {
  final private lazy val context = SqlServer.createDSLContext()

  case class dashboardExecution(
      workflowName: String,
      workflowId: UInteger,
      userName: String,
      userId: UInteger,
      executionId: UInteger,
      executionStatus: String,
      executionTime: Double,
      executionName: String,
      startTime: Long,
      endTime: Long,
      access: Boolean
  )

  def mapToName(code: Byte): String = {
    code match {
      case 0 => "READY"
      case 1 => "RUNNING"
      case 2 => "PAUSED"
      case 3 => "COMPLETED"
      case 4 => "FAILED"
      case 5 => "KILLED"
      case _ => "UNKNOWN" // or throw an exception, depends on your needs
    }
  }

  val sortFieldMapping = Map(
    "workflow_name" -> WORKFLOW.NAME,
    "execution_name" -> WORKFLOW_EXECUTIONS.NAME,
    "initiator" -> USER.NAME,
    "end_time" -> WORKFLOW_EXECUTIONS.LAST_UPDATE_TIME
  )

}

@Produces(Array(MediaType.APPLICATION_JSON))
@Path("/admin/execution")
@RolesAllowed(Array("ADMIN"))
class AdminExecutionResource {

  @GET
  @Path("/totalWorkflow")
  @Produces()
  def getTotalWorkflows: Int = {
    context.select(
        DSL.countDistinct(WORKFLOW.WID)
      )
      .from(WORKFLOW_EXECUTIONS)
      .join(WORKFLOW_VERSION)
      .on(WORKFLOW_EXECUTIONS.VID.eq(WORKFLOW_VERSION.VID))
      .join(USER)
      .on(WORKFLOW_EXECUTIONS.UID.eq(USER.UID))
      .join(WORKFLOW)
      .on(WORKFLOW.WID.eq(WORKFLOW_VERSION.WID))
      .fetchOne(0, classOf[Int])
  }

  /**
    * This method retrieves all existing executions
    */
  @GET
  @Path("/executionList/{pageSize}/{pageIndex}/{sortField}/{sortDirection}")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def listWorkflows(
                     @Auth current_user: SessionUser,
                     @PathParam("pageSize") page_size: Int = 20,
                     @PathParam("pageIndex") page_index: Int = 0,
                     @PathParam("sortField") sortField: String = "end_time",
                     @PathParam("sortDirection") sortDirection: String = "desc"
                   ): List[dashboardExecution] = {
    val latestExecutionId = context.select(
        WORKFLOW_VERSION.WID,
        DSL.max(WORKFLOW_EXECUTIONS.EID).as("max_eid")
      )
      .from(WORKFLOW_EXECUTIONS)
      .join(WORKFLOW_VERSION)
      .on(WORKFLOW_VERSION.VID.eq(WORKFLOW_EXECUTIONS.VID))
      .groupBy(WORKFLOW_VERSION.WID)
      .asTable("latest_execution_id")

    val executions = context
      .select(
        WORKFLOW_EXECUTIONS.UID,
        USER.NAME,
        WORKFLOW_VERSION.WID,
        WORKFLOW.NAME,
        WORKFLOW_EXECUTIONS.EID,
        WORKFLOW_EXECUTIONS.STARTING_TIME,
        WORKFLOW_EXECUTIONS.LAST_UPDATE_TIME,
        WORKFLOW_EXECUTIONS.STATUS,
        WORKFLOW_EXECUTIONS.NAME
      )
      .from(WORKFLOW_EXECUTIONS)
      .join(WORKFLOW_VERSION)
      .on(WORKFLOW_EXECUTIONS.VID.eq(WORKFLOW_VERSION.VID))
      .join(USER)
      .on(WORKFLOW_EXECUTIONS.UID.eq(USER.UID))
      .join(WORKFLOW)
      .on(WORKFLOW.WID.eq(WORKFLOW_VERSION.WID))
      .join(latestExecutionId)
      .on(
        DSL.and(
          WORKFLOW_EXECUTIONS.EID.eq(latestExecutionId.field("max_eid").asInstanceOf[Field[UInteger]]),
          WORKFLOW_VERSION.WID.eq(latestExecutionId.field(WORKFLOW_VERSION.WID))
        )
      )

    var executions_order = executions.limit(page_size).offset(page_index * page_size)
    if (sortField != "NO_SORTING") {
      val orderByField = sortFieldMapping.getOrElse(sortField, WORKFLOW.NAME)
      val order = if (sortDirection == "desc") orderByField.desc() else orderByField.asc()
      executions_order = executions.orderBy(order).limit(page_size).offset(page_index * page_size)
    }

    val workflowEntries = executions_order.fetch()

    val availableWorkflowIds = context
      .select(WORKFLOW_USER_ACCESS.WID)
      .from(WORKFLOW_USER_ACCESS)
      .where(WORKFLOW_USER_ACCESS.UID.eq(current_user.getUid))
      .fetchInto(classOf[UInteger])

    workflowEntries
      .map(workflowRecord => {
        val startingTime =
          workflowRecord.get(WORKFLOW_EXECUTIONS.STARTING_TIME).getTime

        var lastUpdateTime: Long = 0
        if (workflowRecord.get(WORKFLOW_EXECUTIONS.LAST_UPDATE_TIME) == null) {
          lastUpdateTime = 0
        } else {
          lastUpdateTime = workflowRecord.get(WORKFLOW_EXECUTIONS.LAST_UPDATE_TIME).getTime
        }

        val timeDifferenceSeconds = (lastUpdateTime - startingTime) / 1000.0
        val hasAccess = availableWorkflowIds.contains(workflowRecord.get(WORKFLOW_VERSION.WID))
        dashboardExecution(
          workflowRecord.get(WORKFLOW.NAME),
          workflowRecord.get(WORKFLOW_VERSION.WID),
          workflowRecord.get(USER.NAME),
          workflowRecord.get(WORKFLOW_EXECUTIONS.UID),
          workflowRecord.get(WORKFLOW_EXECUTIONS.EID),
          mapToName(workflowRecord.get(WORKFLOW_EXECUTIONS.STATUS)),
          timeDifferenceSeconds,
          workflowRecord.get(WORKFLOW_EXECUTIONS.NAME),
          startingTime,
          lastUpdateTime,
          hasAccess
        )
      })
      .asScala
      .toList
  }
}
