package edu.uci.ics.texera.web.resource.dashboard.user.file

import edu.uci.ics.texera.web.SqlServer
import edu.uci.ics.texera.web.auth.SessionUser
import edu.uci.ics.texera.web.model.common.AccessEntry
import edu.uci.ics.texera.web.model.jooq.generated.Tables.{
  FILE,
  FILE_OF_WORKFLOW,
  USER,
  USER_FILE_ACCESS
}
import edu.uci.ics.texera.web.model.jooq.generated.enums.UserFileAccessPrivilege
import edu.uci.ics.texera.web.model.jooq.generated.tables.daos.{
  FileDao,
  FileOfWorkflowDao,
  UserDao,
  UserFileAccessDao
}
import edu.uci.ics.texera.web.model.jooq.generated.tables.pojos.{FileOfWorkflow, UserFileAccess}
import edu.uci.ics.texera.web.resource.dashboard.user.file.UserFileAccessResource.{
  checkWriteAccess,
  context,
  fileDao,
  userDao
}
import io.dropwizard.auth.Auth
import org.jooq.DSLContext
import org.jooq.types.UInteger

import java.util
import javax.annotation.security.RolesAllowed
import javax.ws.rs._
import javax.ws.rs.core.MediaType

/**
  * A Utility Class used to for operations related to database
  */
object UserFileAccessResource {
  final private lazy val context: DSLContext = SqlServer.createDSLContext
  final private lazy val userDao = new UserDao(context.configuration)
  final private lazy val fileDao = new FileDao(context.configuration)
  final private lazy val file_of_workflowDao = new FileOfWorkflowDao(context.configuration)

  def getFilePath(
      email: String,
      fileName: String,
      uid: UInteger,
      wid: UInteger
  ): Option[String] = {
    val fid = context
      .select(FILE.FID)
      .from(FILE)
      .where(FILE.OWNER_UID.eq(userDao.fetchOneByEmail(email).getUid).and(FILE.NAME.eq(fileName)))
      .fetch()
      .getValue(0, 0)
      .asInstanceOf[UInteger]
    if (
      context
        .fetchExists(
          context
            .selectFrom(USER_FILE_ACCESS)
            .where(USER_FILE_ACCESS.UID.eq(uid).and(USER_FILE_ACCESS.FID.eq(fid)))
        ) || context
        .fetchExists(
          context
            .selectFrom(FILE_OF_WORKFLOW)
            .where(FILE_OF_WORKFLOW.WID.eq(wid).and(FILE_OF_WORKFLOW.FID.eq(fid)))
        )
    ) {
      file_of_workflowDao.merge(new FileOfWorkflow(fid, wid))
      Option(fileDao.fetchOneByFid(fid).getPath)
    } else {
      None
    }
  }

  /**
    * Identifies whether the given user has read-only access over the given workflow
    * @param fid file id
    * @param uid user id, works with file id as primary keys in database
    */
  def checkReadAccess(fid: UInteger, uid: UInteger): Unit = {
    if (
      !(getPrivilege(fid, uid).eq(UserFileAccessPrivilege.READ) || getPrivilege(fid, uid).eq(
        UserFileAccessPrivilege.WRITE
      ))
    ) {
      throw new ForbiddenException("No sufficient access privilege.")
    }
  }

  /**
    * Identifies whether the given user has write access over the given workflow
    * @param fid file id
    * @param uid user id, works with file id as primary keys in database
    */
  def checkWriteAccess(fid: UInteger, uid: UInteger): Unit = {
    if (!getPrivilege(fid, uid).eq(UserFileAccessPrivilege.WRITE)) {
      throw new ForbiddenException("No sufficient access privilege.")
    }
  }

  /**
    * @param fid file id
    * @param uid user id, works with file id as primary keys in database
    * @return UserFileAccessPrivilege value indicating NONE/READ/WRITE
    */
  private def getPrivilege(fid: UInteger, uid: UInteger): UserFileAccessPrivilege = {
    context
      .select()
      .from(USER_FILE_ACCESS)
      .where(USER_FILE_ACCESS.FID.eq(fid).and(USER_FILE_ACCESS.UID.eq(uid)))
      .fetchOneInto(classOf[UserFileAccess])
      .getPrivilege
  }
}
@Produces(Array(MediaType.APPLICATION_JSON))
@RolesAllowed(Array("REGULAR", "ADMIN"))
@Path("/access/file")
class UserFileAccessResource {
  final private val userFileAccessDao = new UserFileAccessDao(context.configuration)

  /**
    * This method returns the owner of a workflow
    *
    * @param fid ,  file id
    * @return ownerEmail,  the owner's email
    */
  @GET
  @Path("/owner/{fid}")
  def getOwner(@PathParam("fid") fid: UInteger): String = {
    val uid = fileDao.fetchOneByFid(fid).getOwnerUid
    userDao.fetchOneByUid(uid).getEmail
  }

  /**
    * Retrieves the list of all shared accesses of the target file
    *
    * @param fid the id of the file
    * @return a List of email/name/permission pair
    */
  @GET
  @Path("list/{fid}")
  def getAccessList(
      @PathParam("fid") fid: UInteger,
      @Auth user: SessionUser
  ): util.List[AccessEntry] = {
    context
      .select(
        USER.EMAIL,
        USER.NAME,
        USER_FILE_ACCESS.PRIVILEGE
      )
      .from(USER_FILE_ACCESS)
      .join(USER)
      .on(USER.UID.eq(USER_FILE_ACCESS.UID))
      .where(
        USER_FILE_ACCESS.FID
          .eq(fid)
          .and(USER_FILE_ACCESS.UID.notEqual(user.getUid))
      )
      .fetchInto(classOf[AccessEntry])
  }

  /**
    * This method shares a file to a user with a specific access type
    *
    * @param fid       the id of target file to be shared to
    * @param email     the email of target user to be shared
    * @param privilege the type of access to be shared
    * @return rejection if user not permitted to share the workflow or Success Message
    */
  @PUT
  @Path("/grant/{fid}/{email}/{privilege}")
  def grantAccess(
      @PathParam("fid") fid: UInteger,
      @PathParam("email") email: String,
      @PathParam("privilege") privilege: String,
      @Auth user: SessionUser
  ): Unit = {
    checkWriteAccess(fid, user.getUid)
    userFileAccessDao.merge(
      new UserFileAccess(
        userDao.fetchOneByEmail(email).getUid,
        fid,
        UserFileAccessPrivilege.valueOf(privilege)
      )
    )
  }

  /**
    * Revoke a user's access to a file
    *
    * @param fid   the id of the file
    * @param email the email of target user whose access is about to be revoked
    * @return A successful resp if granted, failed resp otherwise
    */
  @DELETE
  @Path("/revoke/{fid}/{email}")
  @RolesAllowed(Array("REGULAR", "ADMIN"))
  def revokeAccess(
      @PathParam("fid") fid: UInteger,
      @PathParam("email") email: String,
      @Auth user: SessionUser
  ): Unit = {
    checkWriteAccess(fid, user.getUid)
    context
      .delete(USER_FILE_ACCESS)
      .where(
        USER_FILE_ACCESS.UID
          .eq(userDao.fetchOneByEmail(email).getUid)
          .and(USER_FILE_ACCESS.FID.eq(fid))
      )
      .execute()
  }
}
