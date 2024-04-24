package edu.uci.ics.amber.engine.common.storage.file.localfs
import edu.uci.ics.amber.engine.common.storage.{TexeraDocument, TexeraURI}

import java.io.{InputStream, OutputStream}
import java.nio.file.{Files, Path, Paths, StandardCopyOption}

/**
  * GitVersionControlledDocument supports the version control over a file on local file system
  * - the version control features are based on JGit
  * - this collection can only be used for the file in local file system
  * - operations in this implementation are THREAD-UNSAFE
  * @param gitRepoURI the uri of the git repo
  * @param uri the uri of the file, must be the child directory under the git repo
  * @param commitHash which version does this file locate. It is optional, as when you want to create a new version(commit), you have no version to specify
  */
class GitVersionControlledDocument(
    val gitRepoURI: TexeraURI,
    val uri: TexeraURI,
    val commitHash: Option[String]
) extends TexeraDocument[AnyRef] {
  require(gitRepoURI.getScheme == TexeraURI.FILE_SCHEMA, "Given URI should be a File URI")
  require(uri.getScheme == TexeraURI.FILE_SCHEMA, "Given URI should be a File URI")
  require(
    gitRepoURI.containsChildPath(uri),
    "Given git repo URI must be the parent of document uri"
  )

  private val gitRepoPath: Path = Paths.get(gitRepoURI.getURI)
  private val path: Path = Paths.get(uri.getURI)
  override def getURI: TexeraURI = uri

  override def writeWithStream(inputStream: InputStream): Unit = {
    // Ensure the parent directory exists before copying the file
    val parentDir = path.getParent
    if (parentDir != null) {
      Files.createDirectories(parentDir)
    }

    Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING)
    JGitVersionControlUtils.add(gitRepoPath, path)
  }

  override def readAsOutputStream(outputStream: OutputStream): Unit = {
    JGitVersionControlUtils.readFileContentOfCommitAsOutputStream(
      gitRepoPath,
      commitHash.get,
      path,
      outputStream
    )
  }

  override def readAsInputStream(): InputStream = {
    JGitVersionControlUtils.readFileContentOfCommitAsInputStream(gitRepoPath, commitHash.get, path)
  }

  override def copy(to: Option[TexeraURI]): TexeraURI = {
    to match {
      case Some(targetUri) =>
        // Extract the path from the target URI and copy the file content to it
        val targetPath = Paths.get(targetUri.getURI)
        Files.createDirectories(targetPath.getParent) // Ensure target directory exists
        Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING)
        targetUri // Return the target URI as copying was to a specified location

      case None =>
        // Generate a temporary file and copy the content to it
        val tempFile = Files.createTempFile("copy", ".tmp")
        Files.copy(path, tempFile, StandardCopyOption.REPLACE_EXISTING)

        // Create a new TexeraURI for the temporary file and return it
        TexeraURI(tempFile)
    }
  }

  override def rm(): Unit = {
    if (Files.isDirectory(path))
      throw new IllegalArgumentException("Provided path is a directory, not a file: " + path)

    Files.delete(path)
    JGitVersionControlUtils.rm(gitRepoPath, path)
  }
}
