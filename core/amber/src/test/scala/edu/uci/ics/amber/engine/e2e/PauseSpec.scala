package edu.uci.ics.amber.engine.e2e

import edu.uci.ics.amber.clustering.SingleNodeListener
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import com.twitter.util.{Await, Promise}
import edu.uci.ics.amber.engine.architecture.controller.{ControllerConfig, ControllerState}
import edu.uci.ics.texera.workflow.common.operators.OperatorDescriptor
import edu.uci.ics.texera.workflow.common.workflow.{
  BreakpointInfo,
  OperatorLink,
  OperatorPort,
  WorkflowCompiler,
  WorkflowInfo
}
import org.scalatest.BeforeAndAfterAll

import scala.collection.mutable
import scala.concurrent.duration._
import com.typesafe.scalalogging.Logger
import edu.uci.ics.amber.engine.architecture.controller.ControllerEvent.WorkflowCompleted
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.PauseHandler.PauseWorkflow
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.ResumeHandler.ResumeWorkflow
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.StartWorkflowHandler.StartWorkflow
import edu.uci.ics.amber.engine.common.client.AmberClient
import org.scalatest.flatspec.AnyFlatSpecLike

class PauseSpec
    extends TestKit(ActorSystem("PauseSpec"))
    with ImplicitSender
    with AnyFlatSpecLike
    with BeforeAndAfterAll {

  implicit val timeout: Timeout = Timeout(5.seconds)

  val logger = Logger("PauseSpecLogger")

  override def beforeAll: Unit = {
    system.actorOf(Props[SingleNodeListener], "cluster-info")
  }

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  def shouldPause(
      operators: mutable.MutableList[OperatorDescriptor],
      links: mutable.MutableList[OperatorLink]
  ): Unit = {
    val client =
      new AmberClient(system, Utils.getWorkflow(operators, links), ControllerConfig.default)
    val completion = Promise[Unit]
    client
      .getObservable[WorkflowCompleted]
      .subscribe(evt => {
        completion.setDone()
      })
    client.sendSync(StartWorkflow(), 1.second)
    client.sendSync(PauseWorkflow(), 1.second)
    Thread.sleep(4000)
    client.sendSync(ResumeWorkflow(), 1.second)
    Thread.sleep(400)
    client.sendSync(PauseWorkflow(), 1.second)
    Thread.sleep(4000)
    client.sendSync(ResumeWorkflow(), 1.second)
    Await.result(completion)
  }

  "Engine" should "be able to pause csv->sink workflow" in {
    val csvOpDesc = TestOperators.mediumCsvScanOpDesc()
    val sink = TestOperators.sinkOpDesc()
    logger.info(s"csv-id ${csvOpDesc.operatorID}, sink-id ${sink.operatorID}")
    shouldPause(
      mutable.MutableList[OperatorDescriptor](csvOpDesc, sink),
      mutable.MutableList[OperatorLink](
        OperatorLink(OperatorPort(csvOpDesc.operatorID, 0), OperatorPort(sink.operatorID, 0))
      )
    )
  }

  "Engine" should "be able to pause csv->keyword->sink workflow" in {
    val csvOpDesc = TestOperators.mediumCsvScanOpDesc()
    val keywordOpDesc = TestOperators.keywordSearchOpDesc("Region", "Asia")
    val sink = TestOperators.sinkOpDesc()
    logger.info(
      s"csv-id ${csvOpDesc.operatorID}, keyword-id ${keywordOpDesc.operatorID}, sink-id ${sink.operatorID}"
    )
    shouldPause(
      mutable.MutableList[OperatorDescriptor](csvOpDesc, keywordOpDesc, sink),
      mutable.MutableList[OperatorLink](
        OperatorLink(
          OperatorPort(csvOpDesc.operatorID, 0),
          OperatorPort(keywordOpDesc.operatorID, 0)
        ),
        OperatorLink(OperatorPort(keywordOpDesc.operatorID, 0), OperatorPort(sink.operatorID, 0))
      )
    )
  }

}
