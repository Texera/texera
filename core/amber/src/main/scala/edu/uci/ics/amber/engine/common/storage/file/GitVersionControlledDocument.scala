package edu.uci.ics.amber.engine.common.storage.file

import edu.uci.ics.amber.engine.common.storage.VirtualDocument
import edu.uci.ics.amber.engine.common.storage.file.utils.JGitVersionControlUtils

import java.io.{File, InputStream}
import java.net.URI
import java.nio.file.{Files, Path, Paths, StandardCopyOption}

/**
  * GitVersionControlledDocument supports the version control over a file on local file system
  * - the version control features are based on JGit
  * - this collection can only be used for the file in local file system
  * - operations in this implementation are THREAD-UNSAFE
  * @param gitRepoURI the uri of the git repo
  * @param uri the uri of the file, must be the child directory under the git repo
  * @param versionID which version does this file locate. It is optional, as when you want to create a new version(commit), you have no version to specify
  */
class GitVersionControlledDocument(
    val gitRepoURI: URI,
    val uri: URI,
    val versionID: VersionIdentifier
) extends VirtualDocument[AnyRef] {
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

  override def writeWithStream(inputStream: InputStream): Unit = {
    // Ensure the parent directory exists before copying the file
    val parentDir = path.getParent
    if (parentDir != null) {
      Files.createDirectories(parentDir)
    }

    Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING)
    JGitVersionControlUtils.add(gitRepoPath, path)
  }

  override def asInputStream(): InputStream = {
    JGitVersionControlUtils.readFileContentOfCommitAsInputStream(
      gitRepoPath,
      versionID.getGitVersionHash.get,
      path
    )
  }

  override def asFile(): File = {
    // generate a temporary file, its lifecycle is binded with JVM.
    val tempFile = Files.createTempFile("copy", ".tmp")
    Files.copy(path, tempFile, StandardCopyOption.REPLACE_EXISTING)

    tempFile.toFile
  }

  override def remove(): Unit = {
    if (Files.isDirectory(path))
      throw new IllegalArgumentException("Provided path is a directory, not a file: " + path)

    Files.delete(path)
    JGitVersionControlUtils.rm(gitRepoPath, path)
  }
}
