package texera.common.operators.aggregate

import Engine.Architecture.Breakpoint.GlobalBreakpoint.GlobalBreakpoint
import Engine.Architecture.DeploySemantics.DeployStrategy.{RandomDeployment, RoundRobinDeployment}
import Engine.Architecture.DeploySemantics.DeploymentFilter.{FollowPrevious, ForceLocal, UseAll}
import Engine.Architecture.DeploySemantics.Layer.{ActorLayer, ProcessorWorkerLayer}
import Engine.Architecture.LinkSemantics.{AllToOne, HashBasedShuffle}
import Engine.Architecture.Worker.WorkerState
import Engine.Common.AmberTag.{LayerTag, OperatorIdentifier}
import Engine.Common.Constants
import Engine.Operators.OpExecConfig
import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.util.Timeout
import texera.common.tuple.TexeraTuple

import scala.collection.mutable
import scala.concurrent.ExecutionContext

class TexeraAggregateOpExecConfig[P <: AnyRef](
    tag: OperatorIdentifier,
    val aggFunc: TexeraDistributedAggregation[P]
) extends OpExecConfig(tag) {

  override lazy val topology: Topology = {

    if (aggFunc.groupByFunc == null) {
      val partialLayer = new ProcessorWorkerLayer(
        LayerTag(tag, "localAgg"),
        _ => new TexeraPartialAggregateOpExec(aggFunc),
        Constants.defaultNumWorkers,
        UseAll(),
        RoundRobinDeployment()
      )
      val finalLayer = new ProcessorWorkerLayer(
        LayerTag(tag, "globalAgg"),
        _ => new TexeraFinalAggregateOpExec(aggFunc),
        1,
        ForceLocal(),
        RandomDeployment()
      )
      new Topology(
        Array(
          partialLayer,
          finalLayer
        ),
        Array(
          new AllToOne(partialLayer, finalLayer, Constants.defaultBatchSize)
        ),
        Map()
      )
    } else {
      val partialLayer = new ProcessorWorkerLayer(
        LayerTag(tag, "localAgg"),
        _ => new TexeraPartialAggregateOpExec(aggFunc),
        Constants.defaultNumWorkers,
        UseAll(),
        RoundRobinDeployment()
      )
      val finalLayer = new ProcessorWorkerLayer(
        LayerTag(tag, "globalAgg"),
        _ => new TexeraFinalAggregateOpExec(aggFunc),
        Constants.defaultNumWorkers,
        FollowPrevious(),
        RoundRobinDeployment()
      )
      new Topology(
        Array(
          partialLayer,
          finalLayer
        ),
        Array(
          new HashBasedShuffle(
            partialLayer,
            finalLayer,
            Constants.defaultBatchSize,
            x => {
              val tuple = x.asInstanceOf[TexeraTuple]
              aggFunc.groupByFunc(tuple).hashCode()
            }
          )
        ),
        Map()
      )
    }
  }
  override def assignBreakpoint(
      topology: Array[ActorLayer],
      states: mutable.AnyRefMap[ActorRef, WorkerState.Value],
      breakpoint: GlobalBreakpoint
  )(implicit timeout: Timeout, ec: ExecutionContext, log: LoggingAdapter): Unit = {
    breakpoint.partition(topology(0).layer.filter(states(_) != WorkerState.Completed))
  }
}
