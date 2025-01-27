import * as Papa from "papaparse";
import { Injectable } from "@angular/core";
import { environment } from "../../../../environments/environment";
import { WorkflowWebsocketService } from "../workflow-websocket/workflow-websocket.service";
import { WorkflowActionService } from "../workflow-graph/model/workflow-action.service";
import { BehaviorSubject, EMPTY, expand, finalize, merge, Observable, of } from "rxjs";
import { PaginatedResultEvent, ResultExportResponse } from "../../types/workflow-websocket.interface";
import { NotificationService } from "../../../common/service/notification/notification.service";
import { ExecuteWorkflowService } from "../execute-workflow/execute-workflow.service";
import { ExecutionState, isNotInExecution } from "../../types/execute-workflow.interface";
import { filter } from "rxjs/operators";
import { OperatorResultService, WorkflowResultService } from "../workflow-result/workflow-result.service";
import { OperatorPaginationResultService } from "../workflow-result/workflow-result.service";
import { DownloadService } from "../../../dashboard/service/user/download/download.service";

@Injectable({
  providedIn: "root",
})
export class WorkflowResultExportService {
  hasResultToExportOnHighlightedOperators: boolean = false;
  exportExecutionResultEnabled: boolean = environment.exportExecutionResultEnabled;
  hasResultToExportOnAllOperators = new BehaviorSubject<boolean>(false);
  constructor(
    private workflowWebsocketService: WorkflowWebsocketService,
    private workflowActionService: WorkflowActionService,
    private notificationService: NotificationService,
    private executeWorkflowService: ExecuteWorkflowService,
    private workflowResultService: WorkflowResultService,
    private downloadService: DownloadService
  ) {
    this.registerResultExportResponseHandler();
    this.registerResultToExportUpdateHandler();
  }

  registerResultExportResponseHandler() {
    this.workflowWebsocketService
      .subscribeToEvent("ResultExportResponse")
      .subscribe((response: ResultExportResponse) => {
        if (response.status === "success") {
          this.notificationService.success(response.message);
        } else {
          this.notificationService.error(response.message);
        }
      });
  }

  registerResultToExportUpdateHandler() {
    merge(
      this.executeWorkflowService
        .getExecutionStateStream()
        .pipe(filter(({ previous, current }) => current.state === ExecutionState.Completed)),
      this.workflowActionService.getJointGraphWrapper().getJointOperatorHighlightStream(),
      this.workflowActionService.getJointGraphWrapper().getJointOperatorUnhighlightStream()
    ).subscribe(() => {
      // check if there are any results to export on highlighted operators (either paginated or snapshot)
      this.hasResultToExportOnHighlightedOperators =
        isNotInExecution(this.executeWorkflowService.getExecutionState().state) &&
        this.workflowActionService
          .getJointGraphWrapper()
          .getCurrentHighlightedOperatorIDs()
          .filter(
            operatorId =>
              this.workflowResultService.hasAnyResult(operatorId) ||
              this.workflowResultService.getResultService(operatorId)?.getCurrentResultSnapshot() !== undefined
          ).length > 0;

      // check if there are any results to export on all operators (either paginated or snapshot)
      let staticHasResultToExportOnAllOperators =
        isNotInExecution(this.executeWorkflowService.getExecutionState().state) &&
        this.workflowActionService
          .getTexeraGraph()
          .getAllOperators()
          .map(operator => operator.operatorID)
          .filter(
            operatorId =>
              this.workflowResultService.hasAnyResult(operatorId) ||
              this.workflowResultService.getResultService(operatorId)?.getCurrentResultSnapshot() !== undefined
          ).length > 0;

      // Notify subscribers of changes
      this.hasResultToExportOnAllOperators.next(staticHasResultToExportOnAllOperators);
    });
  }

  /**
   * Export the operator results as files.
   * If multiple operatorIds are provided, results are zipped into a single file.
   */
  exportOperatorsResultToLocal(exportAll: boolean = true): void {
    let operatorIds: string[];
    if (!exportAll)
      operatorIds = [...this.workflowActionService.getJointGraphWrapper().getCurrentHighlightedOperatorIDs()];
    else
      operatorIds = this.workflowActionService
        .getTexeraGraph()
        .getAllOperators()
        .map(operator => operator.operatorID);

    const resultObservables: Observable<any>[] = [];

    operatorIds.forEach(operatorId => {
      const resultService = this.workflowResultService.getResultService(operatorId);
      const paginatedResultService = this.workflowResultService.getPaginatedResultService(operatorId);

      if (paginatedResultService) {
        const observable = this.fetchAllPaginatedResultsAsCSV(paginatedResultService, operatorId);
        resultObservables.push(observable);
      } else if (resultService) {
        const observable = this.fetchVisualizationResultsAsHTML(resultService, operatorId);
        resultObservables.push(observable);
      }
    });

    if (resultObservables.length === 0) {
      return;
    }

    this.downloadService
      .downloadOperatorsResult(resultObservables, this.workflowActionService.getWorkflow())
      .subscribe({
        error: (error: unknown) => {
          console.error("Error exporting operator results:", error);
        },
      });
  }

  /**
   * export the workflow execution result according the export type
   */
  exportWorkflowExecutionResult(
    exportType: string,
    workflowName: string,
    datasetIds: ReadonlyArray<number> = [],
    rowIndex: number,
    columnIndex: number,
    filename: string,
    exportAll: boolean = false
  ): void {
    if (!environment.exportExecutionResultEnabled) {
      return;
    }

    const workflowId = this.workflowActionService.getWorkflow().wid;
    if (!workflowId) {
      return;
    }

    let operatorIds: string[];
    if (!exportAll)
      operatorIds = [...this.workflowActionService.getJointGraphWrapper().getCurrentHighlightedOperatorIDs()];
    else
      operatorIds = this.workflowActionService
        .getTexeraGraph()
        .getAllOperators()
        .map(operator => operator.operatorID);

    this.notificationService.loading("exporting...");
    operatorIds.forEach(operatorId => {
      if (!this.workflowResultService.hasAnyResult(operatorId)) {
        console.log(`Operator ${operatorId} has no result to export`);
        return;
      }
      const operator = this.workflowActionService.getTexeraGraph().getOperator(operatorId);
      const operatorName = operator.customDisplayName ?? operator.operatorType;

      /*
       * This function (and service) was previously used to export result
       *  into the local file system (downloading). Currently it is used to only
       *  export to the dataset.
       *  TODO: refactor this service to have export namespace and download should be
       *   an export type (export to local file system)
       *  TODO: rowIndex and columnIndex can be used to export a specific cells in the result
       */
      this.downloadService
        .exportWorkflowResult(
          exportType,
          workflowId,
          workflowName,
          operatorId,
          operatorName,
          [...datasetIds],
          rowIndex,
          columnIndex,
          filename
        )
        .subscribe({
          next: _ => {
            this.notificationService.info("The result has been exported successfully");
          },
          error: (res: unknown) => {
            const errorResponse = res as { error: { error: string } };
            this.notificationService.error(
              "An error happened in exporting operator results " + errorResponse.error.error
            );
          },
        });
    });
  }

  /**
   * Helper method to fetch all paginated results and convert them to a CSV Blob.
   */
  private fetchAllPaginatedResultsAsCSV(
    paginatedResultService: OperatorPaginationResultService,
    operatorId: string
  ): Observable<{ filename: string; blob: Blob }[]> {
    return new Observable(observer => {
      const results: any[] = [];
      let currentPage = 1;
      const pageSize = 10;

      paginatedResultService
        .selectPage(currentPage, pageSize)
        .pipe(
          expand((pageData: PaginatedResultEvent) => {
            results.push(...pageData.table);
            if (pageData.table.length === pageSize) {
              currentPage++;
              return paginatedResultService.selectPage(currentPage, pageSize);
            } else {
              return EMPTY;
            }
          }),
          finalize(() => {
            const { filename, blob } = this.createCSVBlob(results, operatorId);
            observer.next([{ filename, blob }]);
            observer.complete();
          })
        )
        .subscribe();
    });
  }

  /**
   * Helper method to fetch visualization results and convert them to HTML Blobs.
   */
  private fetchVisualizationResultsAsHTML(
    resultService: OperatorResultService,
    operatorId: string
  ): Observable<{ filename: string; blob: Blob }[]> {
    return new Observable(observer => {
      const snapshot = resultService.getCurrentResultSnapshot();
      const files: { filename: string; blob: Blob }[] = [];

      snapshot?.forEach((s: any, index: number) => {
        const fileContent = Object(s)["html-content"];
        const blob = new Blob([fileContent], { type: "text/html;charset=utf-8" });
        const filename = `result_${operatorId}_${index + 1}.html`;
        files.push({ filename, blob });
      });

      observer.next(files);
      observer.complete();
    });
  }

  /**
   * Convert the results array into CSV format and create a Blob.
   */
  private createCSVBlob(results: any[], operatorId: string): { filename: string; blob: Blob } {
    const csv = Papa.unparse(results); // Convert array of objects to CSV
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const filename = `result_${operatorId}.csv`;
    return { filename, blob };
  }

  /**
   * Reset flags if the user leave workspace
   */
  public resetFlags(): void {
    this.hasResultToExportOnHighlightedOperators = false;
    this.hasResultToExportOnAllOperators = new BehaviorSubject<boolean>(false);
  }

  getExportOnAllOperatorsStatusStream(): Observable<boolean> {
    return this.hasResultToExportOnAllOperators.asObservable();
  }
}
