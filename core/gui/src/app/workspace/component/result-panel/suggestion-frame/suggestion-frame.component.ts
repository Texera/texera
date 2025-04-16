import { Component, OnInit, HostListener, OnDestroy } from "@angular/core";
import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { WorkflowActionService } from "../../../service/workflow-graph/model/workflow-action.service";
import { WorkflowSuggestionService } from "../../../service/workflow-suggestion/workflow-suggestion.service";
import { WorkflowUtilService } from "../../../service/workflow-graph/util/workflow-util.service";
import { NzMessageService } from "ng-zorro-antd/message";
import { WorkflowPersistService } from "../../../../common/service/workflow-persist/workflow-persist.service";
import { Workflow } from "../../../../common/type/workflow";
import { cloneDeep, isEqual } from "lodash";
import { ExecuteWorkflowService } from "../../../service/execute-workflow/execute-workflow.service";
import { ExecutionState } from "../../../types/execute-workflow.interface";
import { filter, take } from "rxjs/operators";
import { WorkflowCompilingService } from "../../../service/compile-workflow/workflow-compiling.service";
import { Subject, Subscription, interval } from "rxjs";
import { CompilationState } from "../../../types/workflow-compiling.interface";

/**
 * SuggestionFrameComponent is a wrapper for the workflow suggestion functionality
 * that allows it to be displayed in the result panel as a tab.
 */
@UntilDestroy()
@Component({
  selector: "texera-suggestion-frame",
  templateUrl: "./suggestion-frame.component.html",
  styleUrls: ["./suggestion-frame.component.scss"],
})
export class SuggestionFrameComponent implements OnInit, OnDestroy {
  // Variables needed for suggestion functionality
  public suggestions: any[] = [];
  public activePreviewId: string | null = null;
  public canModify = true;
  public loadingSuggestions = false;

  // Store the workflow state before a preview is applied
  private workflowBeforePreview: Workflow | null = null;

  // Store original operator properties to compare with changed properties
  private originalOperatorProperties: Map<string, object> = new Map();

  // Store property style maps for highlighting changed properties
  private propertyStyleMaps: Map<string, Map<String, String>> = new Map();

  // Track if we're in preview mode to prevent tab changes
  public isInPreviewMode = false;
  // Track the subscription to compilation state changed stream
  private compilationStateSubscription: Subscription | null = null;
  // Custom subject to handle compilation state changes during preview
  private previewCompilationSubject = new Subject<CompilationState>();
  // Track the tab focus interval
  private tabFocusInterval: Subscription | null = null;
  // Store click listeners to prevent unnecessary event binding
  private boundHandleDocumentClick: any;

  constructor(
    private workflowActionService: WorkflowActionService,
    private workflowUtilService: WorkflowUtilService,
    private messageService: NzMessageService,
    private workflowPersistService: WorkflowPersistService,
    private workflowSuggestionService: WorkflowSuggestionService,
    private executeWorkflowService: ExecuteWorkflowService,
    private workflowCompilingService: WorkflowCompilingService
  ) {
    this.boundHandleDocumentClick = this.handleDocumentClick.bind(this);
  }

  ngOnInit(): void {
    // Subscribe to suggestion service
    this.workflowSuggestionService
      .getSuggestionStream()
      .pipe(untilDestroyed(this))
      .subscribe(suggestions => {
        // Only update suggestions if not in preview mode
        if (!this.isInPreviewMode) {
          this.suggestions = suggestions;
          this.loadingSuggestions = false;
        }
      });

    // Get initial permission state
    this.workflowActionService
      .getWorkflowModificationEnabledStream()
      .pipe(untilDestroyed(this))
      .subscribe(canModify => (this.canModify = canModify));

    // Monitor execution state to refresh suggestions when workflow completes or fails
    this.executeWorkflowService
      .getExecutionStateStream()
      .pipe(
        filter(
          event => event.current.state === ExecutionState.Completed || event.current.state === ExecutionState.Failed
        ),
        untilDestroyed(this)
      )
      .subscribe(event => {
        if (!this.isInPreviewMode) {
          console.log(`SuggestionFrame: Execution state changed to ${event.current.state}, refreshing suggestions`);
          this.refreshSuggestions();
        } else {
          console.log(
            `SuggestionFrame: Execution state changed to ${event.current.state}, but preview is active - skipping refresh`
          );
        }
      });

    // Initial refresh of suggestions
    this.refreshSuggestions();
  }

  ngOnDestroy(): void {
    // Clean up event listener when component is destroyed
    this.removeDocumentClickListener();
    this.restoreCompilationListeners();

    // Clean up the tab focus interval if it exists
    if (this.tabFocusInterval) {
      this.tabFocusInterval.unsubscribe();
      this.tabFocusInterval = null;
    }
  }

  /**
   * Add document click listener when entering preview mode
   */
  private addDocumentClickListener(): void {
    if (!document.hasOwnProperty("suggestionsClickListener")) {
      document.addEventListener("click", this.boundHandleDocumentClick, true);
      // @ts-ignore
      document.suggestionsClickListener = true;
    }
  }

  /**
   * Remove document click listener when exiting preview mode
   */
  private removeDocumentClickListener(): void {
    document.removeEventListener("click", this.boundHandleDocumentClick, true);
    // @ts-ignore
    delete document.suggestionsClickListener;
  }

  /**
   * Event handler to prevent tab changes during preview mode
   */
  private handleDocumentClick(event: MouseEvent): void {
    if (this.isInPreviewMode) {
      // Get the closest button within our component to avoid blocking action buttons
      const actionButton = (event.target as HTMLElement).closest(".suggestion-actions button");
      // Get the event target
      const target = event.target as HTMLElement;

      // Allow clicks within our component actions area
      if (actionButton) {
        return;
      }

      // Check if the click target is a tab or a link that would change focus
      if (target && (target.closest(".ant-tabs-tab") || target.closest("a[href]"))) {
        // Only prevent default if it's not within our suggestion component
        if (!target.closest("texera-suggestion-frame")) {
          event.preventDefault();
          event.stopPropagation();
          this.messageService.warning("Please cancel the preview first before changing tabs");

          // Refocus the suggestion tab
          this.focusSuggestionTab();
        }
      }
    }
  }

  /**
   * Refreshes the workflow suggestions
   */
  public refreshSuggestions(): void {
    // Only refresh if there are operators in the workflow and not in preview mode
    if (this.isInPreviewMode) {
      console.log("Preview mode is active, skipping suggestion refresh");
      return;
    }

    const operators = this.workflowActionService.getTexeraGraph().getAllOperators();
    if (operators.length > 0) {
      this.loadingSuggestions = true;
      this.workflowSuggestionService.refreshSuggestions();
    }
  }

  /**
   * Toggles the preview of a suggestion
   */
  public togglePreview(suggestion: any): void {
    // If there's an active preview, clear it and restore the previous workflow
    if (this.activePreviewId) {
      this.clearPreviewAndRestoreWorkflow();
    }

    if (this.activePreviewId === suggestion.id) {
      // Deactivate preview if clicking the same suggestion (already handled by clearing above)
      this.activePreviewId = null;
      this.isInPreviewMode = false;

      // Notify the suggestion service that preview is no longer active
      this.workflowSuggestionService.setPreviewActive(false);

      // Remove the document click listener
      this.removeDocumentClickListener();

      // Clear the tab focus interval
      if (this.tabFocusInterval) {
        this.tabFocusInterval.unsubscribe();
        this.tabFocusInterval = null;
      }
    } else {
      // Save the current workflow state before creating the preview
      this.saveWorkflowState();

      // Clear property style maps and original properties
      this.propertyStyleMaps.clear();
      this.originalOperatorProperties.clear();

      // Set preview mode to prevent tab changes and suggestion updates
      this.isInPreviewMode = true;

      // Add the document click listener to prevent tab changes
      this.addDocumentClickListener();

      // Notify the suggestion service that preview is active
      this.workflowSuggestionService.setPreviewActive(true);

      // Activate preview for this suggestion
      this.activePreviewId = suggestion.id;

      // Create the preview without requesting new suggestions from backend
      this.createPreview(suggestion);

      // Focus on this tab programmatically
      this.focusSuggestionTab();

      // Notify user that they're in preview mode
      this.messageService.info("Preview mode active. Any compilation errors will be ignored.");
    }
  }

  /**
   * Disables compilation listeners to prevent tab switching during preview
   */
  private disableCompilationListeners(): void {
    // We no longer need to intercept compilation changes since the service is now notified
    // about preview mode and will handle this internally
  }

  /**
   * Restores original compilation listeners
   */
  private restoreCompilationListeners(): void {
    // We no longer need to restore compilation listeners since the service handles this

    // Just clear the tab focus interval if it exists
    if (this.tabFocusInterval) {
      this.tabFocusInterval.unsubscribe();
      this.tabFocusInterval = null;
    }
  }

  /**
   * Focuses on the suggestion tab to prevent tab changes
   */
  private focusSuggestionTab(): void {
    // Find the suggestion tab in the result panel and focus on it initially
    setTimeout(() => {
      const suggestionTab = document.querySelector(".ant-tabs-tab[aria-controls*=\"Suggestions\"]") as HTMLElement;
      if (suggestionTab) {
        suggestionTab.click();
        console.log("Focused on suggestion tab");

        // Set up an interval to keep the suggestion tab focused during preview mode
        if (this.isInPreviewMode) {
          // Clear any existing interval first
          if (this.tabFocusInterval) {
            this.tabFocusInterval.unsubscribe();
          }

          // Start a new interval that checks and refocuses the suggestion tab if needed
          this.tabFocusInterval = interval(300)
            .pipe(untilDestroyed(this))
            .subscribe(() => {
              if (this.isInPreviewMode) {
                const activeTab = document.querySelector(".ant-tabs-tab-active");
                const isSuggestionTabActive =
                  activeTab &&
                  (activeTab.textContent?.includes("Suggestions") ||
                    activeTab.getAttribute("aria-controls")?.includes("Suggestions"));

                if (!isSuggestionTabActive) {
                  console.log("Refocusing suggestion tab...");
                  const suggestionTab = document.querySelector(
                    ".ant-tabs-tab[aria-controls*=\"Suggestions\"]"
                  ) as HTMLElement;
                  if (suggestionTab) {
                    suggestionTab.click();
                  }
                }
              } else if (this.tabFocusInterval) {
                // If we're no longer in preview mode, clear the interval
                this.tabFocusInterval.unsubscribe();
                this.tabFocusInterval = null;
              }
            });
        }
      }
    }, 50);
  }

  /**
   * Applies a suggestion to the workflow
   */
  public applySuggestion(suggestion: any): void {
    // First clear any previews and restore the original workflow
    this.clearPreviewAndRestoreWorkflow();

    // Then apply the changes for real
    try {
      const texeraGraph = this.workflowActionService.getTexeraGraph();

      // Delete operators first
      if (suggestion.operatorsToDelete && suggestion.operatorsToDelete.length > 0) {
        this.workflowActionService.deleteOperatorsAndLinks(suggestion.operatorsToDelete);
      }

      // Add operators
      const operatorsToAdd = suggestion.operatorsToAdd.map((opToAdd: any) => {
        const operatorPredicate = this.workflowUtilService.getNewOperatorPredicate(opToAdd.operatorType);

        // Set properties if provided
        if (opToAdd.properties) {
          Object.assign(operatorPredicate.operatorProperties, opToAdd.properties);
        }

        return {
          op: operatorPredicate,
          pos: opToAdd.position,
        };
      });

      // Add all operators at once
      this.workflowActionService.addOperatorsAndLinks(operatorsToAdd);

      // Add links
      suggestion.linksToAdd.forEach((linkToAdd: any) => {
        let sourceOperatorId = linkToAdd.source.operatorId;
        let targetOperatorId = linkToAdd.target.operatorId;

        // Map operator IDs if needed
        if (!texeraGraph.hasOperator(sourceOperatorId)) {
          const newOperator = operatorsToAdd.find((op: any) => op.op.operatorType === sourceOperatorId.split("-")[0]);
          if (newOperator) sourceOperatorId = newOperator.op.operatorID;
        }

        if (!texeraGraph.hasOperator(targetOperatorId)) {
          const newOperator = operatorsToAdd.find((op: any) => op.op.operatorType === targetOperatorId.split("-")[0]);
          if (newOperator) targetOperatorId = newOperator.op.operatorID;
        }

        if (texeraGraph.hasOperator(sourceOperatorId) && texeraGraph.hasOperator(targetOperatorId)) {
          const link = {
            linkID: `link-${Date.now()}`,
            source: {
              operatorID: sourceOperatorId,
              portID: linkToAdd.source.portId,
            },
            target: {
              operatorID: targetOperatorId,
              portID: linkToAdd.target.portId,
            },
          };

          this.workflowActionService.addLink(link);
        }
      });

      // Change properties for existing operators
      if (suggestion.operatorPropertiesToChange) {
        suggestion.operatorPropertiesToChange.forEach((propChange: any) => {
          if (texeraGraph.hasOperator(propChange.operatorId)) {
            const operator = texeraGraph.getOperator(propChange.operatorId);
            this.workflowActionService.setOperatorProperty(propChange.operatorId, {
              ...operator.operatorProperties,
              ...propChange.properties,
            });
          }
        });
      }

      // Save the workflow to materialize the changes
      const workflow = this.workflowActionService.getWorkflow();
      this.workflowPersistService.persistWorkflow(workflow).subscribe(() => {
        this.messageService.success("Successfully applied and saved the suggestion!");

        // Remove the applied suggestion from the list
        this.suggestions = this.suggestions.filter(s => s.id !== suggestion.id);
      });
    } catch (error) {
      console.error("Error applying suggestion:", error);
      this.messageService.error("Failed to apply the suggestion.");
    }
  }

  /**
   * Removes a suggestion from the list
   */
  public dislikeSuggestion(suggestion: any): void {
    // If this is the active suggestion, restore the workflow first
    if (this.activePreviewId === suggestion.id) {
      this.clearPreviewAndRestoreWorkflow();
    }

    // Remove the suggestion from the list
    this.suggestions = this.suggestions.filter(s => s.id !== suggestion.id);

    // Show a message to confirm the action
    this.messageService.info("Suggestion removed from the list.");
  }

  /**
   * Cancels the preview of a suggestion
   */
  public cancelPreview(): void {
    if (this.activePreviewId) {
      this.clearPreviewAndRestoreWorkflow();
      this.messageService.info("Preview cancelled");
    }
  }

  /**
   * Saves the current workflow state
   */
  private saveWorkflowState(): void {
    const currentWorkflow = this.workflowActionService.getWorkflow();
    // Create a deep copy of the workflow to ensure we don't have references to the original
    this.workflowBeforePreview = cloneDeep(currentWorkflow);
  }

  /**
   * Clears the preview and restores the original workflow
   */
  private clearPreviewAndRestoreWorkflow(): void {
    // Reset active preview ID
    this.activePreviewId = null;

    // Exit preview mode
    this.isInPreviewMode = false;

    // Remove the document click listener
    this.removeDocumentClickListener();

    // Notify the suggestion service that preview is no longer active
    this.workflowSuggestionService.setPreviewActive(false);

    // Restore the workflow to its state before the preview
    this.restoreWorkflowState();
  }

  /**
   * Restores the workflow to its state before the preview
   */
  private restoreWorkflowState(): void {
    if (!this.workflowBeforePreview) return;

    // Clear the current workflow
    this.workflowActionService.clearWorkflow();

    // Restore operators and links from the saved workflow state
    if (this.workflowBeforePreview.content) {
      const content = this.workflowBeforePreview.content;

      // Create array of operators with positions
      const operatorsAndPositions = content.operators.map(op => {
        const position = content.operatorPositions[op.operatorID];
        return {
          op: op,
          pos: position,
        };
      });

      // Add all operators and links back to the workflow
      this.workflowActionService.addOperatorsAndLinks(operatorsAndPositions, content.links, content.commentBoxes);
    }

    // Reset the saved workflow state
    this.workflowBeforePreview = null;

    // Clear data structures
    this.propertyStyleMaps.clear();
    this.originalOperatorProperties.clear();
  }

  /**
   * Creates a preview of a suggestion in the workflow
   */
  private createPreview(suggestion: any): void {
    const texeraGraph = this.workflowActionService.getTexeraGraph();
    const jointGraph = this.workflowActionService.getJointGraph();

    // Handle operators to delete first
    if (suggestion.operatorsToDelete) {
      suggestion.operatorsToDelete.forEach((operatorId: string) => {
        if (texeraGraph.hasOperator(operatorId)) {
          // Highlight the operator in red before "deleting" it
          const operatorCell = jointGraph.getCell(operatorId);
          if (operatorCell) {
            operatorCell.attr({
              rect: {
                fill: "rgba(255, 200, 200, 0.6)",
                stroke: "rgba(255, 0, 0, 0.6)",
                "stroke-width": 2,
              },
            });
          }
        }
      });
    }

    // Add preview operators
    suggestion.operatorsToAdd.forEach((opToAdd: any) => {
      // Create a new operator predicate
      const operatorPredicate = this.workflowUtilService.getNewOperatorPredicate(opToAdd.operatorType);

      // Set properties if provided
      if (opToAdd.properties) {
        Object.assign(operatorPredicate.operatorProperties, opToAdd.properties);
      }

      // Add operator to graph
      this.workflowActionService.addOperator(operatorPredicate, opToAdd.position);

      // Make the operator semi-transparent to indicate it's a preview
      const operatorCell = jointGraph.getCell(operatorPredicate.operatorID);
      if (operatorCell) {
        operatorCell.attr({
          ".": { opacity: 0.6 },
          rect: { stroke: "#1890ff", "stroke-width": 2 },
        });
      }
    });

    // Add preview links
    suggestion.linksToAdd.forEach((linkToAdd: any) => {
      let sourceOperatorId = linkToAdd.source.operatorId;
      let targetOperatorId = linkToAdd.target.operatorId;

      if (texeraGraph.hasOperator(sourceOperatorId) && texeraGraph.hasOperator(targetOperatorId)) {
        try {
          const link = {
            linkID: `link-preview-${Date.now()}`,
            source: {
              operatorID: sourceOperatorId,
              portID: linkToAdd.source.portId,
            },
            target: {
              operatorID: targetOperatorId,
              portID: linkToAdd.target.portId,
            },
          };

          this.workflowActionService.addLink(link);

          // Make the link semi-transparent
          const linkCell = jointGraph.getCell(link.linkID);
          if (linkCell) {
            linkCell.attr({
              ".connection": { opacity: 0.6, stroke: "#1890ff" },
              ".marker-target": { opacity: 0.6, fill: "#1890ff" },
            });
          }
        } catch (error) {
          console.error("Error adding preview link:", error);
        }
      }
    });
  }
}
