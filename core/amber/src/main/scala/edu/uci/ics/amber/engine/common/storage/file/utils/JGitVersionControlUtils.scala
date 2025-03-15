package edu.uci.ics.amber.engine.common.storage.file.utils

import edu.uci.ics.amber.engine.common.storage.file.FileTreeNode
import org.eclipse.jgit.api.{Git, ResetCommand}
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk

import java.io.{IOException, InputStream, OutputStream}
import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.util.Using

/**
  * JGitVersionControlUtils provide utilities for doing git version control using JGit library
  */
object JGitVersionControlUtils {
  def initRepo(path: Path): Option[String] = {
    val gitDir = path.resolve(".git").toFile
    if (gitDir.exists) {
      throw new IOException(s"Repository already exists at $path")
    }

    Using.resource(Git.init().setDirectory(path.toFile).call()) { git =>
      val head: Ref = git.getRepository.exactRef("HEAD")
      Option(head).flatMap { h =>
        Option(h.getTarget).map(_.getName) match {
          case Some(refName) if refName.startsWith("refs/heads/") =>
            Some(refName.substring("refs/heads/".length))
          case _ => None
        }
      }
    }
  }

  def readFileContentOfCommitAsInputStream(
      repoPath: Path,
      commitHash: String,
      filePath: Path
  ): InputStream = {
    if (!filePath.startsWith(repoPath)) {
      throw new IllegalArgumentException("File path must be under the repository path.")
    }

    if (Files.isDirectory(filePath)) {
      throw new IllegalArgumentException("File path points to a directory, not a file.")
    }

    Using.Manager { use =>
      val repository = use(
        new FileRepositoryBuilder()
          .setGitDir(repoPath.resolve(".git").toFile)
          .build()
      )
      val revWalk = use(new RevWalk(repository))

      val commit = revWalk.parseCommit(repository.resolve(commitHash))
      val treeWalk =
        use(TreeWalk.forPath(repository, repoPath.relativize(filePath).toString, commit.getTree))

      if (treeWalk == null) {
        throw new IOException("File not found in commit: " + filePath)
      }
      val objectId = treeWalk.getObjectId(0)
      val loader = repository.open(objectId)

      loader.openStream()
    }.get
  }

  def readFileContentOfCommitAsOutputStream(
      repoPath: Path,
      commitHash: String,
      filePath: Path,
      outputStream: OutputStream
  ): Unit = {
    if (!filePath.startsWith(repoPath)) {
      throw new IllegalArgumentException("File path must be under the repository path.")
    }

    if (Files.isDirectory(filePath)) {
      throw new IllegalArgumentException("File path points to a directory, not a file.")
    }

    Using.Manager { use =>
      val repository = use(
        new FileRepositoryBuilder()
          .setGitDir(repoPath.resolve(".git").toFile)
          .build()
      )
      val revWalk = use(new RevWalk(repository))

      val commit = revWalk.parseCommit(repository.resolve(commitHash))
      val treeWalkOption =
        Option(TreeWalk.forPath(repository, repoPath.relativize(filePath).toString, commit.getTree))

      treeWalkOption match {
        case Some(treeWalk) =>
          val objectId = treeWalk.getObjectId(0)
          val loader = repository.open(objectId)
          loader.copyTo(outputStream)
        case None =>
          throw new IOException("File not found in commit: " + filePath)
      }
    }.get
  }

  def add(repoPath: Path, filePath: Path): Unit = {
    Using.resource(Git.open(repoPath.toFile)) { git =>
      // Stage the file addition/modification
      git.add.addFilepattern(repoPath.relativize(filePath).toString).call()
    }
  }

  def rm(repoPath: Path, filePath: Path): Unit = {
    Using.resource(Git.open(repoPath.toFile)) { git =>
      // Stage the file deletion
      git.rm.addFilepattern(repoPath.relativize(filePath).toString).call()
    }
  }

  def commit(repoPath: Path, commitMessage: String): String = {
    Using.resource(
      new FileRepositoryBuilder()
        .setGitDir(repoPath.resolve(".git").toFile)
        .readEnvironment() // Scan environment GIT_* variables
        .findGitDir() // Scan up the file system tree
        .build()
    ) { repository =>
      Using.resource(new Git(repository)) { git =>
        // Commit the changes that have been staged
        val commit: RevCommit = git.commit.setMessage(commitMessage).call()

        // Return the commit hash
        commit.getId.getName
      }
    } // This ensures any exceptions are thrown outside of the Using block
  }

  def discardUncommittedChanges(repoPath: Path): Unit = {
    Using.resource(
      new FileRepositoryBuilder()
        .setGitDir(repoPath.resolve(".git").toFile)
        .build()
    ) { repository =>
      Using.resource(new Git(repository)) { git =>
        // Reset hard to discard changes in tracked files
        git.reset.setMode(ResetCommand.ResetType.HARD).call()

        // Clean the working directory to remove untracked files
        git.clean.setCleanDirectories(true).call()
      }
    }
  }

  def hasUncommittedChanges(repoPath: Path): Boolean = {
    Using.resource(
      new FileRepositoryBuilder()
        .setGitDir(repoPath.resolve(".git").toFile)
        .readEnvironment()
        .findGitDir()
        .build()
    ) { repository =>
      Using.resource(new Git(repository)) { git =>
        val status = git.status.call()
        !status.isClean
      }
    }
  }

  private def createOrGetNode(
      map: mutable.Map[String, FileTreeNode],
      repoPath: Path,
      path: Path
  ): FileTreeNode = {
    map.getOrElseUpdate(path.toString, new FileTreeNode(repoPath, path))
  }

  private def ensureParentChildLink(
      map: mutable.Map[String, FileTreeNode],
      repoPath: Path,
      childPath: Path,
      childNode: FileTreeNode
  ): Unit = {
    val parentPath = childPath.getParent
    if (parentPath != null && parentPath != repoPath) {
      val parentNode = createOrGetNode(map, repoPath, parentPath)
      parentNode.addChildNode(childNode)
    }
  }

  def getFileTreeNodesOfCommit(repoPath: Path, commitHash: String): List[FileTreeNode] = {
    val pathToFileNodeMap = mutable.Map[String, FileTreeNode]()

    Using.resource(new FileRepositoryBuilder().setGitDir(repoPath.resolve(".git").toFile).build()) {
      repository =>
        Using.resource(new RevWalk(repository)) { revWalk =>
          val commitId = repository.resolve(commitHash)
          val commit = revWalk.parseCommit(commitId)

          Using.resource(new TreeWalk(repository)) { treeWalk =>
            treeWalk.addTree(commit.getTree)
            treeWalk.setRecursive(false)

            while (treeWalk.next()) {
              val fullPath = repoPath.resolve(treeWalk.getPathString)
              val currentNode = createOrGetNode(pathToFileNodeMap, repoPath, fullPath)

              if (treeWalk.isSubtree) {
                treeWalk.enterSubtree()
              } else {
                ensureParentChildLink(pathToFileNodeMap, repoPath, fullPath, currentNode)
              }

              if (currentNode.isDirectory) {
                ensureParentChildLink(pathToFileNodeMap, repoPath, fullPath, currentNode)
              }
            }
          }
        }
    }

    pathToFileNodeMap.values.filter(node => node.getAbsolutePath.getParent == repoPath).toList
  }
}
