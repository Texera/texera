package edu.uci.ics.amber.service.resource

import com.typesafe.scalalogging.LazyLogging
import edu.uci.ics.amber.compiler.WorkflowCompiler
import edu.uci.ics.amber.compiler.model.LogicalPlanPojo
import edu.uci.ics.amber.core.tuple.Attribute
import edu.uci.ics.amber.core.workflow.{PhysicalPlan, WorkflowContext}
import edu.uci.ics.amber.virtualidentity.WorkflowIdentity
import edu.uci.ics.amber.workflowruntimestate.WorkflowFatalError
import jakarta.annotation.security.RolesAllowed
import jakarta.ws.rs.{Consumes, POST, Path, PathParam, Produces}
import jakarta.ws.rs.core.MediaType
import org.jooq.types.UInteger

trait WorkflowCompilationResponse
case class WorkflowCompilationSuccess(
    physicalPlan: PhysicalPlan,
    operatorInputSchemas: Map[String, List[Option[List[Attribute]]]]
) extends WorkflowCompilationResponse

case class WorkflowCompilationFailure(
    operatorErrors: Map[String, WorkflowFatalError],
    operatorInputSchemas: Map[String, List[Option[List[Attribute]]]]
) extends WorkflowCompilationResponse

@Consumes(Array(MediaType.APPLICATION_JSON))
@Produces(Array(MediaType.APPLICATION_JSON))
@RolesAllowed(Array("REGULAR", "ADMIN"))
@Path("/compile")
class WorkflowCompilationResource extends LazyLogging {

  @POST
  @Path("/{wid}")
  def compileWorkflow(
      logicalPlanPojo: LogicalPlanPojo,
      @PathParam("wid") wid: UInteger
  ): WorkflowCompilationResponse = {
    // Create workflow context from wid
    val context = new WorkflowContext(
      workflowId = WorkflowIdentity(wid.toString.toLong)
    )

    // Compile the pojo using WorkflowCompiler
    val compilationResult = new WorkflowCompiler(context).compile(logicalPlanPojo)

    val operatorInputSchemas = compilationResult.operatorIdToInputSchemas.map {
      case (operatorIdentity, schemas) =>
        val opId = operatorIdentity.id
        val attributes = schemas.map { schema =>
          if (schema.isEmpty)
            None
          else
            Some(schema.get.attributes)
        }
        (opId, attributes)
    }

    // Handle success case: No errors in the compilation result
    if (compilationResult.operatorIdToError.isEmpty && compilationResult.physicalPlan.nonEmpty) {
      WorkflowCompilationSuccess(
        physicalPlan = compilationResult.physicalPlan.get,
        operatorInputSchemas
      )
    }
    // Handle failure case: Errors found during compilation
    else {
      WorkflowCompilationFailure(
        operatorErrors = compilationResult.operatorIdToError.map {
          case (operatorIdentity, error) => (operatorIdentity.id, error)
        },
        operatorInputSchemas
      )
    }
  }
}