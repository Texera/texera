package web.resources

import io.kubernetes.client.openapi.models.V1Pod
import jakarta.ws.rs.core.{MediaType, Response}
import jakarta.ws.rs.{Consumes, GET, POST, Path, Produces, QueryParam}
import org.jooq.DSLContext
import org.jooq.types.UInteger
import service.KubernetesClientService
import web.Utils.withTransaction
import web.model.SqlServer
import web.model.jooq.generated.tables.daos.PodDao
import web.resources.WorkflowPodBrainResource.{WorkflowPodCreationParams, WorkflowPodTerminationParams, context}
import web.model.jooq.generated.tables.pojos.Pod

import java.sql.Timestamp
import scala.jdk.CollectionConverters.CollectionHasAsScala

object WorkflowPodBrainResource {

  private lazy val context: DSLContext = SqlServer.createDSLContext()
  case class WorkflowPodCreationParams(uid: UInteger, wid: UInteger)

  case class WorkflowPodTerminationParams(uid: UInteger, wid: UInteger)
}

@Produces(Array(MediaType.APPLICATION_JSON))
@Path("/workflowpod")
class WorkflowPodBrainResource {
  /**
    * Create a new pod for the given workflow wid and workflow content
    * @param param the parameters
    * @return the created pod
    */
  @POST
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Path("/create")
  def createPod(
                 param: WorkflowPodCreationParams
               ): Pod = {
    val newPod: V1Pod = new KubernetesClientService().createPod(param.uid.toString)
    val newSQLPod: Pod = new Pod()

    // Set uid, name, pod_uid, creation_time manually
    // pod_id, terminate_time are auto-generated
    newSQLPod.setUid(param.uid)
    newSQLPod.setName(newPod.getMetadata.getName)
    newSQLPod.setPodUid(newPod.getMetadata.getUid)
    newSQLPod.setCreationTime(Timestamp.from(newPod.getMetadata.getCreationTimestamp.toInstant))

    withTransaction(context) { ctx =>
      val podDao = new PodDao(ctx.configuration())
      podDao.insert(newSQLPod)
      newSQLPod
    }
  }


  /**
    * List all pods created by current user
    * @return
    */
  @GET
  @Path("")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def listPods(@QueryParam("uid") uid: UInteger): java.util.List[Pod] = {
    withTransaction(context) { ctx =>
      val podDao = new PodDao(ctx.configuration())
      var pods: java.util.List[Pod] = null
      if (uid == null) {
         pods = podDao.findAll()
      } else {
        pods = podDao.fetchByUid(uid)
      }
      pods
    }
  }


  /**
    * Terminate the workflow's pod
    * @param param
    * @return
    */
  @POST
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Path("/terminate")
  def terminatePod(
                    param: WorkflowPodTerminationParams
                  ): Response = ???
}
