package edu.uci.ics.amber.engine.common.storage.file.localfs

import edu.uci.ics.amber.engine.common.storage.{TexeraDocument, TexeraURI, file}
import edu.uci.ics.amber.engine.common.storage.file.{FileTreeNode, VersionControlledCollection}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, OutputStream}
import java.nio.file.{Files, Path, Paths}
import scala.collection.mutable.ListBuffer
class VersionControlledStorageSpec extends AnyFlatSpecLike with BeforeAndAfterAll {
  private var testRepoPath: Path = _
  private var testRepoURI: TexeraURI = _

  private var testRepoMasterCommitHashes: ListBuffer[String] = _

  private val testFile1Name = "testFile1.txt"

  private val testFile2Name = "testFile2.txt"
  private val testDirectoryName = "testDir"

  private val testFile1ContentV1 = "This is a test file1 v1"
  private val testFile1ContentV2 = "This is a test file1 v2"
  private val testFile1ContentV3 = "This is a test file1 v3"

  private val testFile2Content = "This is a test file2 in the testDir"

  private def writeToDocument(document: TexeraDocument[_], content: String): Unit = {
    val input = new ByteArrayInputStream(content.getBytes)
    try {
      document.writeWithStream(input)
    } finally {
      input.close()
    }
  }
  private def initVersionStore(repoURI: TexeraURI): Unit = {
    val versionRepo: VersionControlledCollection =
      new GitVersionControlledCollection(repoURI, repoURI, None)
    versionRepo.initVersionStore()
  }

  private def createVersionByAddOneFile(
      repoURI: TexeraURI,
      fileURI: TexeraURI,
      versionName: String,
      fileContent: String
  ): String = {
    val versionRepo: VersionControlledCollection =
      new GitVersionControlledCollection(repoURI, repoURI, None)
    versionRepo.withCreateVersion(versionName) {
      val doc = new GitVersionControlledDocument(repoURI, fileURI, None)
      writeToDocument(doc, fileContent)
    }
  }

  private def createVersionByDeleteOneFile(
      repoURI: TexeraURI,
      fileURI: TexeraURI,
      versionName: String
  ): String = {
    val versionRepo: VersionControlledCollection =
      new GitVersionControlledCollection(repoURI, repoURI, None)
    versionRepo.withCreateVersion(versionName)({
      val doc = new GitVersionControlledDocument(repoURI, fileURI, None)
      doc.rm()
    })
  }

  private def retrieveFileContentOfOneVersion(
      repoURI: TexeraURI,
      fileURI: TexeraURI,
      commitHash: String
  ): String = {
    val document: TexeraDocument[_] =
      new GitVersionControlledDocument(repoURI, fileURI, Some(commitHash))

    val output: OutputStream = new ByteArrayOutputStream()
    document.readAsOutputStream(output)

    output.toString
  }

  override def beforeAll(): Unit = {
//    testRepoPath = Files.createTempDirectory("testRepo")
    testRepoPath = Paths.get(
      "/Users/baijiadong/Desktop/chenlab/texera/core/amber/user-resources/datasets/testDir"
    )
    testRepoURI = TexeraURI(testRepoPath)

    initVersionStore(testRepoURI)

    val fileURI = TexeraURI(testRepoPath.resolve(testFile1Name))

    val versionBuffer: ListBuffer[String] = new ListBuffer[String]
    versionBuffer += createVersionByAddOneFile(testRepoURI, fileURI, "v1", testFile1ContentV1)
    versionBuffer += createVersionByAddOneFile(testRepoURI, fileURI, "v2", testFile1ContentV2)
    versionBuffer += createVersionByAddOneFile(testRepoURI, fileURI, "v3", testFile1ContentV3)

    testRepoMasterCommitHashes = versionBuffer
  }

  override def afterAll(): Unit = {
    // delete the whole repo
    val testRepo: VersionControlledCollection =
      new GitVersionControlledCollection(testRepoURI, testRepoURI, None)
    testRepo.rm()
  }

  "File content" should "match across versions" in {
    val filePath = testRepoPath.resolve(testFile1Name)
    val fileURI: TexeraURI = TexeraURI(filePath)

    retrieveFileContentOfOneVersion(
      testRepoURI,
      fileURI,
      testRepoMasterCommitHashes.head
    ) should be(testFile1ContentV1)
    retrieveFileContentOfOneVersion(testRepoURI, fileURI, testRepoMasterCommitHashes(1)) should be(
      testFile1ContentV2
    )
    retrieveFileContentOfOneVersion(testRepoURI, fileURI, testRepoMasterCommitHashes(2)) should be(
      testFile1ContentV3
    )
  }

  "File tree nodes" should "be consistent" in {
    val file1Path = testRepoPath.resolve(testFile1Name)
    val file1Node: FileTreeNode = new FileTreeNode(testRepoPath, file1Path)
    val fileNodes = ListBuffer(file1Node)

    val testRepoV3: VersionControlledCollection = new GitVersionControlledCollection(
      testRepoURI,
      testRepoURI,
      Some(testRepoMasterCommitHashes.last)
    )
    testRepoV3.getFileTreeNodes.toSet should be(fileNodes.toSet)

    val testDirPath = testRepoPath.resolve(testDirectoryName)
    val file2Path = testDirPath.resolve(testFile2Name)
    val file2URI = TexeraURI(file2Path)
    val v4Hash = createVersionByAddOneFile(testRepoURI, file2URI, "v4", testFile2Content)
    testRepoMasterCommitHashes += v4Hash

    val file2Node: FileTreeNode = new FileTreeNode(testRepoPath, file2Path)
    val dirNode: FileTreeNode = new FileTreeNode(testRepoPath, testDirPath)
    dirNode.addChildNode(file2Node)
    fileNodes += dirNode

    val testRepoV4: VersionControlledCollection = new GitVersionControlledCollection(
      testRepoURI,
      testRepoURI,
      Some(testRepoMasterCommitHashes.last)
    )
    testRepoV4.getFileTreeNodes.toSet should be(fileNodes.toSet)

    val v5Hash = createVersionByDeleteOneFile(testRepoURI, file2URI, "v5")
    testRepoMasterCommitHashes += v5Hash

    fileNodes.dropRightInPlace(1)
    val testRepoV5: VersionControlledCollection = new GitVersionControlledCollection(
      testRepoURI,
      testRepoURI,
      Some(testRepoMasterCommitHashes.last)
    )
    testRepoV5.getFileTreeNodes.toSet should be(fileNodes.toSet)
  }
}
