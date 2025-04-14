import { HttpClient, HttpHeaders } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable, ReplaySubject, of } from "rxjs";
import { catchError, map } from "rxjs/operators";
import { AppSettings } from "../../../common/app-setting";
import { WorkflowActionService } from "../workflow-graph/model/workflow-action.service";
import { WorkflowCompilingService } from "../compile-workflow/workflow-compiling.service";
import { Workflow } from "../../../common/type/workflow";
import { WorkflowResultService } from "../workflow-result/workflow-result.service";

// Define the WorkflowSuggestion interface - this should match the interface in the component
export interface WorkflowSuggestion {
  id: string;
  description: string;
  operatorsToAdd: {
    operatorType: string;
    position: { x: number, y: number };
    properties?: object;
  }[];
  operatorPropertiesToChange: {
    operatorId: string;
    properties: object;
  }[];
  operatorsToDelete: string[]; // IDs of operators to delete
  linksToAdd: {
    source: { operatorId: string, portId: string };
    target: { operatorId: string, portId: string };
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
export const WORKFLOW_SUGGESTION_ENDPOINT = "api/suggest";

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

  constructor(
    private httpClient: HttpClient,
    private workflowActionService: WorkflowActionService,
    private workflowCompilingService: WorkflowCompilingService,
    private workflowResultService: WorkflowResultService
  ) {}

  /**
   * Requests workflow suggestions from the backend service.
   * This method gathers the current workflow state, compilation information,
   * and result data, then sends it to the backend to generate suggestions.
   * 
   * @returns Observable of workflow suggestions
   */
  public getSuggestions(): Observable<WorkflowSuggestion[]> {
    // Get the current workflow
    const workflow: Workflow = this.workflowActionService.getWorkflow();
    
    // Get compilation state info
    const compilationState = {
      state: this.workflowCompilingService.getWorkflowCompilationState(),
      physicalPlan: undefined,
      operatorInputSchemaMap: {},
      operatorErrors: this.workflowCompilingService.getWorkflowCompilationErrors()
    };

    // Get result tables for all operators that have result data
    const resultTables: Record<string, { rows: object[], columnNames: string[] }> = {};
    
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
          paginatedResultService.selectPage(1, 10).subscribe(
            pageData => {
              rows = pageData.table as object[];
            }
          );
        }
        
        resultTables[operatorId] = {
          rows: rows,
          columnNames: columnNames
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
            columnNames: columnNames
          };
        }
      }
    });

    // Prepare the request body
    const requestBody = {
      workflow: JSON.stringify(workflow),
      compilationState: compilationState,
      resultTables: resultTables
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
        catchError(error => {
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
   * Refresh suggestions by requesting new ones from the backend.
   * This will update the suggestion stream.
   */
  public refreshSuggestions(): void {
    this.getSuggestions().subscribe();
  }
} 