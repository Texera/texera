import { HttpClient, HttpHeaders } from "@angular/common/http";
import { Injectable, Injector, Inject, Optional } from "@angular/core";
import { Observable, ReplaySubject, of, merge, BehaviorSubject } from "rxjs";
import { catchError, map, debounceTime, filter } from "rxjs/operators";
import { AppSettings } from "../../../common/app-setting";
import { WorkflowActionService } from "../workflow-graph/model/workflow-action.service";
import { Workflow } from "../../../common/type/workflow";
import { WorkflowResultService } from "../workflow-result/workflow-result.service";
import { ExecuteWorkflowService } from "../execute-workflow/execute-workflow.service";
import { ExecutionState, ExecutionStateInfo } from "../../types/execute-workflow.interface";
// Import the WorkflowCompilingService type for better type checking
import { WorkflowCompilingService } from "../compile-workflow/workflow-compiling.service";
import { CompilationState } from "../../types/workflow-compiling.interface";

// Define the WorkflowSuggestion interface - this should match the interface in the component
export interface WorkflowSuggestion {
  id: string;
  description: string;
  operatorsToAdd: {
    operatorType: string;
    position: { x: number; y: number };
    properties?: object;
  }[];
  operatorPropertiesToChange: {
    operatorId: string;
    properties: object;
  }[];
  operatorsToDelete: string[]; // IDs of operators to delete
  linksToAdd: {
    source: { operatorId: string; portId: string };
    target: { operatorId: string; portId: string };
  }[];
  isPreviewActive: boolean;
}

// Define an interface for result panel data
interface ResultPanelData {
  operatorID: string;
  currentResult?: object[];
  columnKeys?: string[];
}

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
  private suggestionStream = new ReplaySubject<WorkflowSuggestion[]>(1);

  // Stream that indicates whether a preview is currently active
  private previewActiveStream = new BehaviorSubject<boolean>(false);

  // Track previous execution state to avoid duplicate requests
  private previousExecutionState: ExecutionState = ExecutionState.Uninitialized;

  // Track when we've last requested suggestions to debounce multiple result updates
  private lastResultUpdateTime = 0;
  private resultUpdateDebounceMs = 2000; // 2 seconds debounce time for result updates

  constructor(
    private httpClient: HttpClient,
    private workflowActionService: WorkflowActionService,
    private workflowResultService: WorkflowResultService,
    private executeWorkflowService: ExecuteWorkflowService,
    private injector: Injector,
    private workflowCompilingService: WorkflowCompilingService
  ) {

    // Comment out the subscription to make the suggesetion not that often
    // // Listen for workflow changes and refresh suggestions
    // // This follows the same pattern as WorkflowCompilingService
    // merge(
    //   this.workflowActionService.getTexeraGraph().getLinkAddStream(),
    //   this.workflowActionService.getTexeraGraph().getLinkDeleteStream(),
    //   this.workflowActionService.getTexeraGraph().getOperatorAddStream(),
    //   this.workflowActionService.getTexeraGraph().getOperatorDeleteStream(),
    //   this.workflowActionService.getTexeraGraph().getOperatorPropertyChangeStream(),
    //   this.workflowActionService.getTexeraGraph().getDisabledOperatorsChangedStream()
    // )
    //   .pipe(
    //     // Debounce to avoid too many requests during rapid changes
    //     debounceTime(1000),
    //     // Skip refreshing if preview is active
    //     filter(() => !this.previewActiveStream.getValue())
    //   )
    //   .subscribe(() => {
    //     console.log("WorkflowSuggestionService: Workflow change detected");
    //     // Only refresh if there are operators in the workflow
    //     const operators = this.workflowActionService.getTexeraGraph().getAllOperators();
    //     if (operators.length > 0) {
    //       console.log(`WorkflowSuggestionService: Refreshing with ${operators.length} operators`);
    //       this.refreshSuggestions();
    //     }
    //   });

    // Subscribe to execution state changes to refresh suggestions
    this.executeWorkflowService
      .getExecutionStateStream()
      .pipe(
        filter(event => {
          const currentState = event.current.state;
          const previousState = event.previous.state;
          const targetStates = [ExecutionState.Completed, ExecutionState.Failed, ExecutionState.Paused];

          // Only refresh when:
          // 1. The state has changed (prev != current)
          // 2. Current state is one of our target states
          // 3. Current state is different from our tracked previous state (to avoid duplicate refreshes)
          // 4. Preview is not active
          const shouldRefresh =
            previousState !== currentState &&
            targetStates.includes(currentState) &&
            this.previousExecutionState !== currentState &&
            !this.previewActiveStream.getValue();

          // Update our tracked previous state
          if (shouldRefresh) {
            this.previousExecutionState = currentState;
          }

          return shouldRefresh;
        })
      )
      .subscribe(event => {
        console.log(
          `WorkflowSuggestionService: Execution state changed from ${event.previous.state} to ${event.current.state}`
        );
        const operators = this.workflowActionService.getTexeraGraph().getAllOperators();
        if (operators.length > 0) {
          console.log("WorkflowSuggestionService: Refreshing suggestions after execution state change");
          this.refreshSuggestions();
        }
      });

    // // Subscribe to workflow result updates
    // this.workflowResultService
    //   .getResultUpdateStream()
    //   .pipe(
    //     // Skip if preview is active
    //     filter(() => !this.previewActiveStream.getValue())
    //   )
    //   .subscribe(resultUpdate => {
    //     // Only process if there are new results and we're not in a preview
    //     if (resultUpdate && Object.keys(resultUpdate).length > 0) {
    //       console.log("WorkflowSuggestionService: Result update detected for operators:", Object.keys(resultUpdate));
    //
    //       // Debounce multiple result updates that come in quick succession
    //       const now = Date.now();
    //       if (now - this.lastResultUpdateTime > this.resultUpdateDebounceMs) {
    //         this.lastResultUpdateTime = now;
    //
    //         // Refresh suggestions with the new results
    //         const operators = this.workflowActionService.getTexeraGraph().getAllOperators();
    //         if (operators.length > 0) {
    //           console.log("WorkflowSuggestionService: Refreshing suggestions after result update");
    //           this.refreshSuggestions();
    //         }
    //       } else {
    //         console.log("WorkflowSuggestionService: Skipping result update refresh (debounce)");
    //       }
    //     }
    //   });
  }

  /**
   * Requests workflow suggestions from the backend service.
   * This method gathers the current workflow state, compilation information,
   * and result data, then sends it to the backend to generate suggestions.
   *
   * @returns Observable of workflow suggestions
   */
  public getSuggestions(): Observable<WorkflowSuggestion[]> {
    // Skip if preview is active
    if (this.previewActiveStream.getValue()) {
      console.log("WorkflowSuggestionService: Preview active, skipping suggestion request");
      return of([]);
    }

    // Get the current workflow
    const workflow: Workflow = this.workflowActionService.getWorkflow();


    let compilationState = {
      state: this.workflowCompilingService.getWorkflowCompilationState(),
      physicalPlan: undefined,
      operatorInputSchemaMap: this.workflowCompilingService.getOperatorInputSchemaMap(),
      operatorErrors: this.workflowCompilingService.getWorkflowCompilationErrors()
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

    // Send the request to the backend
    return this.httpClient
      .post<WorkflowSuggestion[]>(
        `${AppSettings.getApiEndpoint()}/${WORKFLOW_SUGGESTION_ENDPOINT}`,
        JSON.stringify(requestBody),
        {
          headers: new HttpHeaders({
            "Content-Type": "application/json",
          }),
        }
      )
      .pipe(
        map(suggestions => {
          // Store the suggestions in the stream for other components to subscribe
          this.suggestionStream.next(suggestions);
          return suggestions;
        }),
        catchError((error: unknown) => {
          console.error("Error getting workflow suggestions:", error);
          return of([]);
        })
      );
  }

  /**
   * Get an observable stream of workflow suggestions.
   * Components can subscribe to this stream to get the latest suggestions.
   *
   * @returns Observable of workflow suggestions
   */
  public getSuggestionStream(): Observable<WorkflowSuggestion[]> {
    return this.suggestionStream.asObservable();
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

  /**
   * Refresh suggestions by requesting new ones from the backend.
   * This will update the suggestion stream.
   *
   * @param isPreview If true, skip the actual request to the backend (used during preview mode)
   */
  public refreshSuggestions(isPreview: boolean = false): void {
    console.log(`Refreshing suggestions in service (isPreview: ${isPreview})`);

    // Skip the request if in preview mode or if preview is active from elsewhere
    if (isPreview || this.previewActiveStream.getValue()) {
      console.log("Preview mode: skipping suggestion refresh request");
      return;
    }

    // Log the operators in the workflow to debug
    const operators = this.workflowActionService.getTexeraGraph().getAllOperators();
    console.log(
      `Current operators (${operators.length}):`,
      operators.map(op => op.operatorID)
    );

    // Log the current execution state
    console.log(`Current execution state: ${this.executeWorkflowService.getExecutionState().state}`);

    this.getSuggestions().subscribe(
      suggestions => console.log(`Received ${suggestions.length} suggestions from backend`),
      (error: unknown) => console.error("Error refreshing suggestions:", error)
    );
  }
}
