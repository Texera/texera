package edu.uci.ics.texera.web

import edu.uci.ics.texera.web.model.jooq.generated.enums.DatasetUserAccessPrivilege
import edu.uci.ics.texera.web.model.jooq.generated.tables.pojos.{Dataset, DatasetUserAccess, User}
import org.jooq.types.UInteger
import edu.uci.ics.texera.web.model.jooq.generated.tables.File.FILE
import edu.uci.ics.texera.web.model.jooq.generated.tables.User.USER
import edu.uci.ics.texera.web.model.jooq.generated.tables.Dataset.DATASET
import edu.uci.ics.texera.web.model.jooq.generated.tables.DatasetVersion.DATASET_VERSION
import edu.uci.ics.texera.web.model.jooq.generated.tables.pojos.DatasetVersion
import edu.uci.ics.texera.web.model.jooq.generated.tables.daos.DatasetUserAccessDao
import edu.uci.ics.texera.web.resource.dashboard.user.dataset.service.GitVersionControlLocalFileStorage
import edu.uci.ics.texera.web.resource.dashboard.user.dataset.utils.PathUtils
import org.jooq.{Record2, Record3, Record4, Result}
import org.jooq.impl.DSL

import java.io.FileInputStream
import java.nio.file.{Path, Paths}
import scala.jdk.CollectionConverters.CollectionHasAsScala


object FilesToDatasetMigration extends App {
  val filesDirectory = ""
  val datasetDirectory = ""
  private val context = SqlServer.createDSLContext()

  case class UserKey(
      username: String,
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
        dataset.setName(userKey.username)
        dataset.setDescription(s"$dataset's personal dataset'")
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
              val inputStream = new FileInputStream(filePath.toString)
              val datasetFilePath = datasetPath.resolve(fileName)
              GitVersionControlLocalFileStorage.writeFileToRepo(datasetPath, datasetFilePath, inputStream)
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
      .select(USER.UID, USER.NAME, FILE.NAME, FILE.PATH)
      .from(FILE)
      .join(USER).on(FILE.OWNER_UID.eq(USER.UID))
      .orderBy(USER.UID) // Assuming you want to order by UID for easier processing
      .fetch()

    // Process the result to build the map with Path objects as keys
    val userToFileMap: Map[UserKey, Map[Path, String]] = result.asScala
      .toList // Convert to Scala List
      .groupBy(record => UserKey(record.getValue(USER.NAME), record.getValue(USER.UID))) // Group by user name
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
      /*
      create.update(FILE)
        .set(FILE.PATH, newPath)
        .where(FILE.FID.eq(record.getValue(FILE.FID)))
        .execute()
      */

      // For demonstration, let's just print the old and new paths
      println(s"Old Path: $originalPath, New Path: $newPath")
    }
  }

  refactorFilePath()
  val userToUserFiles = retrieveListOfOwnerAndFiles()

  for ((userKey, userFiles) <- userToUserFiles) {
    createDataset(userKey, userFiles)
  }

}
