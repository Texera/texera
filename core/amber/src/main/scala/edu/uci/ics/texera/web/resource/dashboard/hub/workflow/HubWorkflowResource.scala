package edu.uci.ics.texera.web.resource.dashboard.hub.workflow

import edu.uci.ics.texera.web.SqlServer
import edu.uci.ics.texera.web.model.jooq.generated.Tables._
import edu.uci.ics.texera.web.model.jooq.generated.tables.pojos.{User, Workflow}
import edu.uci.ics.texera.web.resource.dashboard.hub.workflow.HubWorkflowResource.{
  HubWorkflow,
  OwnerInfo,
  PartialUser
}
import org.jooq.impl.DSL
import org.jooq.types.UInteger
import io.dropwizard.auth.Auth
import edu.uci.ics.texera.web.auth.{SessionUser}
import java.sql.Timestamp
import scala.jdk.CollectionConverters._
import scala.collection.mutable
import java.util
import javax.ws.rs._
import javax.ws.rs.core.MediaType
object HubWorkflowResource {
  case class PartialUser(name: String, googleAvatar: String)
  case class OwnerInfo(
      uid: UInteger,
      name: String,
      googleAvatar: String
  )
  case class HubWorkflow(
      name: String,
      description: String,
      wid: UInteger,
      creationTime: Timestamp,
      lastModifiedTime: Timestamp,
      owner: OwnerInfo
  )
}

@Produces(Array(MediaType.APPLICATION_JSON))
@Path("/hub/workflow")
class HubWorkflowResource {
  final private lazy val context = SqlServer.createDSLContext()

  @GET
  @Path("/list_all")
  def getAllWorkflowList: util.List[Workflow] = {
    context
      .select()
      .from(WORKFLOW)
      .fetchInto(classOf[Workflow])
  }

  @GET
  @Path("/count")
  def getWorkflowCount: Integer = {
    context.selectCount
      .from(WORKFLOW)
      .where(WORKFLOW.IS_PUBLISHED.eq(1.toByte))
      .fetchOne(0, classOf[Integer])
  }

  @GET
  @Path("/list")
  def getPublishedWorkflowList: util.List[Workflow] = {
    context
      .select()
      .from(WORKFLOW)
      .where(WORKFLOW.IS_PUBLISHED.eq(1.toByte))
      .fetchInto(classOf[Workflow])
  }

  @GET
  @Path("/owner_user")
  def getOwnerUser(@QueryParam("wid") wid: UInteger): User = {
    context
      .select(
        USER.UID,
        USER.NAME,
        USER.EMAIL,
        USER.PASSWORD,
        USER.GOOGLE_ID,
        USER.ROLE,
        USER.GOOGLE_AVATAR
      )
      .from(WORKFLOW_OF_USER)
      .join(USER)
      .on(WORKFLOW_OF_USER.UID.eq(USER.UID))
      .where(WORKFLOW_OF_USER.WID.eq(wid))
      .fetchOneInto(classOf[User])
  }

  @GET
  @Path("/user_info")
  def getUserInfo(
      @QueryParam("wids") wids: java.util.List[UInteger]
  ): java.util.Map[UInteger, PartialUser] = {
    val widsScala: Seq[UInteger] = wids.asScala.toSeq

    val records = context
      .select(WORKFLOW_OF_USER.WID, USER.NAME, USER.GOOGLE_AVATAR)
      .from(WORKFLOW_OF_USER)
      .join(USER)
      .on(WORKFLOW_OF_USER.UID.eq(USER.UID))
      .where(WORKFLOW_OF_USER.WID.in(widsScala: _*))
      .fetch()

    val userMap = mutable.Map[UInteger, PartialUser]()
    records.forEach { record =>
      val wid = record.getValue(WORKFLOW_OF_USER.WID)
      val user = PartialUser(
        name = record.getValue(USER.NAME),
        googleAvatar = record.getValue(USER.GOOGLE_AVATAR)
      )
      userMap += (wid -> user)
    }
    userMap.asJava
  }

  @GET
  @Path("/popular_workflows")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def getPopularWorkflowList(): List[HubWorkflow] = {
    val hubWorkflowEntries = context
      .select(
        WORKFLOW.NAME,
        WORKFLOW.DESCRIPTION,
        WORKFLOW.WID,
        WORKFLOW.CREATION_TIME,
        WORKFLOW.LAST_MODIFIED_TIME,
        USER.UID,
        USER.NAME,
        USER.GOOGLE_AVATAR
      )
      .from(WORKFLOW)
      .leftJoin(WORKFLOW_USER_CLONES)
      .on(WORKFLOW.WID.eq(WORKFLOW_USER_CLONES.WID))
      .leftJoin(WORKFLOW_OF_USER)
      .on(WORKFLOW.WID.eq(WORKFLOW_OF_USER.WID))
      .leftJoin(USER)
      .on(WORKFLOW_OF_USER.UID.eq(USER.UID))
      .groupBy(
        WORKFLOW.NAME,
        WORKFLOW.DESCRIPTION,
        WORKFLOW.WID,
        WORKFLOW.CREATION_TIME,
        WORKFLOW.LAST_MODIFIED_TIME,
        USER.UID,
        USER.NAME,
        USER.GOOGLE_AVATAR
      )
      .orderBy(DSL.count(WORKFLOW_USER_CLONES.UID).desc())
      .limit(4)
      .fetch();

    hubWorkflowEntries
      .map(workflowRecord => {
        val ownerInfo = OwnerInfo(
          workflowRecord.get(USER.UID),
          workflowRecord.get(USER.NAME),
          workflowRecord.get(USER.GOOGLE_AVATAR)
        )
        HubWorkflow(
          workflowRecord.get(WORKFLOW.NAME),
          workflowRecord.get(WORKFLOW.DESCRIPTION),
          workflowRecord.get(WORKFLOW.WID),
          workflowRecord.get(WORKFLOW.CREATION_TIME),
          workflowRecord.get(WORKFLOW.LAST_MODIFIED_TIME),
          ownerInfo
        )
      })
      .asScala
      .toList
  }

  @GET
  @Path("/recent_workflows")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def getRecentWorkflowList(@Auth sessionUser: SessionUser): List[HubWorkflow] = {
    val uid = sessionUser.getUser.getUid
    val hubWorkflowEntries = context
      .select(
        WORKFLOW.NAME,
        WORKFLOW.DESCRIPTION,
        WORKFLOW.WID,
        WORKFLOW.CREATION_TIME,
        WORKFLOW.LAST_MODIFIED_TIME,
        USER.UID,
        USER.NAME,
        USER.GOOGLE_AVATAR
      )
      .from(WORKFLOW)
      .leftJoin(WORKFLOW_OF_USER)
      .on(WORKFLOW.WID.eq(WORKFLOW_OF_USER.WID))
      .leftJoin(USER)
      .on(WORKFLOW_OF_USER.UID.eq(USER.UID))
      .where(USER.UID.eq(uid))
      .orderBy(WORKFLOW.LAST_MODIFIED_TIME.desc())
      .limit(4)
      .fetch();

    hubWorkflowEntries
      .map(workflowRecord => {
        val ownerInfo = OwnerInfo(
          workflowRecord.get(USER.UID),
          workflowRecord.get(USER.NAME),
          workflowRecord.get(USER.GOOGLE_AVATAR)
        )
        HubWorkflow(
          workflowRecord.get(WORKFLOW.NAME),
          workflowRecord.get(WORKFLOW.DESCRIPTION),
          workflowRecord.get(WORKFLOW.WID),
          workflowRecord.get(WORKFLOW.CREATION_TIME),
          workflowRecord.get(WORKFLOW.LAST_MODIFIED_TIME),
          ownerInfo
        )
      })
      .asScala
      .toList
  }
}
