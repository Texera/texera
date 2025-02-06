package edu.uci.ics.amber.engine.common.storage.file

import edu.uci.ics.amber.engine.common.storage.VirtualCollection

/**
  * VersionControlledVirtualCollection provides the abstraction for collections with version control capability
  */
abstract class VersionControlledCollection extends VirtualCollection {

  /**
    * initialize the version storage. E.g. for a git-based implementation, this function would do `git init` at certain directory
    */
  def initVersionStorage(): Unit

  /**
    * create a new version and return the versionID
    * @param versionName the name of the version
    * @param operations the modifications when creating the version
    * @return versionID
    */
  def withCreateVersion(versionName: String)(operations: => Unit): VersionIdentifier
}
