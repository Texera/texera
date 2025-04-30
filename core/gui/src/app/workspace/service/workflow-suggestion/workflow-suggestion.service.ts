import { HttpClient, HttpHeaders } from "@angular/common/http";
import { Injectable, Injector, Inject, Optional } from "@angular/core";
import { Observable, ReplaySubject, of, merge, BehaviorSubject, pipe } from "rxjs";
import { catchError, map, debounceTime, filter } from "rxjs/operators";
import { AppSettings } from "../../../common/app-setting";
import { WorkflowActionService } from "../workflow-graph/model/workflow-action.service";
import { Workflow } from "../../../common/type/workflow";
import { WorkflowResultService } from "../workflow-result/workflow-result.service";
import { ExecuteWorkflowService } from "../execute-workflow/execute-workflow.service";
import { ExecutionState, ExecutionStateInfo } from "../../types/execute-workflow.interface";
// Import the WorkflowCompilingService type for better type checking
import { WorkflowCompilingService } from "../compile-workflow/workflow-compiling.service";
import { WorkflowSuggestionList } from "../../types/workflow-suggestion.interface";
import { v4 as uuid } from "uuid";

// endpoint for workflow suggestions
export const WORKFLOW_SUGGESTION_ENDPOINT = "workflow-suggestion";

/**
 * WorkflowSuggestionService is responsible for communicating with the backend suggestion service.
 * It gathers the necessary data (workflow, compilation state, and result data) and sends it to
 * the backend to generate workflow suggestions.
 */
@Injectable({
  providedIn: "root",
})
export class WorkflowSuggestionService {
  // Stream that indicates whether a preview is currently active
  private previewActiveStream = new BehaviorSubject<boolean>(false);

  constructor(
    private httpClient: HttpClient,
    private workflowActionService: WorkflowActionService,
    private workflowResultService: WorkflowResultService,
    private executeWorkflowService: ExecuteWorkflowService,
    private workflowCompilingService: WorkflowCompilingService
  ) {}

  /**
   * Requests workflow suggestions from the backend service.
   * This method gathers the current workflow state, compilation information,
   * and result data, then sends it to the backend to generate suggestions.
   *
   * @returns Observable of workflow suggestions
   */
  public getSuggestions(): Observable<WorkflowSuggestionList> {
    // Skip if preview is active
    if (this.previewActiveStream.getValue()) {
      return of({ suggestions: [] });
    }

    // Get the current workflow
    const workflow: Workflow = this.workflowActionService.getWorkflow();

    let compilationState = {
      state: this.workflowCompilingService.getWorkflowCompilationState(),
      physicalPlan: undefined,
      operatorInputSchemaMap: this.workflowCompilingService.getOperatorInputSchemaMap(),
      operatorErrors: this.workflowCompilingService.getWorkflowCompilationErrors(),
    };

    // Get execution state info
    const executionState = this.executeWorkflowService.getExecutionState();

    // Get result tables for all operators that have result data
    const resultTables: Record<string, { rows: object[]; columnNames: string[] }> = {};

    // Get the results from all operators
    const operators = this.workflowActionService.getTexeraGraph().getAllOperators();

    operators.forEach(operator => {
      const operatorId = operator.operatorID;

      // Check if this operator has paginated results
      const paginatedResultService = this.workflowResultService.getPaginatedResultService(operatorId);
      if (paginatedResultService) {
        // Get schema attributes for column names
        const schema = paginatedResultService.getSchema();
        const columnNames = schema.map(attr => attr.attributeName);

        // Select the first page of data (typically 10 rows)
        // We're using a synchronous approach here to simplify things
        let rows: object[] = [];

        // If there are results, try to get them
        if (paginatedResultService.getCurrentTotalNumTuples() > 0) {
          paginatedResultService.selectPage(1, 10).subscribe(pageData => {
            rows = pageData.table as object[];
          });
        }

        resultTables[operatorId] = {
          rows: rows,
          columnNames: columnNames,
        };
      }

      // Check if this operator has non-paginated results
      const resultService = this.workflowResultService.getResultService(operatorId);
      if (resultService) {
        // Get the current result snapshot
        const resultSnapshot = resultService.getCurrentResultSnapshot();
        if (resultSnapshot) {
          // Since this is non-paginated data (likely visualization data),
          // we might not have schema information, so we'll extract column names from the first object
          const firstRow = resultSnapshot[0] || {};
          const columnNames = Object.keys(firstRow);

          resultTables[operatorId] = {
            rows: Array.from(resultSnapshot),
            columnNames: columnNames,
          };
        }
      }
    });

    // Prepare the request body
    const requestBody = {
      workflow: JSON.stringify(workflow),
      compilationState: compilationState,
      executionState: executionState,
      resultTables: resultTables,
    };

    return this.httpClient
      .post<WorkflowSuggestionList>(
        `${AppSettings.getApiEndpoint()}/${WORKFLOW_SUGGESTION_ENDPOINT}`,
        JSON.stringify(requestBody),
        {
          headers: new HttpHeaders({
            "Content-Type": "application/json",
          }),
        }
      )
      .pipe(
        map(suggestionList => {
          suggestionList.suggestions.forEach(suggestion => {
            suggestion.suggestionID = `suggestion-${uuid()}`;
          });
          return suggestionList;
        }),
        catchError((error: unknown) => {
          console.error("Error getting workflow suggestions:", error);
          return of({ suggestions: [] });
        })
      );
  }

  /**
   * Get an observable stream indicating whether a preview is active.
   * Components can subscribe to this to know when to skip certain operations.
   *
   * @returns Observable boolean indicating if a preview is active
   */
  public getPreviewActiveStream(): Observable<boolean> {
    return this.previewActiveStream.asObservable();
  }

  /**
   * Set whether a preview is currently active.
   * This will notify all subscribers to the preview active stream.
   *
   * @param isActive Whether a preview is currently active
   */
  public setPreviewActive(isActive: boolean): void {
    console.log(`WorkflowSuggestionService: Setting preview active to ${isActive}`);
    this.previewActiveStream.next(isActive);
    this.workflowCompilingService.setPreviewActive(isActive);
  }
}
