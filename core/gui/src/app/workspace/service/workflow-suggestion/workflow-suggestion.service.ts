import { HttpClient, HttpHeaders } from "@angular/common/http";
import { Injectable, OnDestroy } from "@angular/core";
import { Observable, of, BehaviorSubject, pipe, timer } from "rxjs";
import { catchError, map } from "rxjs/operators";
import { AppSettings } from "../../../common/app-setting";
import { Workflow } from "../../../common/type/workflow";
import { ExecutionStateInfo } from "../../types/execute-workflow.interface";
// Import the WorkflowCompilingService type for better type checking
import { WorkflowSuggestionList } from "../../types/workflow-suggestion.interface";
import { v4 as uuid } from "uuid";
import { CompilationStateInfo } from "../../types/workflow-compiling.interface";

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
export class WorkflowSuggestionService implements OnDestroy {
  // Stream that indicates whether a preview is currently active - initialized to false
  private previewActiveStream = new BehaviorSubject<boolean>(false);
  // Flag to ignore workflow changes during preview activation/deactivation
  private mock = false; // Set to true to enable mock mode

  constructor(private httpClient: HttpClient) {
    // Ensure preview is false on initial load
    this.resetPreviewState();
  }

  ngOnDestroy(): void {
    // Make sure preview state is reset when service is destroyed
    this.resetPreviewState();
  }

  /**
   * Requests workflow suggestions from the backend service.
   * This method gathers the current workflow state, compilation information,
   * and result data, then sends it to the backend to generate suggestions.
   *
   * @returns Observable of workflow suggestions
   */
  public getSuggestions(
    workflow: Workflow,
    compilationState: CompilationStateInfo,
    executionState: ExecutionStateInfo
  ): Observable<WorkflowSuggestionList> {
    // Skip if preview is active
    if (this.previewActiveStream.getValue()) {
      return of({ suggestions: [] });
    }

    if (this.mock) {
      return of(this.MOCK_SUGGESTIONS);
    }

    return this.httpClient
      .post<WorkflowSuggestionList>(
        `${AppSettings.getApiEndpoint()}/${WORKFLOW_SUGGESTION_ENDPOINT}`,
        {
          workflow: JSON.stringify(workflow),
          compilationState: compilationState,
          executionState: executionState,
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

    // Set the preview state
    this.previewActiveStream.next(isActive);
  }

  /**
   * Reset the preview state to false.
   * This should be called when navigating away from the workspace or
   * when the component is destroyed.
   */
  public resetPreviewState(): void {
    this.setPreviewActive(false);
  }

  MOCK_SUGGESTIONS: WorkflowSuggestionList = {
    suggestions: [
      {
        suggestionID: "suggestion-0",
        suggestion: "Add aggregation to compute summary statistics for tweet data.",
        suggestionType: "improve",
        changes: {
          operatorsToAdd: [
            {
              operatorType: "Aggregate",
              operatorID: "Aggregate-operator-new-1",
              operatorProperties: {
                aggregations: [
                  {
                    aggFunction: "sum",
                    attribute: "favorite_count",
                    "result attribute": "total_favorite_count",
                  },
                  {
                    aggFunction: "average",
                    attribute: "retweet_count",
                    "result attribute": "average_retweet_count",
                  },
                ],
                groupByKeys: ["create_at_month"],
              },
              customDisplayName: "Aggregate Tweet Data",
            },
          ],
          linksToAdd: [
            {
              linkID: "link-8af0915c-6ccc-439a-bf05-1b39cefdbbfd",
              source: {
                operatorID: "Sort-operator-3985eaf1-5af2-4f4a-bd0f-1c4b3f7e78c2",
                portID: "output-0",
              },
              target: {
                operatorID: "Aggregate-operator-new-1",
                portID: "input-0",
              },
            },
          ],
          operatorsToDelete: [],
        },
      },
      {
        suggestionID: "suggestion-1",
        suggestion: "Enhance the workflow with sentiment analysis of tweets.",
        suggestionType: "improve",
        changes: {
          operatorsToAdd: [
            {
              operatorType: "HuggingFaceSentimentAnalysis",
              operatorID: "HuggingFaceSentimentAnalysis-5226",
              operatorProperties: {
                attribute: "text",
                "Positive result attribute": "positive_sentiment",
                "Neutral result attribute": "neutral_sentiment",
                "Negative result attribute": "negative_sentiment",
              },
              customDisplayName: "Sentiment Analysis",
            },
          ],
          linksToAdd: [
            {
              linkID: "link-e20f28c0-2da3-406c-be8a-df2a41ff22e3",
              source: {
                operatorID: "PythonUDFV2-operator-3e3c9f53-dae3-4dc4-b724-7ffdb8e7b80c",
                portID: "output-0",
              },
              target: {
                operatorID: "HuggingFaceSentimentAnalysis-5226",
                portID: "input-0",
              },
            },
          ],
          operatorsToDelete: [],
        },
      },
    ],
  };
}
