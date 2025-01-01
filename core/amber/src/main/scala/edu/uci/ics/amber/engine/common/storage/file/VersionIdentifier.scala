package edu.uci.ics.amber.engine.common.storage.file

/**
  * VersionIdentifier identifies a version that is managed by Collection. The Collection must be a subclass of VersionControlledCollection
  * @param gitVersionHash the git commit hash. it is used to identify a version managed using git-based implementation
  */
class VersionIdentifier(val gitVersionHash: Option[String] = None) {
  def getGitVersionHash: Option[String] = gitVersionHash
}
