package edu.uci.ics.amber.engine.common.storage.file

import java.nio.file.{Files, Path}
import scala.collection.mutable.ListBuffer

/**
  * FileTreeNode is used to capture the tree structure of a certain directory in filesystem
  * @param rootDirectoryPath the path of the directory
  * @param path the path of the actual file/directory node under the rootDirectory
  */
class FileTreeNode(rootDirectoryPath: Path, path: Path) {
  require(
    path.startsWith(rootDirectoryPath),
    "Given file path must be the children of root directory"
  )

  private val absoluteFilePath: Path = path
  private val relativeFilePath: Path = rootDirectoryPath.relativize(absoluteFilePath)
  val children: ListBuffer[FileTreeNode] = ListBuffer()

  def isFile: Boolean = Files.isRegularFile(absoluteFilePath)

  def isDirectory: Boolean = Files.isDirectory(absoluteFilePath)

  def getAbsolutePath: Path = absoluteFilePath

  def getRelativePath: Path = relativeFilePath

  def addChildNode(child: FileTreeNode): Unit = {
    if (child.getAbsolutePath.getParent != this.absoluteFilePath) {
      throw new IllegalArgumentException("Child node is not a direct subpath of the parent node")
    }
    this.children += child
  }

  def getChildren: ListBuffer[FileTreeNode] = children

  override def equals(other: Any): Boolean =
    other match {
      case that: FileTreeNode =>
        (that canEqual this) &&
          this.absoluteFilePath == that.absoluteFilePath &&
          this.children.toList == that.children.toList // Convert to List for comparison
      case _ => false
    }

  private def canEqual(other: Any): Boolean = other.isInstanceOf[FileTreeNode]

  override def hashCode(): Int = {
    val state = Seq(absoluteFilePath, children.toList) // Convert to List for hash code
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}
