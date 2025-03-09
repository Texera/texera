package edu.uci.ics.amber.engine.common.storage.file

import edu.uci.ics.amber.engine.common.storage.VirtualDocument
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import java.net.URI
import java.nio.file.{Files, Path}
import scala.collection.mutable.ListBuffer
class GitVersionControlledStorageSpec extends AnyFlatSpecLike with BeforeAndAfterAll {
  private var testRepoPath: Path = _
  private var testRepoURI: URI = _

  private var testRepoMasterCommitHashes: ListBuffer[VersionIdentifier] = _

  private val testFile1Name = "testFile1.txt"

  private val testFile2Name = "testFile2.txt"
  private val testDirectoryName = "testDir"

  private val testFile1ContentV1 = "This is a test file1 v1"
  private val testFile1ContentV2 = "This is a test file1 v2"
  private val testFile1ContentV3 = "This is a test file1 v3"

  private val testFile2Content = "This is a test file2 in the testDir"

  private def writeToDocument(document: VirtualDocument[_], content: String): Unit = {
    val input = new ByteArrayInputStream(content.getBytes)
    try {
      document.writeWithStream(input)
    } finally {
      input.close()
    }
  }
  private def initVersionStore(repoURI: URI): Unit = {
    val versionRepo: VersionControlledCollection =
      new GitVersionControlledCollection(repoURI, repoURI, new VersionIdentifier())
    versionRepo.initVersionStorage()
  }

  private def createVersionByAddOneFile(
      repoURI: URI,
      fileURI: URI,
      versionName: String,
      fileContent: String
  ): VersionIdentifier = {
    val versionRepo: VersionControlledCollection =
      new GitVersionControlledCollection(repoURI, repoURI, new VersionIdentifier())
    versionRepo.withCreateVersion(versionName) {
      val doc = new GitVersionControlledDocument(repoURI, fileURI, new VersionIdentifier())
      writeToDocument(doc, fileContent)
    }
  }

  private def createVersionByDeleteOneFile(
      repoURI: URI,
      fileURI: URI,
      versionName: String
  ): VersionIdentifier = {
    val versionRepo: VersionControlledCollection =
      new GitVersionControlledCollection(repoURI, repoURI, new VersionIdentifier)
    versionRepo.withCreateVersion(versionName)({
      val doc = new GitVersionControlledDocument(repoURI, fileURI, new VersionIdentifier)
      doc.remove()
    })
  }

  private def retrieveFileContentOfOneVersion(
      repoURI: URI,
      fileURI: URI,
      versionIdentifier: VersionIdentifier
  ): String = {
    val document: VirtualDocument[_] =
      new GitVersionControlledDocument(repoURI, fileURI, versionIdentifier)

    val output: ByteArrayOutputStream = new ByteArrayOutputStream()
    val is: InputStream = document.asInputStream()
    val buffer = new Array[Byte](1024) // Buffer size of 1024 bytes
    var len: Int = is.read(buffer)
    while (len != -1) {
      output.write(buffer, 0, len)
      len = is.read(buffer)
    }
    is.close()
    output.toString
  }

  override def beforeAll(): Unit = {
    testRepoPath = Files.createTempDirectory("testRepo")
    testRepoURI = testRepoPath.toUri

    initVersionStore(testRepoURI)

    val fileURI = testRepoPath.resolve(testFile1Name).toUri

    val versionBuffer: ListBuffer[VersionIdentifier] = new ListBuffer[VersionIdentifier]
    versionBuffer += createVersionByAddOneFile(testRepoURI, fileURI, "v1", testFile1ContentV1)
    versionBuffer += createVersionByAddOneFile(testRepoURI, fileURI, "v2", testFile1ContentV2)
    versionBuffer += createVersionByAddOneFile(testRepoURI, fileURI, "v3", testFile1ContentV3)

    testRepoMasterCommitHashes = versionBuffer
  }

  override def afterAll(): Unit = {
    // delete the whole repo
    val testRepo: VersionControlledCollection =
      new GitVersionControlledCollection(testRepoURI, testRepoURI, new VersionIdentifier)
    testRepo.remove()
  }

  "File content" should "match across versions" in {
    val filePath = testRepoPath.resolve(testFile1Name)
    val fileURI = filePath.toUri

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

    val testRepoV3: GitVersionControlledCollection = new GitVersionControlledCollection(
      testRepoURI,
      testRepoURI,
      testRepoMasterCommitHashes.last
    )
    testRepoV3.getFileTreeNodes.toSet should be(fileNodes.toSet)

    val testDirPath = testRepoPath.resolve(testDirectoryName)
    val file2Path = testDirPath.resolve(testFile2Name)
    val file2URI = file2Path.toUri
    val v4Hash = createVersionByAddOneFile(testRepoURI, file2URI, "v4", testFile2Content)
    testRepoMasterCommitHashes += v4Hash

    val file2Node: FileTreeNode = new FileTreeNode(testRepoPath, file2Path)
    val dirNode: FileTreeNode = new FileTreeNode(testRepoPath, testDirPath)
    dirNode.addChildNode(file2Node)
    fileNodes += dirNode

    val testRepoV4: GitVersionControlledCollection = new GitVersionControlledCollection(
      testRepoURI,
      testRepoURI,
      testRepoMasterCommitHashes.last
    )
    testRepoV4.getFileTreeNodes.toSet should be(fileNodes.toSet)

    val v5Hash = createVersionByDeleteOneFile(testRepoURI, file2URI, "v5")
    testRepoMasterCommitHashes += v5Hash

    fileNodes.dropRightInPlace(1)
    val testRepoV5: GitVersionControlledCollection = new GitVersionControlledCollection(
      testRepoURI,
      testRepoURI,
      testRepoMasterCommitHashes.last
    )
    testRepoV5.getFileTreeNodes.toSet should be(fileNodes.toSet)
  }
}
