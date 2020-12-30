package edu.uci.ics.amber.engine.architecture.messaginglayer

import edu.uci.ics.amber.engine.architecture.sendsemantics.datatransferpolicy.DataTransferPolicy
import edu.uci.ics.amber.engine.common.ambermessage.WorkerMessage.UpdateInputLinking
import edu.uci.ics.amber.engine.common.ambermessage.neo.DataPayload
import edu.uci.ics.amber.engine.common.ambertag.LinkTag
import edu.uci.ics.amber.engine.common.ambertag.neo.VirtualIdentity.ActorVirtualIdentity
import edu.uci.ics.amber.engine.common.tuple.ITuple

import scala.util.control.Breaks

/** This class is a container of all the transfer policies.
  * @param selfID
  * @param dataOutputPort
  * @param controlOutputPort
  */
class TupleToBatchConverter(
    selfID: ActorVirtualIdentity,
    dataOutputPort: DataOutputPort,
    controlOutputPort: ControlOutputPort
) {
  private var policies = new Array[DataTransferPolicy](0)

  /** Add down stream operator and its transfer policy
    * @param policy
    * @param linkTag
    * @param receivers
    */
  def addPolicy(
      policy: DataTransferPolicy,
      linkTag: LinkTag,
      receivers: Array[ActorVirtualIdentity]
  ): Unit = {
    var i = 0
    receivers.foreach { x =>
      controlOutputPort.sendTo(x, UpdateInputLinking(selfID, linkTag.inputNum))
    }
    policy.initialize(linkTag, receivers)
    Breaks.breakable {
      while (i < policies.length) {
        if (policies(i).tag == policy.tag) {
          policies(i) = policy
          Breaks.break()
        }
        i += 1
      }
      policies = policies :+ policy
    }
  }

  /** Push one tuple to the downstream, will be batched by each transfer policy.
    * Should ONLY be called by DataProcessor.
    * @param tuple
    */
  def passTupleToDownstream(tuple: ITuple): Unit = {
    var i = 0
    while (i < policies.length) {
      val receiverAndBatch: Option[(ActorVirtualIdentity, DataPayload)] =
        policies(i).addTupleToBatch(tuple)
      receiverAndBatch match {
        case Some((id, batch)) =>
          // send it to messaging layer to be sent downstream
          dataOutputPort.sendTo(id, batch)
        case None =>
        // Do nothing
      }
      i += 1
    }
  }

  /* Old API: for compatibility */
  def resetPolicies(): Unit = {
    policies.foreach(_.reset())
  }

  /* Send the last batch and EOU marker to all down streams */
  def emitEndOfUpstream(): Unit = {
    var i = 0
    while (i < policies.length) {
      val receiversAndBatches: Array[(ActorVirtualIdentity, DataPayload)] = policies(i).noMore()
      receiversAndBatches.foreach {
        case (id, batch) => dataOutputPort.sendTo(id, batch)
      }
      i += 1
    }
  }

}
