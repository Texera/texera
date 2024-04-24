package edu.uci.ics.amber.engine.common.storage.file

import edu.uci.ics.amber.engine.common.storage.TexeraCollection

abstract class VersionControlledCollection extends TexeraCollection {
// init current collection as a empty version store
  def initVersionStore(): Unit

  def withCreateVersion(versionName: String)(operations: => Unit): String

  def getFileTreeNodes: List[FileTreeNode]
}
