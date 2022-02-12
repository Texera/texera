package edu.uci.ics.texera.web.service

import com.typesafe.scalalogging.LazyLogging
import edu.uci.ics.texera.web.SqlServer
import edu.uci.ics.texera.web.model.jooq.generated.Tables.{WORKFLOW, WORKFLOW_VERSION}
import edu.uci.ics.texera.web.model.jooq.generated.tables.daos.WorkflowExecutionsDao
import edu.uci.ics.texera.web.model.jooq.generated.tables.pojos.WorkflowExecutions
import edu.uci.ics.texera.web.workflowruntimestate.WorkflowAggregatedState
import org.jooq.types.UInteger

import java.sql.Timestamp
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`

/**
  * This global object handles inserting a new entry to the DB to store metadata information about every workflow execution
  * It also updates the entry if an execution status is updated
  */
object ExecutionsMetadataPersistService extends LazyLogging {
  final private lazy val context = SqlServer.createDSLContext()

  private val workflowExecutionsDao = new WorkflowExecutionsDao(
    context.configuration
  )

  /**
    * This method inserts a new entry of a workflow execution in the database and returns the generated eId
    *
    * @param wid     the given workflow
    * @return generated execution ID
    */

  private def getLatestVersion(wid: UInteger): UInteger = {
    context
      .select(WORKFLOW_VERSION.VID)
      .from(WORKFLOW_VERSION)
      .leftJoin(WORKFLOW)
      .on(WORKFLOW_VERSION.WID.eq(WORKFLOW.WID))
      .where(WORKFLOW_VERSION.WID.eq(wid))
      .fetchInto(classOf[UInteger])
      .toList
      .max
  }

  private def maptoStatusCode(state: WorkflowAggregatedState): Byte = {
    state match {
      case WorkflowAggregatedState.UNINITIALIZED                   => 0
      case WorkflowAggregatedState.READY                           => 0
      case WorkflowAggregatedState.RUNNING                         => 1
      case WorkflowAggregatedState.PAUSING                         => ???
      case WorkflowAggregatedState.PAUSED                          => 2
      case WorkflowAggregatedState.RESUMING                        => ???
      case WorkflowAggregatedState.RECOVERING                      => ???
      case WorkflowAggregatedState.COMPLETED                       => 3
      case WorkflowAggregatedState.ABORTED                         => 4
      case WorkflowAggregatedState.UNKNOWN                         => ???
      case WorkflowAggregatedState.Unrecognized(unrecognizedValue) => ???
    }
  }

  def insertNewExecution(
      wid: Long
  ): Long = {
    // first retrieve the latest version of this workflow
    val uint = UInteger.valueOf(wid)
    val vid = getLatestVersion(uint)
    val newExecution = new WorkflowExecutions()
    newExecution.setWid(uint)
    newExecution.setVid(vid)
    newExecution.setStartingTime(new Timestamp(System.currentTimeMillis()))
    workflowExecutionsDao.insert(newExecution)
    newExecution.getEid.longValue()
  }

  def tryUpdateExistingExecution(eid: Long, state: WorkflowAggregatedState): Unit = {
    try {
      val code = maptoStatusCode(state)
      val execution = workflowExecutionsDao.fetchOneByEid(UInteger.valueOf(eid))
      execution.setStatus(code)
      execution.setCompletionTime(new Timestamp(System.currentTimeMillis()))
      workflowExecutionsDao.update(execution)
    } catch {
      case t: Throwable =>
        logger.info("Unable to update execution. Error = " + t.getMessage)
    }
  }
}
