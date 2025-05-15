import { Component, OnInit, HostListener, OnDestroy } from "@angular/core";
import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { WorkflowActionService } from "../../../service/workflow-graph/model/workflow-action.service";
import { WorkflowSuggestionService } from "../../../service/workflow-suggestion/workflow-suggestion.service";
import { WorkflowUtilService } from "../../../service/workflow-graph/util/workflow-util.service";
import { NzMessageService } from "ng-zorro-antd/message";
import { WorkflowPersistService } from "../../../../common/service/workflow-persist/workflow-persist.service";
import { Workflow } from "../../../../common/type/workflow";
import { cloneDeep } from "lodash";
import { ExecuteWorkflowService } from "../../../service/execute-workflow/execute-workflow.service";
import { ExecutionState } from "../../../types/execute-workflow.interface";
import { filter } from "rxjs/operators";
import { WorkflowCompilingService } from "../../../service/compile-workflow/workflow-compiling.service";
import { Subscription, interval } from "rxjs";
import { WorkflowSuggestion, WorkflowSuggestionList } from "../../../types/workflow-suggestion.interface";
import { OperatorPredicate } from "../../../types/workflow-common.interface";

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
  public suggestions: WorkflowSuggestion[] = [];
  public activePreviewId: string | null = null;
  public canModify = true;
  public loadingSuggestions = false;
  public intentionText = "";
  // Store the workflow state before a preview is applied
  private workflowBeforePreview: Workflow | null = null;

  // Store original operator properties to compare with changed properties
  private originalOperatorProperties: Map<string, object> = new Map();

  // Store property style maps for highlighting changed properties
  private propertyStyleMaps: Map<string, Map<String, String>> = new Map();

  // Track if we're in preview mode to prevent tab changes
  public isInPreviewMode = false;
  // Track the tab focus interval
  private tabFocusInterval: Subscription | null = null;
  // Store click listeners to prevent unnecessary event binding
  private boundHandleDocumentClick: any;

  constructor(
    private workflowActionService: WorkflowActionService,
    private workflowUtilService: WorkflowUtilService,
    private workflowCompilingService: WorkflowCompilingService,
    private workflowExecuteService: ExecuteWorkflowService,
    private messageService: NzMessageService,
    private workflowPersistService: WorkflowPersistService,
    private workflowSuggestionService: WorkflowSuggestionService,
    private executeWorkflowService: ExecuteWorkflowService
  ) {
    this.boundHandleDocumentClick = this.handleDocumentClick.bind(this);
  }

  ngOnInit(): void {
    // Get initial permission state
    this.workflowActionService
      .getWorkflowModificationEnabledStream()
      .pipe(untilDestroyed(this))
      .subscribe(canModify => (this.canModify = canModify));
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

  public refreshSuggestions(): void {
    if (this.isInPreviewMode) return;
    const operators = this.workflowActionService.getTexeraGraph().getAllOperators();
    if (operators.length === 0) return;

    this.loadingSuggestions = true;
    const focusedOperatorIDs = this.workflowActionService.getJointGraphWrapper().getCurrentHighlightedOperatorIDs();
    const intention = this.intentionText.trim();

    this.workflowSuggestionService
      .getSuggestions(
        this.workflowActionService.getWorkflow(),
        this.workflowCompilingService.getWorkflowCompilationStateInfo(),
        this.workflowExecuteService.getExecutionState(),
        this.intentionText.trim(),
        this.workflowActionService.getJointGraphWrapper().getCurrentHighlightedOperatorIDs()
      )
      .pipe(untilDestroyed(this))
      .subscribe((suggestionList: WorkflowSuggestionList) => {
        console.log("Received suggestions:", suggestionList);
        this.suggestions = suggestionList.suggestions;
        this.loadingSuggestions = false;
      });
  }

  public togglePreview(suggestion: WorkflowSuggestion): void {
    if (this.activePreviewId) {
      this.clearPreviewAndRestoreWorkflow();
    }

    if (this.activePreviewId === suggestion.suggestionID) {
      this.activePreviewId = null;
      this.isInPreviewMode = false;
      this.workflowSuggestionService.setPreviewActive(false);
      this.removeDocumentClickListener();
      if (this.tabFocusInterval) {
        this.tabFocusInterval.unsubscribe();
        this.tabFocusInterval = null;
      }
    } else {
      this.saveWorkflowState();
      this.propertyStyleMaps.clear();
      this.originalOperatorProperties.clear();
      this.isInPreviewMode = true;
      this.addDocumentClickListener();
      this.workflowSuggestionService.setPreviewActive(true);
      this.activePreviewId = suggestion.suggestionID;
      this.createPreview(suggestion);
      this.focusSuggestionTab();
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
   * Removes a suggestion from the list
   */
  public dislikeSuggestion(suggestion: WorkflowSuggestion): void {
    // If this is the active suggestion, restore the workflow first
    if (this.activePreviewId === suggestion.suggestionID) {
      this.clearPreviewAndRestoreWorkflow();
    }

    // Remove the suggestion from the list
    this.suggestions = this.suggestions.filter(s => s.suggestionID !== suggestion.suggestionID);

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
   * Internal helper to apply workflow changes described by a suggestion.
   * Used by both applySuggestion and createPreview.
   */
  private applyWorkflowSuggestion(suggestion: WorkflowSuggestion, options: { preview: boolean }): Map<string, string> {
    const texeraGraph = this.workflowActionService.getTexeraGraph();
    const jointGraph = this.workflowActionService.getJointGraph();
    const jointGraphWrapper = this.workflowActionService.getJointGraphWrapper();
    const operatorIDMap = new Map<string, string>();
    const operatorsAndPositions: { op: OperatorPredicate; pos: { x: number; y: number } }[] = [];

    suggestion.changes.operatorsToAdd.forEach((op, index) => {
      const isExisting = texeraGraph.hasOperator(op.operatorID);

      if (isExisting) {
        if (options.preview) {
          const cell = jointGraph.getCell(op.operatorID);
          if (cell) {
            cell.attr({
              rect: {
                fill: "rgba(255, 200, 200, 0.6)",
                stroke: "rgba(255, 0, 0, 0.6)",
                "stroke-width": 2,
              },
            });
          }
        } else {
          this.workflowActionService.setOperatorProperty(op.operatorID, {
            ...op.operatorProperties,
          });
        }
        return;
      }

      const newOp = this.workflowUtilService.getNewOperatorPredicate(op.operatorType);
      Object.assign(newOp.operatorProperties, op.operatorProperties);
      operatorIDMap.set(op.operatorID, newOp.operatorID);

      let pos = { x: 100, y: 100 + index * 100 };
      const anchorLink = suggestion.changes.linksToAdd.find(l => l.target.operatorID === op.operatorID);
      if (anchorLink) {
        const anchorID = texeraGraph.hasOperator(anchorLink.source.operatorID)
          ? anchorLink.source.operatorID
          : operatorIDMap.get(anchorLink.source.operatorID);
        if (anchorID && texeraGraph.hasOperator(anchorID)) {
          const anchorPos = jointGraphWrapper.getElementPosition(anchorID);
          pos = { x: anchorPos.x + 200, y: anchorPos.y };
        }
      }

      operatorsAndPositions.push({ op: newOp, pos });

      if (options.preview) {
        this.workflowActionService.addOperator(newOp, pos);
        const cell = jointGraph.getCell(newOp.operatorID);
        if (cell) {
          cell.attr({
            ".": { opacity: 0.6 },
            rect: { stroke: "#1890ff", "stroke-width": 2 },
          });
        }
      }
    });

    if (!options.preview) {
      if (suggestion.changes.operatorsToDelete.length > 0) {
        this.workflowActionService.deleteOperatorsAndLinks(suggestion.changes.operatorsToDelete);
      }
      this.workflowActionService.addOperatorsAndLinks(operatorsAndPositions);
    }

    suggestion.changes.linksToAdd.forEach(link => {
      const sourceID = operatorIDMap.get(link.source.operatorID) ?? link.source.operatorID;
      const targetID = operatorIDMap.get(link.target.operatorID) ?? link.target.operatorID;

      if (texeraGraph.hasOperator(sourceID) && texeraGraph.hasOperator(targetID)) {
        const linkObject = {
          linkID: options.preview ? `link-preview-${Date.now()}` : `link-${Date.now()}`,
          source: { operatorID: sourceID, portID: link.source.portID },
          target: { operatorID: targetID, portID: link.target.portID },
        };
        this.workflowActionService.addLink(linkObject);

        if (options.preview) {
          const cell = jointGraph.getCell(linkObject.linkID);
          if (cell) {
            cell.attr({
              ".connection": { opacity: 0.6, stroke: "#1890ff" },
              ".marker-target": { opacity: 0.6, fill: "#1890ff" },
            });
          }
        }
      }
    });

    return operatorIDMap;
  }

  public applySuggestion(suggestion: WorkflowSuggestion): void {
    this.clearPreviewAndRestoreWorkflow();
    try {
      this.applyWorkflowSuggestion(suggestion, { preview: false });
      const workflow = this.workflowActionService.getWorkflow();
      this.workflowPersistService
        .persistWorkflow(workflow)
        .pipe(untilDestroyed(this))
        .subscribe(() => {
          this.messageService.success("Successfully applied and saved the suggestion!");
          this.suggestions = this.suggestions.filter(s => s.suggestionID !== suggestion.suggestionID);
        });
    } catch (error) {
      console.error("Error applying suggestion:", error);
      this.messageService.error("Failed to apply the suggestion.");
    }
  }

  private createPreview(suggestion: WorkflowSuggestion): void {
    this.applyWorkflowSuggestion(suggestion, { preview: true });
  }
}
