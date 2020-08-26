package Engine.Operators.PythonUDF

import java.util

import Engine.Architecture.Breakpoint.GlobalBreakpoint.GlobalBreakpoint
import Engine.Architecture.DeploySemantics.DeployStrategy.RoundRobinDeployment
import Engine.Architecture.DeploySemantics.DeploymentFilter.FollowPrevious
import Engine.Architecture.DeploySemantics.Layer.{ActorLayer, ProcessorWorkerLayer}
import Engine.Architecture.Worker.WorkerState
import Engine.Common.AmberTag.{LayerTag, OperatorTag}
import Engine.Operators.OperatorMetadata
import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.util.Timeout

import collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.ExecutionContext

class PythonUDFMetadata(
                         tag: OperatorTag,
                         val numWorkers: Int,
                         val pythonScriptFile: String,
                         val inputColumns: mutable.Buffer[String],
                         val outputColumns: mutable.Buffer[String],
                         val outerFiles: mutable.Buffer[String],
                         val batchSize: Int) extends OperatorMetadata(tag) {
  override lazy val topology: Topology = {
    new Topology(
      Array(
        new ProcessorWorkerLayer(
          LayerTag(tag, "main"),
          _ => new PythonUDFTupleProcessor(
            pythonScriptFile,
            new util.ArrayList[String](inputColumns.asJava),
            new util.ArrayList[String](outputColumns.asJava),
            new util.ArrayList[String](outerFiles.asJava),
            batchSize),
          numWorkers,
          FollowPrevious(),
          RoundRobinDeployment()
        )
      ),
      Array(),
      Map()
    )
  }
  override def assignBreakpoint(
                                 topology: Array[ActorLayer],
                                 states: mutable.AnyRefMap[ActorRef, WorkerState.Value],
                                 breakpoint: GlobalBreakpoint
                               )(implicit timeout: Timeout, ec: ExecutionContext, log: LoggingAdapter): Unit = {
    breakpoint.partition(topology(0).layer.filter(states(_) != WorkerState.Completed))
  }
}
