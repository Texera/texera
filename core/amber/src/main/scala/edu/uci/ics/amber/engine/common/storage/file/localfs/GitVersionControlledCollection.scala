package edu.uci.ics.amber.engine.common.storage.file.localfs

import edu.uci.ics.amber.engine.common.storage.{TexeraDocument, TexeraURI}
import edu.uci.ics.amber.engine.common.storage.file.{FileTreeNode, VersionControlledCollection}

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
  * @param commitHash which version does this directory locate. It is optional, as when you want to create a new version(commit), you have no version to specify
  */
class GitVersionControlledCollection(
    val gitRepoURI: TexeraURI,
    val uri: TexeraURI,
    val commitHash: Option[String]
) extends VersionControlledCollection {
  require(gitRepoURI.getScheme == TexeraURI.FILE_SCHEMA, "Given URI should be a File URI")
  require(uri.getScheme == TexeraURI.FILE_SCHEMA, "Given URI should be a File URI")
  require(
    gitRepoURI == uri || gitRepoURI.containsChildPath(uri),
    "Given git repo URI must be equal to or be the parent of uri"
  )
  private val gitRepoPath: Path = Paths.get(gitRepoURI.getURI)
  private val path: Path = Paths.get(uri.getURI)

  override def getURI: TexeraURI = uri
  override def initVersionStore(): Unit = {
    if (gitRepoPath != path) {
      throw new RuntimeException("Current Collection is not able to be inited")
    }
    JGitVersionControlUtils.initRepo(path)
  }

  override def withCreateVersion(versionName: String)(operations: => Unit): String = {
    if (gitRepoPath != path) {
      throw new RuntimeException("Current Collection is not the root of the version store")
    }
    operations
    JGitVersionControlUtils.commit(gitRepoPath, versionName)
  }

  override def getDocuments: List[TexeraDocument[_]] = {
    getFileTreeNodes
      .filter(node => node.isFile && node.getAbsolutePath.getParent == path)
      .map(node =>
        new GitVersionControlledDocument(
          gitRepoURI,
          TexeraURI(node.getAbsolutePath),
          commitHash
        )
      )
  }

  override def getDocument(name: String): TexeraDocument[_] = {
    val docURI = TexeraURI(path.resolve(name))
    new GitVersionControlledDocument(gitRepoURI, docURI, commitHash)
  }

  override def rm(): Unit = {
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
    JGitVersionControlUtils.getFileTreeNodesOfCommit(gitRepoPath, commitHash.get)
  }
}
