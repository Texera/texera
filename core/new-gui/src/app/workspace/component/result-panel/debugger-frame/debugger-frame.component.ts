import {
  Component,
  Input,
  OnChanges,
  OnInit,
  SimpleChanges
} from "@angular/core";
import { ExecuteWorkflowService } from "../../../service/execute-workflow/execute-workflow.service";
import { BreakpointTriggerInfo } from "../../../types/workflow-common.interface";
import { WorkflowWebsocketService } from "../../../service/workflow-websocket/workflow-websocket.service";
import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { TypedValue } from "../../../types/workflow-websocket.interface";
import { FlatTreeControl, TreeControl } from "@angular/cdk/tree";
import {
  CollectionViewer,
  DataSource,
  SelectionChange
} from "@angular/cdk/collections";
import { BehaviorSubject, merge, Observable } from "rxjs";
import { map, tap } from "rxjs/operators";

interface FlatTreeNode {
  expandable: boolean;
  expression: string;
  name: string;
  type: string;
  value: string;
  level: number;
  loading?: boolean;
}

@UntilDestroy()
@Component({
  selector: "texera-debugger-frame",
  templateUrl: "./debugger-frame.component.html",
  styleUrls: ["./debugger-frame.component.scss"]
})
export class DebuggerFrameComponent implements OnInit, OnChanges {
  @Input() operatorId?: string;
  // display breakpoint
  breakpointTriggerInfo?: BreakpointTriggerInfo;
  breakpointAction: boolean = false;

  expressionTreeControl = new FlatTreeControl<FlatTreeNode>(
    (node) => node.level,
    (node) => node.expandable
  );
  pythonExpressionSource?: PythonExpressionSource;

  constructor(
    private executeWorkflowService: ExecuteWorkflowService,
    private workflowWebsocketService: WorkflowWebsocketService
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    this.operatorId = changes.operatorId?.currentValue;
    this.renderConsole();
  }

  renderConsole() {
    // try to fetch if we have breakpoint info
    this.breakpointTriggerInfo =
      this.executeWorkflowService.getBreakpointTriggerInfo();
    if (this.breakpointTriggerInfo) {
      this.breakpointAction = true;
    }
  }

  onClickSkipTuples(): void {
    this.executeWorkflowService.skipTuples();
    this.breakpointAction = false;
  }

  onClickRetry() {
    this.executeWorkflowService.retryExecution();
    this.breakpointAction = false;
  }

  onClickEvaluate() {
    if (this.operatorId) {
      this.workflowWebsocketService.send("PythonExpressionEvaluateRequest", {
        expression: "self",
        operatorId: this.operatorId
      });
    }
  }

  ngOnInit(): void {
    this.pythonExpressionSource = new PythonExpressionSource(
      this.expressionTreeControl,
      this.workflowWebsocketService,
      this.operatorId
    );
  }
}

@UntilDestroy()
class PythonExpressionSource implements DataSource<FlatTreeNode> {
  private readonly flattenedDataSubject: BehaviorSubject<FlatTreeNode[]>;
  private childrenLoadedSet = new Set<FlatTreeNode>();

  constructor(
    private treeControl: TreeControl<FlatTreeNode>,
    private workflowWebsocketService: WorkflowWebsocketService,
    private operatorId?: string
  ) {
    this.flattenedDataSubject = new BehaviorSubject<FlatTreeNode[]>([]);
    treeControl.dataNodes = [];

    this.registerEvaluatedValuesHandler();
  }

  toFlatTreeNode(value: TypedValue, parentNode?: FlatTreeNode): FlatTreeNode {
    return <FlatTreeNode>{
      expression:
        (parentNode?.expression ? parentNode?.expression + "." : "") +
        value.expression,
      name: value.expression.replace(/__getitem__\((.*?)\)/, "[$1]"),
      type: value.valueType,
      value: value.valueStr,
      expandable: value.expandable,
      level: (parentNode?.level ?? -1) + 1
    };
  }

  connect(collectionViewer: CollectionViewer): Observable<FlatTreeNode[]> {
    const changes = [
      collectionViewer.viewChange,
      this.treeControl.expansionModel.changed.pipe(
        tap((change) => this.handleExpansionChange(change))
      ),
      this.flattenedDataSubject
    ];
    return merge(...changes).pipe(
      map(() => this.expandFlattenedNodes(this.flattenedDataSubject.getValue()))
    );
  }

  expandFlattenedNodes(nodes: FlatTreeNode[]): FlatTreeNode[] {
    const treeControl = this.treeControl;
    const results: FlatTreeNode[] = [];
    const currentExpand: boolean[] = [];
    currentExpand[0] = true;

    nodes.forEach((node) => {
      let expand = true;
      for (let i = 0; i <= treeControl.getLevel(node); i++) {
        expand = expand && currentExpand[i];
      }
      if (expand) {
        results.push(node);
      }
      if (treeControl.isExpandable(node)) {
        currentExpand[treeControl.getLevel(node) + 1] =
          treeControl.isExpanded(node);
      }
    });
    return results;
  }

  handleExpansionChange(change: SelectionChange<FlatTreeNode>): void {
    if (change.added) {
      change.added.forEach((node) => this.loadChildren(node));
    }
  }

  loadChildren(node: FlatTreeNode): void {
    if (this.childrenLoadedSet.has(node)) {
      return;
    }
    node.loading = true;
    this.getChildren(node);
  }

  disconnect(): void {
    this.flattenedDataSubject.complete();
  }

  getChildren(node: FlatTreeNode): void {
    if (this.operatorId) {
      this.workflowWebsocketService.send("PythonExpressionEvaluateRequest", {
        expression: node.expression,
        operatorId: this.operatorId
      });
    }
  }

  registerEvaluatedValuesHandler() {
    this.workflowWebsocketService
      .subscribeToEvent("PythonExpressionEvaluateResponse")
      .pipe(untilDestroyed(this))
      .subscribe((response) => {
        const flattenedData = this.flattenedDataSubject.getValue();
        const parentNode = flattenedData.find(
          (node) => node.expression === response.expression
        );

        if (parentNode) {
          // found parent node, add to it as children
          response.values.forEach((evaluatedValue) => {
            const treeNodes = evaluatedValue.attributes.map((typedValue) =>
              this.toFlatTreeNode(typedValue, parentNode)
            );
            const index = flattenedData.indexOf(parentNode);
            if (index !== -1) {
              flattenedData.splice(index + 1, 0, ...treeNodes);
              this.childrenLoadedSet.add(parentNode);
            }
            parentNode.loading = false;
          });
        } else {
          // append new expressions as new tree roots
          const newRootNodes = response.values.map((evaluatedValue) =>
            this.toFlatTreeNode(evaluatedValue.value)
          );
          flattenedData.push(...newRootNodes);
        }
        this.flattenedDataSubject.next(flattenedData);
      });
  }
}

