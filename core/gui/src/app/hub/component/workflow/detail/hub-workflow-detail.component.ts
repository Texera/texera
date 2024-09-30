import { AfterViewInit, Component, OnInit, HostListener, OnDestroy, ViewChild, ViewContainerRef } from "@angular/core";
import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { ActivatedRoute, Router } from "@angular/router";
import { environment } from "../../../../../environments/environment";
import { UserService } from "../../../../common/service/user/user.service";
import { UndoRedoService } from "../../../../workspace/service/undo-redo/undo-redo.service";
import { WorkflowPersistService } from "../../../../common/service/workflow-persist/workflow-persist.service";
import { WorkflowWebsocketService } from "../../../../workspace/service/workflow-websocket/workflow-websocket.service";
import { WorkflowActionService } from "../../../../workspace/service/workflow-graph/model/workflow-action.service";
import { OperatorMetadataService } from "../../../../workspace/service/operator-metadata/operator-metadata.service";
import { NzMessageService } from "ng-zorro-antd/message";
import { NotificationService } from "../../../../common/service/notification/notification.service";
import { CodeEditorService } from "../../../../workspace/service/code-editor/code-editor.service";
import { distinctUntilChanged, filter, switchMap } from "rxjs/operators";
import { Workflow } from "../../../../common/type/workflow";
import { of } from "rxjs";
import { isDefined } from "../../../../common/util/predicate";
import { HubWorkflowService } from "../../../service/workflow/hub-workflow.service";
import { User } from "src/app/common/type/user";

@UntilDestroy()
@Component({
  selector: "texera-hub-workflow-result",
  templateUrl: "hub-workflow-detail.component.html",
  styleUrls: ["hub-workflow-detail.component.scss"],
})
export class HubWorkflowDetailComponent implements AfterViewInit, OnDestroy {
  wid: number;
  workflowName: string = "";
  ownerUser!: User;
  workflow = {
    steps: [
      {
        name: "Step 1: Data Collection",
        description: "Collect necessary data from various sources.",
        status: "Completed",
      },
      {
        name: "Step 2: Data Analysis",
        description: "Analyze the collected data for insights.",
        status: "In Progress",
      },
      {
        name: "Step 3: Report Generation",
        description: "Generate reports based on the analysis.",
        status: "Not Started",
      },
      {
        name: "Step 4: Presentation",
        description: "Present the findings to stakeholders.",
        status: "Not Started",
      },
    ],
  };

  public pid?: number = undefined;
  userSystemEnabled = environment.userSystemEnabled;
  @ViewChild("codeEditor", { read: ViewContainerRef }) codeEditorViewRef!: ViewContainerRef;
  constructor(
    private userService: UserService,
    // list additional services in constructor so they are initialized even if no one use them directly
    private undoRedoService: UndoRedoService,
    private workflowPersistService: WorkflowPersistService,
    private workflowWebsocketService: WorkflowWebsocketService,
    private workflowActionService: WorkflowActionService,
    private route: ActivatedRoute,
    private operatorMetadataService: OperatorMetadataService,
    private message: NzMessageService,
    private router: Router,
    private notificationService: NotificationService,
    private codeEditorService: CodeEditorService,
    private hubWorkflowService: HubWorkflowService
  ) {
    this.wid = this.route.snapshot.params.id;
  }

  ngOnInit() {
    this.hubWorkflowService
      .getOwnerUser(this.wid)
      .pipe(untilDestroyed(this))
      .subscribe(owner => {
        this.ownerUser = owner;
      });
    this.hubWorkflowService
      .getWorkflowName(this.wid)
      .pipe(untilDestroyed(this))
      .subscribe(workflowName => {
        this.workflowName = workflowName;
      });
  }

  ngAfterViewInit(): void {
    // clear the current workspace, reset as `WorkflowActionService.DEFAULT_WORKFLOW`
    this.workflowActionService.resetAsNewWorkflow();

    if (this.userSystemEnabled) {
      this.registerReEstablishWebsocketUponWIdChange();
    }

    this.registerLoadOperatorMetadata();

    this.codeEditorService.vc = this.codeEditorViewRef;
  }

  @HostListener("window:beforeunload")
  ngOnDestroy() {
    if (this.workflowPersistService.isWorkflowPersistEnabled()) {
      const workflow = this.workflowActionService.getWorkflow();
      this.workflowPersistService.persistWorkflow(workflow).pipe(untilDestroyed(this)).subscribe();
    }

    this.codeEditorViewRef.clear();
    this.workflowWebsocketService.closeWebsocket();
    this.workflowActionService.clearWorkflow();
  }

  loadWorkflowWithId(wid: number): void {
    // disable the workspace until the workflow is fetched from the backend
    this.workflowActionService.disableWorkflowModification();
    this.hubWorkflowService
      .retrievePublicWorkflow(wid)
      .pipe(untilDestroyed(this))
      .subscribe(
        (workflow: Workflow) => {
          this.workflowActionService.setNewSharedModel(wid, this.userService.getCurrentUser());
          // remember URL fragment
          const fragment = this.route.snapshot.fragment;
          // load the fetched workflow
          this.workflowActionService.reloadWorkflow(workflow);
          this.workflowActionService.enableWorkflowModification();
          // set the URL fragment to previous value
          // because reloadWorkflow will highlight/unhighlight all elements
          // which will change the URL fragment
          this.router.navigate([], {
            relativeTo: this.route,
            fragment: fragment !== null ? fragment : undefined,
            preserveFragment: false,
          });
          // highlight the operator, comment box, or link in the URL fragment
          if (fragment) {
            if (this.workflowActionService.getTexeraGraph().hasElementWithID(fragment)) {
              this.workflowActionService.highlightElements(false, fragment);
            } else {
              this.notificationService.error(`Element ${fragment} doesn't exist`);
              // remove the fragment from the URL
              this.router.navigate([], { relativeTo: this.route });
            }
          }
          // clear stack
          this.undoRedoService.clearUndoStack();
          this.undoRedoService.clearRedoStack();
        },
        () => {
          this.workflowActionService.resetAsNewWorkflow();
          // enable workspace for modification
          this.workflowActionService.enableWorkflowModification();
          // clear stack
          this.undoRedoService.clearUndoStack();
          this.undoRedoService.clearRedoStack();
          this.message.error("You don't have access to this workflow, please log in with an appropriate account");
        }
      );
  }

  registerLoadOperatorMetadata() {
    this.operatorMetadataService
      .getOperatorMetadata()
      .pipe(untilDestroyed(this))
      .subscribe(() => {
        // load workflow with wid if presented in the URL
        if (this.wid) {
          // if wid is present in the url, load it from the backend
          this.userService
            .userChanged()
            .pipe(untilDestroyed(this))
            .subscribe(() => {
              this.loadWorkflowWithId(this.wid);
            });
        } else {
          // no workflow to load, pending to create a new workflow
        }
      });
  }

  registerReEstablishWebsocketUponWIdChange() {
    this.workflowActionService
      .workflowMetaDataChanged()
      .pipe(
        switchMap(() => of(this.workflowActionService.getWorkflowMetadata().wid)),
        filter(isDefined),
        distinctUntilChanged()
      )
      .pipe(untilDestroyed(this))
      .subscribe(wid => {
        this.workflowWebsocketService.reopenWebsocket(wid);
      });
  }
}
