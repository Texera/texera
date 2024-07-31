package edu.uci.ics.texera.web.resource.dashboard

import edu.uci.ics.texera.web.model.jooq.generated.Tables.{
  PROJECT_USER_ACCESS,
  USER,
  WORKFLOW,
  WORKFLOW_OF_PROJECT,
  WORKFLOW_OF_USER,
  WORKFLOW_USER_ACCESS
}
import org.jooq.TableLike
import org.jooq.types.UInteger

object DefaultWorkflowSearchQueryBuilder extends BaseWorkFlowSearchQueryBuilder {

  override protected def constructFromClause(
      uid: UInteger,
      params: DashboardResource.SearchQueryParams
  ): TableLike[_] = {
    WORKFLOW
      .leftJoin(WORKFLOW_USER_ACCESS)
      .on(WORKFLOW_USER_ACCESS.WID.eq(WORKFLOW.WID))
      .leftJoin(WORKFLOW_OF_USER)
      .on(WORKFLOW_OF_USER.WID.eq(WORKFLOW.WID))
      .leftJoin(USER)
      .on(USER.UID.eq(WORKFLOW_OF_USER.UID))
      .leftJoin(WORKFLOW_OF_PROJECT)
      .on(WORKFLOW_OF_PROJECT.WID.eq(WORKFLOW.WID))
      .leftJoin(PROJECT_USER_ACCESS)
      .on(PROJECT_USER_ACCESS.PID.eq(WORKFLOW_OF_PROJECT.PID))
      .where(
        WORKFLOW.IS_PUBLISHED.eq(1.toByte)
      )
  }
}
