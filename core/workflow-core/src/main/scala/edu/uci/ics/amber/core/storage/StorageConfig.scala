package edu.uci.ics.amber.core.storage

import edu.uci.ics.amber.util.PathUtils.corePath
import org.yaml.snakeyaml.Yaml

import java.nio.file.Path
import java.util.{Map => JMap}
import scala.jdk.CollectionConverters._

object StorageConfig {
  private val conf: Map[String, Any] = {
    val yaml = new Yaml()
    val inputStream = getClass.getClassLoader.getResourceAsStream("storage-config.yaml")
    val javaConf = yaml.load(inputStream).asInstanceOf[JMap[String, Any]].asScala.toMap

    val storageMap = javaConf("storage").asInstanceOf[JMap[String, Any]].asScala.toMap
    val mongodbMap = storageMap("mongodb").asInstanceOf[JMap[String, Any]].asScala.toMap
    val icebergMap = storageMap("iceberg").asInstanceOf[JMap[String, Any]].asScala.toMap
    val icebergCatalogMap = icebergMap("catalog").asInstanceOf[JMap[String, Any]].asScala.toMap
    val icebergPostgresMap =
      icebergCatalogMap("postgres").asInstanceOf[JMap[String, Any]].asScala.toMap
    val icebergTableMap = icebergMap("table").asInstanceOf[JMap[String, Any]].asScala.toMap
    val icebergCommitMap = icebergTableMap("commit").asInstanceOf[JMap[String, Any]].asScala.toMap
    val icebergRetryMap = icebergCommitMap("retry").asInstanceOf[JMap[String, Any]].asScala.toMap
    val jdbcMap = storageMap("jdbc").asInstanceOf[JMap[String, Any]].asScala.toMap
    val lakefsMap = storageMap("lakefs").asInstanceOf[JMap[String, Any]].asScala.toMap
    val lakefsAuthMap = lakefsMap("auth").asInstanceOf[JMap[String, Any]].asScala.toMap
    val lakefsBlockStorageMap =
      lakefsMap("block-storage").asInstanceOf[JMap[String, Any]].asScala.toMap
    val s3Map = storageMap("s3").asInstanceOf[JMap[String, Any]].asScala.toMap
    val s3AuthMap = s3Map("auth").asInstanceOf[JMap[String, Any]].asScala.toMap

    javaConf.updated(
      "storage",
      storageMap
        .updated("mongodb", mongodbMap)
        .updated(
          "iceberg",
          icebergMap
            .updated(
              "table",
              icebergTableMap.updated(
                "commit",
                icebergCommitMap.updated("retry", icebergRetryMap)
              )
            )
            .updated(
              "catalog",
              icebergCatalogMap.updated("postgres", icebergPostgresMap)
            )
        )
        .updated("jdbc", jdbcMap)
        .updated(
          "lakefs",
          lakefsMap.updated("auth", lakefsAuthMap).updated("block-storage", lakefsBlockStorageMap)
        )
        .updated("s3", s3Map.updated("auth", s3AuthMap))
    )
  }

  // Result storage mode
  val resultStorageMode: String =
    conf("storage").asInstanceOf[Map[String, Any]]("result-storage-mode").asInstanceOf[String]

  // MongoDB configurations
  val mongodbUrl: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("mongodb")
    .asInstanceOf[Map[String, Any]]("url")
    .asInstanceOf[String]

  val mongodbDatabaseName: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("mongodb")
    .asInstanceOf[Map[String, Any]]("database")
    .asInstanceOf[String]

  val mongodbBatchSize: Int = conf("storage")
    .asInstanceOf[Map[String, Any]]("mongodb")
    .asInstanceOf[Map[String, Any]]("commit-batch-size")
    .asInstanceOf[Int]

  // Iceberg table configurations
  val icebergTableResultNamespace: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("iceberg")
    .asInstanceOf[Map[String, Any]]("table")
    .asInstanceOf[Map[String, Any]]("result-namespace")
    .asInstanceOf[String]

  val icebergTableConsoleMessagesNamespace: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("iceberg")
    .asInstanceOf[Map[String, Any]]("table")
    .asInstanceOf[Map[String, Any]]("console-messages-namespace")
    .asInstanceOf[String]

  val icebergTableRuntimeStatisticsNamespace: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("iceberg")
    .asInstanceOf[Map[String, Any]]("table")
    .asInstanceOf[Map[String, Any]]("runtime-statistics-namespace")
    .asInstanceOf[String]

  val icebergTableCommitBatchSize: Int = conf("storage")
    .asInstanceOf[Map[String, Any]]("iceberg")
    .asInstanceOf[Map[String, Any]]("table")
    .asInstanceOf[Map[String, Any]]("commit")
    .asInstanceOf[Map[String, Any]]("batch-size")
    .asInstanceOf[Int]

  val icebergTableCommitNumRetries: Int = conf("storage")
    .asInstanceOf[Map[String, Any]]("iceberg")
    .asInstanceOf[Map[String, Any]]("table")
    .asInstanceOf[Map[String, Any]]("commit")
    .asInstanceOf[Map[String, Any]]("retry")
    .asInstanceOf[Map[String, Any]]("num-retries")
    .asInstanceOf[Int]

  val icebergTableCommitMinRetryWaitMs: Int = conf("storage")
    .asInstanceOf[Map[String, Any]]("iceberg")
    .asInstanceOf[Map[String, Any]]("table")
    .asInstanceOf[Map[String, Any]]("commit")
    .asInstanceOf[Map[String, Any]]("retry")
    .asInstanceOf[Map[String, Any]]("min-wait-ms")
    .asInstanceOf[Int]

  val icebergTableCommitMaxRetryWaitMs: Int = conf("storage")
    .asInstanceOf[Map[String, Any]]("iceberg")
    .asInstanceOf[Map[String, Any]]("table")
    .asInstanceOf[Map[String, Any]]("commit")
    .asInstanceOf[Map[String, Any]]("retry")
    .asInstanceOf[Map[String, Any]]("max-wait-ms")
    .asInstanceOf[Int]

  // Iceberg catalog configurations
  val icebergCatalogType: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("iceberg")
    .asInstanceOf[Map[String, Any]]("catalog")
    .asInstanceOf[Map[String, Any]]("type")
    .asInstanceOf[String]

  val icebergRESTCatalogUri: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("iceberg")
    .asInstanceOf[Map[String, Any]]("catalog")
    .asInstanceOf[Map[String, Any]]("rest-uri")
    .asInstanceOf[String]

  val icebergPostgresCatalogUriWithoutScheme: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("iceberg")
    .asInstanceOf[Map[String, Any]]("catalog")
    .asInstanceOf[Map[String, Any]]("postgres")
    .asInstanceOf[Map[String, Any]]("uri-without-scheme")
    .asInstanceOf[String]

  val icebergPostgresCatalogUsername: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("iceberg")
    .asInstanceOf[Map[String, Any]]("catalog")
    .asInstanceOf[Map[String, Any]]("postgres")
    .asInstanceOf[Map[String, Any]]("username")
    .asInstanceOf[String]

  val icebergPostgresCatalogPassword: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("iceberg")
    .asInstanceOf[Map[String, Any]]("catalog")
    .asInstanceOf[Map[String, Any]]("postgres")
    .asInstanceOf[Map[String, Any]]("password")
    .asInstanceOf[String]

  // JDBC configurations
  val jdbcUrl: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("jdbc")
    .asInstanceOf[Map[String, Any]]("url")
    .asInstanceOf[String]

  val jdbcUsername: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("jdbc")
    .asInstanceOf[Map[String, Any]]("username")
    .asInstanceOf[String]

  val jdbcPassword: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("jdbc")
    .asInstanceOf[Map[String, Any]]("password")
    .asInstanceOf[String]

  // File storage configurations
  val fileStorageDirectoryPath: Path =
    corePath.resolve("amber").resolve("user-resources").resolve("workflow-results")

  // LakeFS configurations
  val lakefsEndpoint: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("lakefs")
    .asInstanceOf[Map[String, Any]]("endpoint")
    .asInstanceOf[String]

  val lakefsUsername: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("lakefs")
    .asInstanceOf[Map[String, Any]]("auth")
    .asInstanceOf[Map[String, Any]]("username")
    .asInstanceOf[String]

  val lakefsPassword: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("lakefs")
    .asInstanceOf[Map[String, Any]]("auth")
    .asInstanceOf[Map[String, Any]]("password")
    .asInstanceOf[String]

  // LakeFS Block Storage configurations
  val lakefsBlockStorageType: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("lakefs")
    .asInstanceOf[Map[String, Any]]("block-storage")
    .asInstanceOf[Map[String, Any]]("type")
    .asInstanceOf[String]

  val lakefsBlockStorageBucketName: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("lakefs")
    .asInstanceOf[Map[String, Any]]("block-storage")
    .asInstanceOf[Map[String, Any]]("bucket-name")
    .asInstanceOf[String]

  val s3Endpoint: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("s3")
    .asInstanceOf[Map[String, Any]]("endpoint")
    .asInstanceOf[String]

  val s3Region: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("s3")
    .asInstanceOf[Map[String, Any]]("region")
    .asInstanceOf[String]

  val s3Username: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("s3")
    .asInstanceOf[Map[String, Any]]("auth")
    .asInstanceOf[Map[String, Any]]("username")
    .asInstanceOf[String]

  val s3Password: String = conf("storage")
    .asInstanceOf[Map[String, Any]]("s3")
    .asInstanceOf[Map[String, Any]]("auth")
    .asInstanceOf[Map[String, Any]]("password")
    .asInstanceOf[String]
}
