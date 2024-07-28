package edu.uci.ics.texera.web.resource.dashboard.hub.test

import edu.uci.ics.texera.web.SqlServer
import edu.uci.ics.texera.web.auth.SessionUser
import org.jooq.types.UInteger

import java.util

import edu.uci.ics.texera.web.resource.dashboard.hub.test.TestHubResource.Workflow

import javax.ws.rs._
import javax.ws.rs.core.MediaType
import edu.uci.ics.texera.web.model.jooq.generated.Tables._
import edu.uci.ics.texera.web.resource.dashboard.hub.test.TestHubResource.getAllWorkflows
import io.dropwizard.auth.Auth
import scala.jdk.CollectionConverters.IterableHasAsScala

object TestHubResource {
  final private lazy val context = SqlServer.createDSLContext()

  case class Workflow(
                       workflowId: UInteger,
                       workflowName: String
                     )

  def getAllWorkflows: List[Workflow] = {
    val allWorkflowEntries = context
      .select(
        WORKFLOW.WID,
        WORKFLOW.NAME
      )
      .from(
        WORKFLOW
      )
      .fetch()

    allWorkflowEntries
      .map(workflowRecord => {
        Workflow(
          workflowRecord.get(WORKFLOW.WID),
          workflowRecord.get(WORKFLOW.NAME)
        )
      })
      .asScala
      .toList
  }
}


@Path("/hub")
class TestHubResource {
  @GET
  @Path("/get_all_workflows")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def getCreatedWorkflow: List[Workflow] = {
    getAllWorkflows
  }
}