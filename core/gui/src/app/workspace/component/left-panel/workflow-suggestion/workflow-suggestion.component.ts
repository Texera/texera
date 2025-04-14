import { Component, OnInit, HostListener } from "@angular/core";
import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { WorkflowActionService } from "../../../service/workflow-graph/model/workflow-action.service";
import { WorkflowUtilService } from "../../../service/workflow-graph/util/workflow-util.service";
import { JointUIService } from "../../../service/joint-ui/joint-ui.service";
import { NzMessageService } from "ng-zorro-antd/message";
import { WorkflowPersistService } from "../../../../common/service/workflow-persist/workflow-persist.service";
import { Workflow, WorkflowContent } from "../../../../common/type/workflow";
import { cloneDeep, isEqual } from "lodash";
import { DynamicSchemaService } from "../../../service/dynamic-schema/dynamic-schema.service";

interface WorkflowSuggestion {
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

@UntilDestroy()
@Component({
  selector: "texera-workflow-suggestion",
  templateUrl: "workflow-suggestion.component.html",
  styleUrls: ["workflow-suggestion.component.scss"],
})
export class WorkflowSuggestionComponent implements OnInit {
  public suggestions: WorkflowSuggestion[] = [];
  public activePreviewId: string | null = null;
  public canModify = true;

  // Store the workflow state before a preview is applied
  private workflowBeforePreview: Workflow | null = null;

  // Store original operator properties to compare with changed properties
  private originalOperatorProperties: Map<string, object> = new Map();

  // Store property style maps for highlighting changed properties
  private propertyStyleMaps: Map<string, Map<String, String>> = new Map();

  // Flag to prevent clicks inside the component from triggering the document click handler
  private clickedInside = false;

  constructor(
    private workflowActionService: WorkflowActionService,
    private workflowUtilService: WorkflowUtilService,
    private jointUIService: JointUIService,
    private messageService: NzMessageService,
    private workflowPersistService: WorkflowPersistService,
    private dynamicSchemaService: DynamicSchemaService
  ) {}

  ngOnInit(): void {
    this.workflowActionService
      .getWorkflowModificationEnabledStream()
      .pipe(untilDestroyed(this))
      .subscribe(canModify => (this.canModify = canModify));

    // Listen to operator highlight events to apply property style maps
    this.workflowActionService
      .getJointGraphWrapper()
      .getJointOperatorHighlightStream()
      .pipe(untilDestroyed(this))
      .subscribe(operatorIDArray => {
        // Handle operatorID which comes as a readonly string array
        // Extract the first operator ID from the array
        const operatorID = operatorIDArray.length > 0 ? operatorIDArray[0] : "";

        if (this.propertyStyleMaps.has(operatorID)) {
          // Wait for the property editor to appear
          setTimeout(() => {
            // Apply property style maps to highlight changed properties
            const operatorHighlightedEvent = new CustomEvent("operator-highlighted", {
              detail: {
                operatorID: operatorID,
                propertyStyleMap: this.propertyStyleMaps.get(operatorID),
              },
            });
            document.dispatchEvent(operatorHighlightedEvent);

            // Add event listener for the property editor component
            this.setupPropertyEditorListener();
          }, 100);
        }
      });

    // Load mock suggestions when component initializes
    this.loadMockSuggestions();
  }

  @HostListener("click")
  onClickInside() {
    this.clickedInside = true;
  }

  @HostListener("document:click")
  onClickOutside() {
    // Reset the flag for the next click
    this.clickedInside = false;
    // Remove this functionality - previews should only be cleared via the Cancel button
    // No longer clearing preview when clicking outside
  }

  private setupPropertyEditorListener(): void {
    // Look for elements with specific CSS class that the property editor uses
    const formlyFields = document.querySelectorAll(".formly-field");

    // Try to find and target the property editor
    const propertyEditor = document.querySelector(".property-editor-content");
    if (propertyEditor) {
      // Find all the formly fields and apply styles
      formlyFields.forEach(field => {
        const key = field.getAttribute("data-field-key");
        // Apply highlighting for changed properties
        const activeOperatorID = this.workflowActionService
          .getJointGraphWrapper()
          .getCurrentHighlightedOperatorIDs()[0];

        if (activeOperatorID && this.propertyStyleMaps.has(activeOperatorID)) {
          const styleMap = this.propertyStyleMaps.get(activeOperatorID)!;
          if (key && styleMap.has(key)) {
            const highlightStyle = styleMap.get(key)!;
            // Apply the highlighting style to this field
            const fieldElement = field.querySelector(".formly-field-content");
            if (fieldElement) {
              fieldElement.setAttribute("style", highlightStyle.toString());
            }
          }
        }
      });
    }
  }

  private loadMockSuggestions(): void {
    this.suggestions = [
      {
        id: "suggestion1",
        description: "Add a KeywordSearch operator with sentiment analysis",
        operatorsToAdd: [
          {
            operatorType: "KeywordSearch",
            position: { x: 400, y: 300 },
            properties: {
              keyword: "climate change",
              attributes: ["content", "title"],
            },
          },
          {
            operatorType: "SentimentAnalysis",
            position: { x: 600, y: 300 },
            properties: {
              attribute: "content",
              resultAttribute: "sentiment",
            },
          },
        ],
        operatorPropertiesToChange: [
          {
            operatorId: "View-Results-1",
            properties: {
              limit: 20,
              offset: 0,
            },
          },
        ],
        operatorsToDelete: [],
        linksToAdd: [
          {
            source: { operatorId: "Source-Scan-1", portId: "output-0" },
            target: { operatorId: "KeywordSearch-1", portId: "input-0" },
          },
          {
            source: { operatorId: "KeywordSearch-1", portId: "output-0" },
            target: { operatorId: "SentimentAnalysis-1", portId: "input-0" },
          },
          {
            source: { operatorId: "SentimentAnalysis-1", portId: "output-0" },
            target: { operatorId: "View-Results-1", portId: "input-0" },
          },
        ],
        isPreviewActive: false,
      },
      {
        id: "suggestion2",
        description: "Replace ScanSource with CSVFileScan for better performance",
        operatorsToAdd: [
          {
            operatorType: "CSVFileScan",
            position: { x: 200, y: 200 },
            properties: {
              fileName: "data.csv",
              limit: -1,
              offset: 0,
              schema: "auto",
            },
          },
        ],
        operatorPropertiesToChange: [],
        operatorsToDelete: ["Source-Scan-1"],
        linksToAdd: [
          {
            source: { operatorId: "CSVFileScan-1", portId: "output-0" },
            target: { operatorId: "View-Results-1", portId: "input-0" },
          },
        ],
        isPreviewActive: false,
      },
      {
        id: "suggestion3",
        description: "Enhance workflow with projection and sorting",
        operatorsToAdd: [
          {
            operatorType: "Projection",
            position: { x: 400, y: 200 },
            properties: {
              attributes: ["id", "name", "price", "category"],
            },
          },
          {
            operatorType: "Sort",
            position: { x: 600, y: 200 },
            properties: {
              sortAttributesList: [
                {
                  attributeName: "price",
                  order: "desc",
                },
              ],
            },
          },
        ],
        operatorPropertiesToChange: [
          {
            operatorId: "Source-Scan-1",
            properties: {
              tableName: "products",
              limit: 1000,
            },
          },
        ],
        operatorsToDelete: [],
        linksToAdd: [
          {
            source: { operatorId: "Source-Scan-1", portId: "output-0" },
            target: { operatorId: "Projection-1", portId: "input-0" },
          },
          {
            source: { operatorId: "Projection-1", portId: "output-0" },
            target: { operatorId: "Sort-1", portId: "input-0" },
          },
          {
            source: { operatorId: "Sort-1", portId: "output-0" },
            target: { operatorId: "View-Results-1", portId: "input-0" },
          },
        ],
        isPreviewActive: false,
      },
    ];
  }

  public cancelPreview(): void {
    if (this.activePreviewId) {
      this.clearPreviewAndRestoreWorkflow();
      this.messageService.info("Preview cancelled");
    }
  }

  public togglePreview(suggestion: WorkflowSuggestion): void {
    // If there's an active preview, clear it and restore the previous workflow
    if (this.activePreviewId) {
      this.clearPreviewAndRestoreWorkflow();
    }

    if (this.activePreviewId === suggestion.id) {
      // Deactivate preview if clicking the same suggestion (already handled by clearing above)
      this.activePreviewId = null;
    } else {
      // Save the current workflow state before creating the preview
      this.saveWorkflowState();

      // Clear property style maps and original properties
      this.propertyStyleMaps.clear();
      this.originalOperatorProperties.clear();

      // Activate preview for this suggestion
      this.activePreviewId = suggestion.id;
      this.createPreview(suggestion);
    }
  }

  private saveWorkflowState(): void {
    const currentWorkflow = this.workflowActionService.getWorkflow();
    // Create a deep copy of the workflow to ensure we don't have references to the original
    this.workflowBeforePreview = cloneDeep(currentWorkflow);
  }

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
   * Calculates which properties have changed between two objects
   * @param original The original object
   * @param modified The modified object
   * @returns Array of property names that have changed
   */
  private getChangedPropertyNames(original: object, modified: object): string[] {
    const changedProperties: string[] = [];

    // Find all properties that exist in either original or modified
    const allKeys = new Set([...Object.keys(original), ...Object.keys(modified)]);

    // Compare each property
    allKeys.forEach(key => {
      const originalValue = (original as any)[key];
      const modifiedValue = (modified as any)[key];

      // Check if the property value is different
      if (!isEqual(originalValue, modifiedValue)) {
        changedProperties.push(key);
      }
    });

    return changedProperties;
  }

  private createPreview(suggestion: WorkflowSuggestion): void {
    const texeraGraph = this.workflowActionService.getTexeraGraph();
    const jointGraph = this.workflowActionService.getJointGraph();

    // Handle operators to delete first
    suggestion.operatorsToDelete.forEach(operatorId => {
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

    // Add preview operators
    suggestion.operatorsToAdd.forEach(opToAdd => {
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

        // Handle operator click to highlight it
        operatorCell.on("cell:pointerclick", () => {
          // Get the operator ID as a simple string
          const opId: string = operatorPredicate.operatorID;
          // Now highlight it - use spread syntax to convert single string to varargs
          this.workflowActionService.getJointGraphWrapper().highlightOperators(opId);
        });
      }
    });

    // Change operator properties for existing operators
    suggestion.operatorPropertiesToChange.forEach(propChange => {
      const operator = texeraGraph.getOperator(propChange.operatorId);
      if (operator) {
        // Store original properties for comparison
        this.originalOperatorProperties.set(propChange.operatorId, cloneDeep(operator.operatorProperties));

        // Apply property changes (these will be undone by restoring the workflow state)
        const newProperties = {
          ...operator.operatorProperties,
          ...propChange.properties,
        };

        this.workflowActionService.setOperatorProperty(propChange.operatorId, newProperties);

        // Calculate which properties have changed
        const changedPropertyNames = this.getChangedPropertyNames(
          this.originalOperatorProperties.get(propChange.operatorId)!,
          newProperties
        );

        // Create a map of property styles for highlighting changed properties
        const propertyStyleMap = new Map<String, String>();
        changedPropertyNames.forEach(propName => {
          propertyStyleMap.set(
            propName,
            "background-color: rgba(82, 196, 26, 0.2); border: 1px solid #52c41a; border-radius: 4px;"
          );
        });

        // Store the property style map for this operator
        this.propertyStyleMaps.set(propChange.operatorId, propertyStyleMap);

        // Make the operator light green to indicate property changes
        const operatorCell = jointGraph.getCell(propChange.operatorId);
        if (operatorCell) {
          operatorCell.attr({
            rect: {
              fill: "rgba(82, 196, 26, 0.2)",
              stroke: "#52c41a",
              "stroke-width": 2,
            },
          });

          // Handle operator click to highlight it
          operatorCell.on("cell:pointerclick", () => {
            // Get the operator ID as a simple string
            const opId: string = propChange.operatorId;
            // Now highlight it - use spread syntax to convert single string to varargs
            this.workflowActionService.getJointGraphWrapper().highlightOperators(opId);
          });
        }
      }
    });

    // Add preview links
    suggestion.linksToAdd.forEach(linkToAdd => {
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

  private clearPreviewAndRestoreWorkflow(): void {
    // Reset active preview ID
    this.activePreviewId = null;

    // Restore the workflow to its state before the preview
    this.restoreWorkflowState();
  }

  public applySuggestion(suggestion: WorkflowSuggestion): void {
    // First clear any previews and restore the original workflow
    this.clearPreviewAndRestoreWorkflow();

    // Then apply the changes for real
    try {
      // Delete operators first
      if (suggestion.operatorsToDelete.length > 0) {
        this.workflowActionService.deleteOperatorsAndLinks(suggestion.operatorsToDelete);
      }

      // Add operators
      const operatorsToAdd = suggestion.operatorsToAdd.map(opToAdd => {
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
      suggestion.linksToAdd.forEach(linkToAdd => {
        let sourceOperatorId = linkToAdd.source.operatorId;
        let targetOperatorId = linkToAdd.target.operatorId;

        // Map operator IDs if needed
        if (!this.workflowActionService.getTexeraGraph().hasOperator(sourceOperatorId)) {
          const newOperator = operatorsToAdd.find(op => op.op.operatorType === sourceOperatorId.split("-")[0]);
          if (newOperator) sourceOperatorId = newOperator.op.operatorID;
        }

        if (!this.workflowActionService.getTexeraGraph().hasOperator(targetOperatorId)) {
          const newOperator = operatorsToAdd.find(op => op.op.operatorType === targetOperatorId.split("-")[0]);
          if (newOperator) targetOperatorId = newOperator.op.operatorID;
        }

        if (
          this.workflowActionService.getTexeraGraph().hasOperator(sourceOperatorId) &&
          this.workflowActionService.getTexeraGraph().hasOperator(targetOperatorId)
        ) {
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
      suggestion.operatorPropertiesToChange.forEach(propChange => {
        if (this.workflowActionService.getTexeraGraph().hasOperator(propChange.operatorId)) {
          const operator = this.workflowActionService.getTexeraGraph().getOperator(propChange.operatorId);
          this.workflowActionService.setOperatorProperty(propChange.operatorId, {
            ...operator.operatorProperties,
            ...propChange.properties,
          });
        }
      });

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

  public dislikeSuggestion(suggestion: WorkflowSuggestion): void {
    // If this is the active suggestion, restore the workflow first
    if (this.activePreviewId === suggestion.id) {
      this.clearPreviewAndRestoreWorkflow();
    }

    // Remove the suggestion from the list
    this.suggestions = this.suggestions.filter(s => s.id !== suggestion.id);

    // Show a message to confirm the action
    this.messageService.info("Suggestion removed from the list.");
  }
}
