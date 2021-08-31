import { Location } from '@angular/common';
import { AfterViewInit, Component, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Version } from '../../../environments/version';
import { UserService } from '../../common/service/user/user.service';
import { WorkflowPersistService } from '../../common/service/workflow-persist/workflow-persist.service';
import { Workflow } from '../../common/type/workflow';
import { SchemaPropagationService } from '../service/dynamic-schema/schema-propagation/schema-propagation.service';
import { SourceTablesService } from '../service/dynamic-schema/source-tables/source-tables.service';
import { OperatorMetadataService } from '../service/operator-metadata/operator-metadata.service';
import { ResultPanelToggleService } from '../service/result-panel-toggle/result-panel-toggle.service';
import { UndoRedoService } from '../service/undo-redo/undo-redo.service';
import { WorkflowCacheService } from '../service/workflow-cache/workflow-cache.service';
import { WorkflowActionService } from '../service/workflow-graph/model/workflow-action.service';
import { WorkflowWebsocketService } from '../service/workflow-websocket/workflow-websocket.service';
import { NzMessageService } from 'ng-zorro-antd/message';
import { WorkflowConsoleService } from '../service/workflow-console/workflow-console.service';
import { debounceTime, filter } from 'rxjs/operators';

@Component({
  selector: 'texera-workspace',
  templateUrl: './workspace.component.html',
  styleUrls: ['./workspace.component.scss'],
  providers: [
    // uncomment this line for manual testing without opening backend server
    // { provide: OperatorMetadataService, useClass: StubOperatorMetadataService },
  ]
})
export class WorkspaceComponent implements OnDestroy, AfterViewInit {

  public gitCommitHash: string = Version.raw;
  public showResultPanel: boolean = false;
  public userSystemEnabled: boolean = environment.userSystemEnabled;

  private subscriptions: Subscription = new Subscription();

  constructor(
    private resultPanelToggleService: ResultPanelToggleService,
    // list additional services in constructor so they are initialized even if no one use them directly
    private sourceTablesService: SourceTablesService,
    private schemaPropagationService: SchemaPropagationService,
    private undoRedoService: UndoRedoService,
    private userService: UserService,
    private workflowCacheService: WorkflowCacheService,
    private workflowPersistService: WorkflowPersistService,
    private workflowWebsocketService: WorkflowWebsocketService,
    private workflowActionService: WorkflowActionService,
    private workflowConsoleService: WorkflowConsoleService,
    private location: Location,
    private route: ActivatedRoute,
    private operatorMetadataService: OperatorMetadataService,
    private message: NzMessageService
  ) {
    this.subscriptions.add(this.resultPanelToggleService.getToggleChangeStream().subscribe(
      value => this.showResultPanel = value
    ));
  }

  ngAfterViewInit(): void {

    /**
     * On initialization of the workspace, there could be three cases:
     *
     * - with userSystem enabled, usually during prod mode:
     * 1. Accessed by URL `/`, no workflow is in the URL (Cold Start):
     -    - A new `WorkflowActionService.DEFAULT_WORKFLOW` is created, which is an empty workflow with undefined id.
     *    - After an Auto-persist being triggered by a WorkflowAction event, it will create a new workflow in the database
     *    and update the URL with its new ID from database.
     * 2. Accessed by URL `/workflow/:id` (refresh manually, or redirected from dashboard workflow list):
     *    - It will retrieve the workflow from database with the given ID. Because it has an ID, it will be linked to the database
     *    - Auto-persist will be triggered upon all workspace events.
     *
     * - with userSystem disabled, during dev mode:
     * 1. Accessed by URL `/`, with a workflow cached (refresh manually):
     *    - This will trigger the WorkflowCacheService to load the workflow from cache.
     *    - Auto-cache will be triggered upon all workspace events.
     *
     * WorkflowActionService is the single source of the workflow representation. Both WorkflowCacheService and WorkflowPersistService are
     * reflecting changes from WorkflowActionService.
     */
    // clear the current workspace, reset as `WorkflowActionService.DEFAULT_WORKFLOW`
    this.workflowActionService.resetAsNewWorkflow();

    this.operatorMetadataService.getOperatorMetadata()
      .pipe(filter(metadata => metadata.operators.length !== 0)).subscribe(() => {
      if (environment.userSystemEnabled) {
        // load workflow with wid if presented in the URL
        if (this.route.snapshot.params.id) {
          const wid = this.route.snapshot.params.id;
          // if wid is present in the url, load it from the backend
          this.subscriptions.add(this.userService.userChanged().subscribe(() => this.loadWorkflowWithId(wid)));
        } else {
          // no workflow to load, pending to create a new workflow
        }
        // responsible for persisting the workflow to the backend
        this.registerAutoPersistWorkflow();
      } else {
        // load the cached workflow
        this.workflowActionService.reloadWorkflow(this.workflowCacheService.getCachedWorkflow());
        // clear stack
        this.undoRedoService.clearUndoStack();
        this.undoRedoService.clearRedoStack();
        // responsible for saving the existing workflow in cache
        this.registerAutoCacheWorkFlow();
      }
    });

  }

  public ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  private registerAutoCacheWorkFlow(): void {
    this.subscriptions.add(this.workflowActionService.workflowChanged().pipe(debounceTime(100))
      .subscribe(() => {
        this.workflowCacheService.setCacheWorkflow(this.workflowActionService.getWorkflow());
      }));
  }

  private registerAutoPersistWorkflow(): void {
    this.subscriptions.add(this.workflowActionService.workflowChanged().pipe(debounceTime(100))
      .subscribe(() => {
        if (this.userService.isLogin()) {
          this.workflowPersistService.persistWorkflow(this.workflowActionService.getWorkflow())
            .subscribe((updatedWorkflow: Workflow) => {
              this.workflowActionService.setWorkflowMetadata(updatedWorkflow);
              this.location.go(`/workflow/${updatedWorkflow.wid}`);
            });
          // to sync up with the updated information, such as workflow.wid
        }
      }));
  }

  private loadWorkflowWithId(wid: number): void {
    // disable the workspace until the workflow is fetched from the backend
    this.workflowActionService.disableWorkflowModification();
    this.workflowPersistService.retrieveWorkflow(wid).subscribe(
      (workflow: Workflow) => {
        // enable workspace for modification
        this.workflowActionService.enableWorkflowModification();
        // load the fetched workflow
        this.workflowActionService.reloadWorkflow(workflow);
        // clear stack
        this.undoRedoService.clearUndoStack();
        this.undoRedoService.clearRedoStack();
      },
      () => { this.message.error('You don\'t have access to this workflow, please log in with an appropriate account'); }
    );
  }

}
