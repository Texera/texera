package Engine.FaultTolerance.Materializer

import java.io.{BufferedWriter, FileWriter}
import java.net.URI

import Engine.Common.tuple.Tuple
import Engine.Common.{InputExhausted, OperatorExecutor}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}

class HashBasedMaterializer(
    val outputPath: String,
    val index: Int,
    val hashFunc: Tuple => Int,
    val numBuckets: Int,
    val remoteHDFS: String = null
) extends OperatorExecutor {

  var writer: Array[BufferedWriter] = _

  override def open(): Unit = {
    writer = new Array[BufferedWriter](numBuckets)
    for (i <- 0 until numBuckets) {
      writer(i) = new BufferedWriter(new FileWriter(outputPath + "/" + index + "/" + i + ".tmp"))
    }
  }

  override def close(): Unit = {
    writer.foreach(_.close())
  }

  override def processTuple(tuple: Either[Tuple, InputExhausted], input: Int): scala.Iterator[Tuple] = {
    tuple match {
      case Left(t) =>
        val index = (hashFunc(t) % numBuckets + numBuckets) % numBuckets
        writer(index).write(t.mkString("|"))
        Iterator()
      case Right(_) =>
        for (i <- 0 until numBuckets) {
          writer(i).close()
        }
        if (remoteHDFS != null) {
          val fs = FileSystem.get(new URI(remoteHDFS), new Configuration())
          for (i <- 0 until numBuckets) {
            fs.copyFromLocalFile(
              new Path(outputPath + "/" + index + "/" + i + ".tmp"),
              new Path(outputPath + "/" + i + "/" + index + ".tmp")
            )
          }
          fs.close()
        }
        Iterator()
    }
  }

}
