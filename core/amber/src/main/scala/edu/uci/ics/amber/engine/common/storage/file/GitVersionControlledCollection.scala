package edu.uci.ics.amber.engine.common.storage.file

import edu.uci.ics.amber.engine.common.storage.VirtualDocument
import edu.uci.ics.amber.engine.common.storage.file.utils.JGitVersionControlUtils

import java.net.URI
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.util.Using

/**
  * GitVersionControlledCollection supports the version control over a directory on local file system.
  * - the version control features are based on JGit
  * - this collection can only be used for the directory in local file system
  * - operations in this implementation are THREAD-UNSAFE
  * @param gitRepoURI the uri of the git repo
  * @param uri the uri of the directory, must be the child directory under the git repo
  * @param versionID which version does this directory locate. It is optional, as when you want to create a new version(commit), you have no version to specify
  */
class GitVersionControlledCollection(
    val gitRepoURI: URI,
    val uri: URI,
    val versionID: VersionIdentifier
) extends VersionControlledCollection
    with FileTreeDisplayable {
  private val allowedURISchemas = Set("file")

  require(
    allowedURISchemas.contains(gitRepoURI.getScheme),
    "Given gitRepoURI's schema is not allowed"
  )
  require(allowedURISchemas.contains(uri.getScheme), "Given collection URI's schema is not allowed")
  private val gitRepoPath: Path = Paths.get(gitRepoURI)
  private val path: Path = Paths.get(uri)

  require(
    gitRepoPath.equals(path) || path.startsWith(gitRepoPath),
    "Given git repo URI must be equal to or be the parent of uri"
  )

  override def getURI: URI = uri
  override def initVersionStorage(): Unit = {
    if (gitRepoPath != path) {
      throw new RuntimeException("Current Collection is not able to be inited")
    }
    JGitVersionControlUtils.initRepo(path)
  }

  override def withCreateVersion(versionName: String)(operations: => Unit): VersionIdentifier = {
    if (gitRepoPath != path) {
      throw new RuntimeException("Current Collection is not the root of the version store")
    }
    operations
    new VersionIdentifier(Some(JGitVersionControlUtils.commit(gitRepoPath, versionName)))
  }

  override def getDocuments: List[VirtualDocument[_]] = {
    getFileTreeNodes
      .filter(node => node.isFile && node.getAbsolutePath.getParent == path)
      .map(node =>
        new GitVersionControlledDocument(
          gitRepoURI,
          node.getAbsolutePath.toUri,
          versionID
        )
      )
  }

  override def getDocument(name: String): VirtualDocument[_] = {
    val docURI = path.resolve(name).toUri
    new GitVersionControlledDocument(gitRepoURI, docURI, versionID)
  }

  override def remove(): Unit = {
    Using(Files.walk(path)) { paths =>
      paths
        .iterator()
        .asScala
        .toSeq
        .reverse
        .foreach(path => Files.delete(path))
    }.get
  }

  override def getFileTreeNodes: List[FileTreeNode] = {
    JGitVersionControlUtils.getFileTreeNodesOfCommit(gitRepoPath, versionID.getGitVersionHash.get)
  }
}
