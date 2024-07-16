import { Injectable } from "@angular/core";
import { Input, ViewChild } from "@angular/core";
import { Router } from "@angular/router";
import { NzModalService } from "ng-zorro-antd/modal";
import { firstValueFrom, observable, of } from "rxjs";
import {
  DEFAULT_WORKFLOW_NAME,
  WorkflowPersistService,
} from "../../../../common/service/workflow-persist/workflow-persist.service";
import { DashboardEntry } from "../../type/dashboard-entry";
import { UserService } from "../../../../common/service/user/user.service";
import { untilDestroyed } from "@ngneat/until-destroy";
import { NotificationService } from "../../../../common/service/notification/notification.service";
import { WorkflowContent } from "../../../../common/type/workflow";
import { FileSaverService } from "../../service/user-file/file-saver.service";
import { FiltersComponent } from "../../component/filters/filters.component";
import { SearchResultsComponent } from "../../component/search-results/search-results.component";
import { SearchService } from "../../service/search.service";
import { SortMethod } from "../../type/sort-method";
import { isDefined } from "../../../../common/util/predicate";
import { UserProjectService } from "../../service/user-project/user-project.service";
import { map, mergeMap, tap } from "rxjs/operators";
import { Observable } from "rxjs";
import { WorkflowActionService } from "../../../../workspace/service/workflow-graph/model/workflow-action.service";
import { WorkflowUtilService } from "../../../../workspace/service/workflow-graph/util/workflow-util.service";

import { observe } from "@ngx-formly/core/lib/utils";
import { Point } from "../../../../workspace/types/workflow-common.interface";

/**
 * UserWorkflowService facilitates creating a new workflow from the dataset dashboard
 */

@Injectable({
  providedIn: "root",
})
export class UserWorkflowService {
  public ROUTER_WORKFLOW_BASE_URL = "workflow";
  private _searchResultsComponent?: SearchResultsComponent;
  @ViewChild(SearchResultsComponent) get searchResultsComponent(): SearchResultsComponent {
    if (this._searchResultsComponent) {
      return this._searchResultsComponent;
    }
    throw new Error("Property cannot be accessed before it is initialized.");
  }
  set searchResultsComponent(value: SearchResultsComponent) {
    this._searchResultsComponent = value;
  }
  private _filters?: FiltersComponent;
  @ViewChild(FiltersComponent) get filters(): FiltersComponent {
    if (this._filters) {
      return this._filters;
    }
    throw new Error("Property cannot be accessed before it is initialized.");
  }
  set filters(value: FiltersComponent) {
    value.masterFilterListChange.pipe(untilDestroyed(this)).subscribe({ next: () => this.search() });
    this._filters = value;
  }
  private masterFilterList: ReadonlyArray<string> | null = null;

  // receive input from parent components (UserProjectSection), if any
  @Input() public pid?: number = undefined;
  @Input() public accessLevel?: string = undefined;
  public sortMethod = SortMethod.EditTimeDesc;
  lastSortMethod: SortMethod | null = null;

  constructor(
    private workflowPersistService: WorkflowPersistService,
    private userProjectService: UserProjectService,
    private searchService: SearchService,
    private workflowUtilService: WorkflowUtilService
  ) {}

  /* Creates a workflow from the dataset dashboard with a pre-initialized scan operator*/
  public onClickCreateNewWorkflowFromDatasetDashboard(
    datasetFile: string,
    scanOption: string
  ): Observable<number | undefined> {
    let operatorPredicate = this.workflowUtilService.getNewOperatorPredicate(scanOption);
    operatorPredicate = this.workflowUtilService.addFileName(operatorPredicate, datasetFile); // add the filename

    let localPid = this.pid;
    let point: Point = { x: 474, y: 235 };
    let emptyWorkflowContent: WorkflowContent = {
      operators: [operatorPredicate],
      commentBoxes: [],
      groups: [],
      links: [],
      operatorPositions: {
        [operatorPredicate.operatorID]: { x: 474, y: 235 },
      },
    };

    return this.workflowPersistService.createWorkflow(emptyWorkflowContent, DEFAULT_WORKFLOW_NAME).pipe(
      tap(createdWorkflow => {
        if (!createdWorkflow.workflow.wid) {
          throw new Error("Workflow creation failed.");
        }
      }),
      mergeMap(createdWorkflow => {
        // Check if localPid is defined; if so, add the workflow to the project
        if (localPid) {
          return this.userProjectService.addWorkflowToProject(localPid, createdWorkflow.workflow.wid!).pipe(
            // Regardless of the project addition outcome, pass the wid downstream
            map(() => createdWorkflow.workflow.wid)
          );
        } else {
          // If there's no localPid, skip adding to the project and directly pass the wid downstream
          return of(createdWorkflow.workflow.wid);
        }
      })
      //untilDestroyed(this)
    );
  }

  /**
   * Searches workflows with keywords and filters given in the masterFilterList.
   * @returns
   */
  async search(forced: Boolean = false): Promise<void> {
    const sameList =
      this.masterFilterList !== null &&
      this.filters.masterFilterList.length === this.masterFilterList.length &&
      this.filters.masterFilterList.every((v, i) => v === this.masterFilterList![i]);
    if (!forced && sameList && this.sortMethod === this.lastSortMethod) {
      // If the filter lists are the same, do no make the same request again.
      return;
    }
    this.lastSortMethod = this.sortMethod;
    this.masterFilterList = this.filters.masterFilterList;
    let filterParams = this.filters.getSearchFilterParameters();
    if (isDefined(this.pid)) {
      // force the project id in the search query to be the current pid.
      filterParams.projectIds = [this.pid];
    }
    this.searchResultsComponent.reset(async (start, count) => {
      const results = await firstValueFrom(
        this.searchService.search(
          this.filters.getSearchKeywords(),
          filterParams,
          start,
          count,
          "workflow",
          this.sortMethod
        )
      );
      return {
        entries: results.results.map(i => {
          if (i.workflow) {
            return new DashboardEntry(i.workflow);
          } else {
            throw new Error("Unexpected type in SearchResult.");
          }
        }),
        more: results.more,
      };
    });
    await this.searchResultsComponent.loadMore();
  }
}
