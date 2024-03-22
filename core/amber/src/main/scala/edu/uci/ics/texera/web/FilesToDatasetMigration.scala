package edu.uci.ics.texera.web

import edu.uci.ics.texera.web.FilesToDatasetMigration.context
import edu.uci.ics.texera.web.model.jooq.generated.enums.DatasetUserAccessPrivilege
import edu.uci.ics.texera.web.model.jooq.generated.tables.pojos.{Dataset, DatasetUserAccess, DatasetVersion, Environment, EnvironmentOfWorkflow, User}
import org.jooq.types.UInteger
import edu.uci.ics.texera.web.model.jooq.generated.tables.File.FILE
import edu.uci.ics.texera.web.model.jooq.generated.tables.User.USER
import edu.uci.ics.texera.web.model.jooq.generated.tables.Dataset.DATASET
import edu.uci.ics.texera.web.model.jooq.generated.tables.DatasetVersion.DATASET_VERSION
import edu.uci.ics.texera.web.model.jooq.generated.tables.WorkflowOfUser.WORKFLOW_OF_USER
import edu.uci.ics.texera.web.model.jooq.generated.tables.WorkflowUserAccess.WORKFLOW_USER_ACCESS
import edu.uci.ics.texera.web.model.jooq.generated.tables.EnvironmentOfWorkflow.ENVIRONMENT_OF_WORKFLOW
import edu.uci.ics.texera.web.model.jooq.generated.tables.DatasetOfEnvironment.DATASET_OF_ENVIRONMENT
import edu.uci.ics.texera.web.model.jooq.generated.tables.DatasetUserAccess.DATASET_USER_ACCESS
import edu.uci.ics.texera.web.model.jooq.generated.tables.Environment.ENVIRONMENT
import edu.uci.ics.texera.web.model.jooq.generated.tables.daos.{DatasetUserAccessDao, EnvironmentOfWorkflowDao}
import edu.uci.ics.texera.web.resource.dashboard.user.dataset.service.GitVersionControlLocalFileStorage
import edu.uci.ics.texera.web.resource.dashboard.user.dataset.utils.PathUtils
import org.jooq.{Record, Record2, Record3, Record4, Result}
import org.jooq.impl.DSL

import java.io.FileInputStream
import java.nio.file.{Path, Paths}
import java.util
import scala.collection.convert.ImplicitConversions.`iterable AsScalaIterable`
import scala.jdk.CollectionConverters.{CollectionHasAsScala, IterableHasAsJava}


object FilesToDatasetMigration extends App {
  val filesDirectory = "/Users/baijiadong/Desktop/chenlab/texera/core/amber/user-resources/files"
  private val context = SqlServer.createDSLContext()

  case class UserKey(
                      userEmail: String,
                      uid: UInteger
                    )
  def createDataset(
                     userKey: UserKey,
                     ownerAndFiles: Map[UserKey, Map[Path, String]]): Unit = {
    // first insert the dataset entry
    val files = ownerAndFiles.get(userKey)
    files match {
      case None => throw new RuntimeException("Should not happen")
      case Some(f) =>
        val datasetOfUserDao = new DatasetUserAccessDao(context.configuration())
        // first insert a new dataset
        val dataset: Dataset = new Dataset()
        dataset.setName(userKey.userEmail)
        dataset.setDescription(s"${userKey.userEmail}'s personal dataset")
        dataset.setIsPublic(0.toByte)
        dataset.setOwnerUid(userKey.uid)

        val createdDataset = context
          .insertInto(DATASET)
          .set(context.newRecord(DATASET, dataset))
          .returning()
          .fetchOne()

        val did = createdDataset.getDid
        val datasetPath = PathUtils.getDatasetPath(did)
        createdDataset.setStoragePath(datasetPath.toString)
        createdDataset.update()

        val datasetUserAccess = new DatasetUserAccess()
        datasetUserAccess.setDid(createdDataset.getDid)
        datasetUserAccess.setUid(userKey.uid)
        datasetUserAccess.setPrivilege(DatasetUserAccessPrivilege.WRITE)
        datasetOfUserDao.insert(datasetUserAccess)

        // initialize the dataset directory
        GitVersionControlLocalFileStorage.initRepo(datasetPath)
        val commitHash = GitVersionControlLocalFileStorage.withCreateVersion(
          datasetPath,
          "v1",
          () => {
            for ((filePath, fileName) <- f) {
              println(s"Path: $filePath, File Name: $fileName")
              try {
                val inputStream = new FileInputStream(filePath.toString)
                val datasetFilePath = datasetPath.resolve(fileName)
                GitVersionControlLocalFileStorage.writeFileToRepo(datasetPath, datasetFilePath, inputStream)
              } catch {
                case exception: Exception => println(exception)
              }
            }
          }
        )

        // then create the dataset version
        val datasetVersion = new DatasetVersion()

        datasetVersion.setName("v1")
        datasetVersion.setDid(did)
        datasetVersion.setCreatorUid(userKey.uid)
        datasetVersion.setVersionHash(commitHash)

        context
          .insertInto(DATASET_VERSION) // Assuming DATASET is the table reference
          .set(context.newRecord(DATASET_VERSION, datasetVersion))
          .returning() // Assuming ID is the primary key column
          .fetchOne()
          .into(classOf[DatasetVersion])
    }
  }

  def retrieveListOfOwnerAndFiles(): Map[UserKey, Map[Path, String]] = {
    // Assuming you want to fetch UID, USERNAME, and perhaps FILE.NAME for simplicity
    val result: Result[Record4[UInteger, String, String, String]] = context
      .select(USER.UID, USER.EMAIL, FILE.NAME, FILE.PATH)
      .from(FILE)
      .join(USER).on(FILE.OWNER_UID.eq(USER.UID))
      .orderBy(USER.UID) // Assuming you want to order by UID for easier processing
      .fetch()

    // Process the result to build the map with Path objects as keys
    val userToFileMap: Map[UserKey, Map[Path, String]] = result.asScala
      .toList // Convert to Scala List
      .groupBy(record => UserKey(record.getValue(USER.EMAIL), record.getValue(USER.UID))) // Group by user name
      .mapValues { records =>
        records.map { record =>
          val pathString = record.getValue(FILE.PATH)
          val path = Paths.get(pathString) // Convert string to Path
          val name = record.getValue(FILE.NAME)
          path -> name // Use Path object as the key
        }.toMap // Convert each list of pairs to a map
      }.toMap

    userToFileMap
  }

  def refactorFilePath(): Unit = {
    // 1. Fetch all file records
    val result: Result[Record2[UInteger, String]] = context
      .select(FILE.FID, FILE.PATH)
      .from(FILE)
      .fetch()

    // 2. Process each record to update the path
    result.forEach { record =>
      val originalPath = record.getValue(FILE.PATH)
      val newPath = originalPath.replace("/home/texera/texera/core/amber/user-resources/files", filesDirectory)

      // Here you would update the database record with the new path
      // Uncomment and modify the below lines if you decide to update the paths in the database.
      // Please ensure transaction management and error handling as necessary.
      context.update(FILE)
        .set(FILE.PATH, newPath)
        .where(FILE.FID.eq(record.getValue(FILE.FID)))
        .execute()

      // For demonstration, let's just print the old and new paths
      println(s"Old Path: $originalPath, New Path: $newPath")
    }
  }

  def findPersonalDidForUser(uid: UInteger): Option[UInteger] = {
    // Query to find the personal dataset DID for a user
    val result = context.select(DATASET.DID)
      .from(DATASET)
      .where(DATASET.OWNER_UID.eq(uid)
        .and(DATASET.DESCRIPTION.like("%personal dataset%")))
      .fetchOne()

    Option(result).map(_.getValue(DATASET.DID))
  }

  def createEnvironmentForUserWorkflows(uid: UInteger): Unit = {
    // Find all workflow IDs for the user
    val workflowIds = context.select(WORKFLOW_OF_USER.WID)
      .from(WORKFLOW_OF_USER)
      .where(WORKFLOW_OF_USER.UID.eq(uid))
      .map(record => record.getValue(WORKFLOW_OF_USER.WID))

    val workflowIdsSet = workflowIds.asJavaCollection
    // Find workflow IDs that already have an environment
    val workflowWithEnvironmentIds = context.select(ENVIRONMENT_OF_WORKFLOW.WID)
      .from(ENVIRONMENT_OF_WORKFLOW)
      .where(ENVIRONMENT_OF_WORKFLOW.WID.in(workflowIdsSet))
      .map(record => record.getValue(ENVIRONMENT_OF_WORKFLOW.WID))

    // Find workflow IDs without an environment by subtracting the above set from all workflow IDs
    val workflowIdsWithoutEnvironment = workflowIds.toSet -- workflowWithEnvironmentIds.toSet
    val environmentOfWorkflowDao = new EnvironmentOfWorkflowDao(context.configuration())
    // For each workflow without an environment, create a new environment
    workflowIdsWithoutEnvironment.foreach { wid =>
      // Placeholder for environment creation logic
      // createEnvironmentForWorkflow(wid)
      println(s"Creating environment for workflow ID: $wid")
      val environment = new Environment();
      environment.setName(
        s"Environment of Workflow #$wid"
      )
      environment.setDescription(
        s"Runtime Environment of Workflow #$wid"
      )
      environment.setOwnerUid(uid)

      val createdEnvironment = context
        .insertInto(ENVIRONMENT)
        .set(context.newRecord(ENVIRONMENT, environment))
        .returning()
        .fetchOne()
        .into(classOf[Environment])

      environmentOfWorkflowDao.insert(new EnvironmentOfWorkflow(createdEnvironment.getEid, wid))
    }
  }

  // Extension method to fetch results as Scala List
  implicit class RichResult[R <: Record](result: Result[R]) {
    def fetchAsScala: List[R] = result.asScala.toList
  }

  def adjustUserAccessToDataset(uid: UInteger, did: UInteger): Unit = {
    val datasetUserAccessDao = new DatasetUserAccessDao(context.configuration())
    val workflowIds: util.List[UInteger] = context.select(WORKFLOW_OF_USER.WID)
      .from(WORKFLOW_OF_USER)
      .where(WORKFLOW_OF_USER.UID.eq(uid))
      .fetch()
      .getValues(WORKFLOW_OF_USER.WID, classOf[UInteger])

    workflowIds.foreach { wid =>
      val userAccessToWorkflow = context.select(WORKFLOW_USER_ACCESS.UID)
        .from(WORKFLOW_USER_ACCESS)
        .where(WORKFLOW_USER_ACCESS.WID.eq(wid))
        .fetch()
        .getValues(WORKFLOW_USER_ACCESS.UID, classOf[UInteger])

      userAccessToWorkflow.foreach { accessUid =>
        try {
          val datasetUserAccess = new DatasetUserAccess(did, accessUid, DatasetUserAccessPrivilege.READ)
          datasetUserAccessDao.insert(datasetUserAccess)
        } catch {
          case e: Exception => print("do nothing")// Ignore or log the exception if the access already exists
        }
      }
    }
  }

  def addPersonalDatasetToAllWorkflowOfAnUser(uid: UInteger, did: UInteger) = {
    // Find all workflow IDs for the user
    val workflowIds = context.select(WORKFLOW_OF_USER.WID)
      .from(WORKFLOW_OF_USER)
      .where(WORKFLOW_OF_USER.UID.eq(uid))
      .map(record => record.getValue(WORKFLOW_OF_USER.WID))

    // For each workflow, find the unique environment ID
    val environmentIds = workflowIds.map(wid => context.select(ENVIRONMENT_OF_WORKFLOW.EID)
      .from(ENVIRONMENT_OF_WORKFLOW)
      .where(ENVIRONMENT_OF_WORKFLOW.WID.eq(wid))
      .fetchOne()
      .getValue(ENVIRONMENT_OF_WORKFLOW.EID)
    )

    // Find the single dvid for the given did from dataset_version
    val dvid = context.select(DATASET_VERSION.DVID)
      .from(DATASET_VERSION)
      .where(DATASET_VERSION.DID.eq(did))
      .fetchOne()
      .map(_.getValue(DATASET_VERSION.DVID))

    if (dvid != null) {
      // For each environment, insert the did and dvid into dataset_of_environment
      environmentIds.foreach { eid =>
        context.insertInto(DATASET_OF_ENVIRONMENT,
            DATASET_OF_ENVIRONMENT.DID, DATASET_OF_ENVIRONMENT.EID, DATASET_OF_ENVIRONMENT.DVID)
          .values(did, eid, dvid)
          .execute()
      }
    }
  }

//  refactorFilePath()
//  val userToUserFiles = retrieveListOfOwnerAndFiles()
//
//  for ((userKey, userFiles) <- userToUserFiles) {
//    createDataset(userKey, userToUserFiles)
//  }

  val USERS = DSL.table("user") // Replace with your actual table name
  val USER_ID = DSL.field("uid", classOf[UInteger]) // Replace with your actual user ID field name

  // Fetch all user IDs as a Scala List[UInteger]
  val userIds: List[UInteger] = context.select(USER_ID).from(USERS)
    .fetch()
    .asScala
    .toList
    .map(record => record.getValue(USER_ID, classOf[UInteger]))

  // Iterate over the list of user IDs
  for (uid <- userIds) {
    // Your iteration logic here
//    createEnvironmentForUserWorkflows(uid)
    val did = findPersonalDidForUser(uid)
    did match {
      case Some(d) => {
//        addPersonalDatasetToAllWorkflowOfAnUser(uid, d)
        adjustUserAccessToDataset(uid, d)
      }
      case None => println("do nothing")
    }
  }
}
