package edu.uci.ics.amber.engine.architecture.sendsemantics.partitioners

import edu.uci.ics.amber.core.tuple.Tuple
import edu.uci.ics.amber.engine.architecture.sendsemantics.partitionings.OneToOnePartitioning
import edu.uci.ics.amber.core.virtualidentity.ActorVirtualIdentity

case class OneToOnePartitioner(partitioning: OneToOnePartitioning, actorId: ActorVirtualIdentity)
    extends Partitioner {

  override def getBucketIndex(tuple: Tuple): Iterator[Int] = Iterator(0)

  override def allReceivers: Seq[ActorVirtualIdentity] =
    Seq(partitioning.channels.filter(_.fromWorkerId == actorId).head.toWorkerId)
}
