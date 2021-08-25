package edu.uci.ics.amber.engine.e2e

import akka.actor.{ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import ch.vorburger.mariadb4j.DB
import edu.uci.ics.amber.clustering.SingleNodeListener
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.StartWorkflowHandler.StartWorkflow
import edu.uci.ics.amber.engine.architecture.controller._
import edu.uci.ics.amber.engine.architecture.principal.OperatorResult
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCClient
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCClient.ControlInvocation
import edu.uci.ics.amber.engine.common.tuple.ITuple
import edu.uci.ics.amber.engine.common.virtualidentity.WorkflowIdentity
import edu.uci.ics.texera.workflow.common.WorkflowContext
import edu.uci.ics.texera.workflow.common.operators.OperatorDescriptor
import edu.uci.ics.texera.workflow.common.tuple.Tuple
import edu.uci.ics.texera.workflow.common.tuple.schema.AttributeType
import edu.uci.ics.texera.workflow.common.workflow._
import edu.uci.ics.texera.workflow.operators.aggregate.AggregationFunction
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import java.sql.PreparedStatement
import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

class DataProcessingSpec
    extends TestKit(ActorSystem("DataProcessingSpec"))
    with ImplicitSender
    with AnyFlatSpecLike
    with BeforeAndAfterAll
    with BeforeAndAfterEach {

  implicit val timeout: Timeout = Timeout(5.seconds)
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  var inMemoryMySQLInstance: Option[DB] = None

  override def beforeAll: Unit = {
    system.actorOf(Props[SingleNodeListener], "cluster-info")
  }
  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  def buildWorkflow(
      operators: mutable.MutableList[OperatorDescriptor],
      links: mutable.MutableList[OperatorLink]
  ): (WorkflowIdentity, Workflow) = {
    val context = new WorkflowContext
    context.jobID = "workflow-test"

    val texeraWorkflowCompiler = new WorkflowCompiler(
      WorkflowInfo(operators, links, mutable.MutableList[BreakpointInfo]()),
      context
    )
    val workflow = texeraWorkflowCompiler.amberWorkflow
    val workflowTag = WorkflowIdentity("workflow-test")
    (workflowTag, workflow)
  }

  def executeWorkflow(id: WorkflowIdentity, workflow: Workflow): Map[String, List[ITuple]] = {
    val parent = TestProbe()
    var results: Map[String, OperatorResult] = null
    val eventListener = ControllerEventListener()
    eventListener.workflowCompletedListener = evt => results = evt.result
    val controller = parent.childActorOf(
      Controller.props(id, workflow, eventListener, ControllerConfig.default)
    )
    parent.expectMsg(ControllerState.Ready)
    controller ! ControlInvocation(AsyncRPCClient.IgnoreReply, StartWorkflow())
    parent.expectMsg(ControllerState.Running)
    parent.expectMsg(1.minute, ControllerState.Completed)
    parent.ref ! PoisonPill
    results.map(e => (e._1, e._2.result))
  }

  def initializeInMemoryMySQLInstance(): (String, String, String, String, String, String) = {
    import ch.vorburger.mariadb4j.{DB, DBConfigurationBuilder}

    import java.sql.DriverManager

    val database: String = "new"
    val table: String = "test"
    val username: String = "root"
    val password: String = ""

    val config = DBConfigurationBuilder.newBuilder
      .setPort(0) // 0 => automatically detect free port
      .addArg("--default-time-zone=+0:00")
      .build()

    inMemoryMySQLInstance = Option(DB.newEmbeddedDB(config))
    inMemoryMySQLInstance.get.start()
    inMemoryMySQLInstance.get.createDB(database)

    // insert test data
    val conn = DriverManager.getConnection(config.getURL(database), username, password)
    var statement: PreparedStatement = conn.prepareStatement(
      s"create table $table (id int primary key auto_increment, text VARCHAR(512), " +
        s"point FLOAT, created_at DATE default NOW() not null)"
    )
    statement.execute()
    statement = conn.prepareStatement(s"insert into $table (text) values ('hello world')")
    statement.execute()
    statement.close()
    conn.close()
    ("localhost", config.getPort.toString, database, table, username, password)
  }

  "Engine" should "execute headerlessCsv->sink workflow normally" in {
    val headerlessCsvOpDesc = TestOperators.headerlessSmallCsvScanOpDesc()
    val sink = TestOperators.sinkOpDesc()
    val (id, workflow) = buildWorkflow(
      mutable.MutableList[OperatorDescriptor](headerlessCsvOpDesc, sink),
      mutable.MutableList[OperatorLink](
        OperatorLink(
          OperatorPort(headerlessCsvOpDesc.operatorID, 0),
          OperatorPort(sink.operatorID, 0)
        )
      )
    )
    val results = executeWorkflow(id, workflow)(sink.operatorID)

    assert(results.size == 100)
  }

  "Engine" should "execute headerlessMultiLineDataCsv-->sink workflow normally" in {
    val headerlessCsvOpDesc = TestOperators.headerlessSmallMultiLineDataCsvScanOpDesc()
    val sink = TestOperators.sinkOpDesc()
    val (id, workflow) = buildWorkflow(
      mutable.MutableList[OperatorDescriptor](headerlessCsvOpDesc, sink),
      mutable.MutableList[OperatorLink](
        OperatorLink(
          OperatorPort(headerlessCsvOpDesc.operatorID, 0),
          OperatorPort(sink.operatorID, 0)
        )
      )
    )
    val results = executeWorkflow(id, workflow)(sink.operatorID)

    assert(results.size == 100)
  }

  "Engine" should "execute jsonl->sink workflow normally" in {
    val jsonlOp = TestOperators.smallJSONLScanOpDesc()
    val sink = TestOperators.sinkOpDesc()
    val (id, workflow) = buildWorkflow(
      mutable.MutableList[OperatorDescriptor](jsonlOp, sink),
      mutable.MutableList[OperatorLink](
        OperatorLink(
          OperatorPort(jsonlOp.operatorID, 0),
          OperatorPort(sink.operatorID, 0)
        )
      )
    )
    val results = executeWorkflow(id, workflow)(sink.operatorID)

    assert(results.size == 100)

    for (result <- results) {
      val schema = result.asInstanceOf[Tuple].getSchema
      assert(schema.getAttribute("id").getType == AttributeType.LONG)
      assert(schema.getAttribute("first_name").getType == AttributeType.STRING)
      assert(schema.getAttribute("flagged").getType == AttributeType.BOOLEAN)
      assert(schema.getAttribute("year").getType == AttributeType.INTEGER)
      assert(schema.getAttribute("created_at").getType == AttributeType.TIMESTAMP)
      assert(schema.getAttributes.size() == 9)
    }

  }

  "Engine" should "execute mediumFlattenJsonl->sink workflow normally" in {
    val jsonlOp = TestOperators.mediumFlattenJSONLScanOpDesc()
    val sink = TestOperators.sinkOpDesc()
    val (id, workflow) = buildWorkflow(
      mutable.MutableList[OperatorDescriptor](jsonlOp, sink),
      mutable.MutableList[OperatorLink](
        OperatorLink(
          OperatorPort(jsonlOp.operatorID, 0),
          OperatorPort(sink.operatorID, 0)
        )
      )
    )
    val results = executeWorkflow(id, workflow)(sink.operatorID)

    assert(results.size == 1000)

    for (result <- results) {
      val schema = result.asInstanceOf[Tuple].getSchema
      assert(schema.getAttribute("id").getType == AttributeType.LONG)
      assert(schema.getAttribute("first_name").getType == AttributeType.STRING)
      assert(schema.getAttribute("flagged").getType == AttributeType.BOOLEAN)
      assert(schema.getAttribute("year").getType == AttributeType.INTEGER)
      assert(schema.getAttribute("created_at").getType == AttributeType.TIMESTAMP)
      assert(schema.getAttribute("test_object.array2.another").getType == AttributeType.INTEGER)
      assert(schema.getAttributes.size() == 13)
    }
  }

  "Engine" should "execute headerlessCsv->keyword->sink workflow normally" in {
    val headerlessCsvOpDesc = TestOperators.headerlessSmallCsvScanOpDesc()
    val keywordOpDesc = TestOperators.keywordSearchOpDesc("column-1", "Asia")
    val sink = TestOperators.sinkOpDesc()
    val (id, workflow) = buildWorkflow(
      mutable.MutableList[OperatorDescriptor](headerlessCsvOpDesc, keywordOpDesc, sink),
      mutable.MutableList[OperatorLink](
        OperatorLink(
          OperatorPort(headerlessCsvOpDesc.operatorID, 0),
          OperatorPort(keywordOpDesc.operatorID, 0)
        ),
        OperatorLink(OperatorPort(keywordOpDesc.operatorID, 0), OperatorPort(sink.operatorID, 0))
      )
    )
    executeWorkflow(id, workflow)
  }

  "Engine" should "execute headerlessCsv->word count->sink workflow normally" in {

    val headerlessCsvOpDesc = TestOperators.headerlessSmallCsvScanOpDesc()
    // Get only the highest count, for testing purposes
    val wordCountOpDesc = TestOperators.wordCloudOpDesc("column-1", 1)
    val sink = TestOperators.sinkOpDesc()
    val (id, workflow) = buildWorkflow(
      mutable.MutableList[OperatorDescriptor](headerlessCsvOpDesc, wordCountOpDesc, sink),
      mutable.MutableList[OperatorLink](
        OperatorLink(
          OperatorPort(headerlessCsvOpDesc.operatorID, 0),
          OperatorPort(wordCountOpDesc.operatorID, 0)
        ),
        OperatorLink(OperatorPort(wordCountOpDesc.operatorID, 0), OperatorPort(sink.operatorID, 0))
      )
    )
    val result = executeWorkflow(id, workflow).values
    // Assert that only one tuple came out successfully
    assert(result.size == 1)

  }

  "Engine" should "execute csv->sink workflow normally" in {
    val csvOpDesc = TestOperators.smallCsvScanOpDesc()
    val sink = TestOperators.sinkOpDesc()
    val (id, workflow) = buildWorkflow(
      mutable.MutableList[OperatorDescriptor](csvOpDesc, sink),
      mutable.MutableList[OperatorLink](
        OperatorLink(OperatorPort(csvOpDesc.operatorID, 0), OperatorPort(sink.operatorID, 0))
      )
    )
    executeWorkflow(id, workflow)
  }

  "Engine" should "execute csv->keyword->sink workflow normally" in {
    val csvOpDesc = TestOperators.smallCsvScanOpDesc()
    val keywordOpDesc = TestOperators.keywordSearchOpDesc("Region", "Asia")
    val sink = TestOperators.sinkOpDesc()
    val (id, workflow) = buildWorkflow(
      mutable.MutableList[OperatorDescriptor](csvOpDesc, keywordOpDesc, sink),
      mutable.MutableList[OperatorLink](
        OperatorLink(
          OperatorPort(csvOpDesc.operatorID, 0),
          OperatorPort(keywordOpDesc.operatorID, 0)
        ),
        OperatorLink(OperatorPort(keywordOpDesc.operatorID, 0), OperatorPort(sink.operatorID, 0))
      )
    )
    executeWorkflow(id, workflow)
  }

  "Engine" should "execute csv->keyword->count->sink workflow normally" in {
    val csvOpDesc = TestOperators.smallCsvScanOpDesc()
    val keywordOpDesc = TestOperators.keywordSearchOpDesc("Region", "Asia")
    val countOpDesc =
      TestOperators.aggregateAndGroupByDesc("Region", AggregationFunction.COUNT, List[String]())
    val sink = TestOperators.sinkOpDesc()
    val (id, workflow) = buildWorkflow(
      mutable.MutableList[OperatorDescriptor](csvOpDesc, keywordOpDesc, countOpDesc, sink),
      mutable.MutableList[OperatorLink](
        OperatorLink(
          OperatorPort(csvOpDesc.operatorID, 0),
          OperatorPort(keywordOpDesc.operatorID, 0)
        ),
        OperatorLink(
          OperatorPort(keywordOpDesc.operatorID, 0),
          OperatorPort(countOpDesc.operatorID, 0)
        ),
        OperatorLink(OperatorPort(countOpDesc.operatorID, 0), OperatorPort(sink.operatorID, 0))
      )
    )
    executeWorkflow(id, workflow)
  }

  "Engine" should "execute csv->keyword->averageAndGroupBy->sink workflow normally" in {
    val csvOpDesc = TestOperators.smallCsvScanOpDesc()
    val keywordOpDesc = TestOperators.keywordSearchOpDesc("Region", "Asia")
    val averageAndGroupByOpDesc =
      TestOperators.aggregateAndGroupByDesc(
        "Units Sold",
        AggregationFunction.AVERAGE,
        List[String]("Country")
      )
    val sink = TestOperators.sinkOpDesc()
    val (id, workflow) = buildWorkflow(
      mutable
        .MutableList[OperatorDescriptor](csvOpDesc, keywordOpDesc, averageAndGroupByOpDesc, sink),
      mutable.MutableList[OperatorLink](
        OperatorLink(
          OperatorPort(csvOpDesc.operatorID, 0),
          OperatorPort(keywordOpDesc.operatorID, 0)
        ),
        OperatorLink(
          OperatorPort(keywordOpDesc.operatorID, 0),
          OperatorPort(averageAndGroupByOpDesc.operatorID, 0)
        ),
        OperatorLink(
          OperatorPort(averageAndGroupByOpDesc.operatorID, 0),
          OperatorPort(sink.operatorID, 0)
        )
      )
    )
    executeWorkflow(id, workflow)
  }

  "Engine" should "execute csv->(csv->)->join->sink workflow normally" in {
    val headerlessCsvOpDesc1 = TestOperators.headerlessSmallCsvScanOpDesc()
    val headerlessCsvOpDesc2 = TestOperators.headerlessSmallCsvScanOpDesc()
    val joinOpDesc = TestOperators.joinOpDesc("column-1", "column-1")
    val sink = TestOperators.sinkOpDesc()
    val (id, workflow) = buildWorkflow(
      mutable.MutableList[OperatorDescriptor](
        headerlessCsvOpDesc1,
        headerlessCsvOpDesc2,
        joinOpDesc,
        sink
      ),
      mutable.MutableList[OperatorLink](
        OperatorLink(
          OperatorPort(headerlessCsvOpDesc1.operatorID, 0),
          OperatorPort(joinOpDesc.operatorID, 0)
        ),
        OperatorLink(
          OperatorPort(headerlessCsvOpDesc2.operatorID, 0),
          OperatorPort(joinOpDesc.operatorID, 1)
        ),
        OperatorLink(
          OperatorPort(joinOpDesc.operatorID, 0),
          OperatorPort(sink.operatorID, 0)
        )
      )
    )
    executeWorkflow(id, workflow)
  }

  // TODO: use mock data to perform the test, remove dependency on the real AsterixDB
//  "Engine" should "execute asterixdb->sink workflow normally" in {
//
//    val asterixDBOp = TestOperators.asterixDBSourceOpDesc()
//    val sink = TestOperators.sinkOpDesc()
//    val (id, workflow) = buildWorkflow(
//      mutable.MutableList[OperatorDescriptor](asterixDBOp, sink),
//      mutable.MutableList[OperatorLink](
//        OperatorLink(OperatorPort(asterixDBOp.operatorID, 0), OperatorPort(sink.operatorID, 0))
//      )
//    )
//    executeWorkflow(id, workflow)
//  }

  "Engine" should "execute mysql->sink workflow normally" in {
    val (host, port, database, table, username, password) = initializeInMemoryMySQLInstance()
    val inMemoryMsSQLSourceOpDesc = TestOperators.inMemoryMySQLSourceOpDesc(
      host,
      port,
      database,
      table,
      username,
      password
    )

    val sink = TestOperators.sinkOpDesc()
    val (id, workflow) = buildWorkflow(
      mutable.MutableList[OperatorDescriptor](inMemoryMsSQLSourceOpDesc, sink),
      mutable.MutableList[OperatorLink](
        OperatorLink(
          OperatorPort(inMemoryMsSQLSourceOpDesc.operatorID, 0),
          OperatorPort(sink.operatorID, 0)
        )
      )
    )
    executeWorkflow(id, workflow)

    inMemoryMySQLInstance.get.stop()
  }

}
