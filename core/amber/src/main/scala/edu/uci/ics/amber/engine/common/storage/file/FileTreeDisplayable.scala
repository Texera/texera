package edu.uci.ics.amber.engine.common.storage.file

/**
  * FileTreeDisplayable is implemented when a object wants to display the file tree structure
  */
trait FileTreeDisplayable {
  def getFileTreeNodes: List[FileTreeNode]
}
