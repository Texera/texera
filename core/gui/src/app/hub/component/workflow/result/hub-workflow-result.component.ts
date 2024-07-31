import { Component, OnInit } from "@angular/core";
import { ActivatedRoute } from "@angular/router";
import { HubWorkflow } from "../../type/hub-workflow.interface";
import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { SearchService } from "../../../../dashboard/service/user/search.service";
import { SearchFilterParameters } from "../../../../dashboard/type/search-filter-parameters";
import { SortMethod } from "../../../../dashboard/type/sort-method";
import { SearchResult } from "../../../../dashboard/type/search-result";
import { DashboardWorkflow } from "../../../../dashboard/type/dashboard-workflow.interface";
import { HubWorkflowService } from "../../../service/workflow/hub-workflow.service";

@UntilDestroy()
@Component({
  selector: "texera-hub-workflow-result",
  templateUrl: "hub-workflow-result.component.html",
  styleUrls: ["hub-workflow-result.component.scss"],
})
export class HubWorkflowResultComponent implements OnInit {
  query: string = "";
  listOfWorkflowsWithUserInfo: Array<HubWorkflow & { userName?: string; userGoogleAvatar?: string; color?: string }> =
    [];
  currentPage: number = 1;
  resultsPerPage: number = 10;
  totalResults: number = 0;

  constructor(
    private route: ActivatedRoute,
    private searchService: SearchService,
    private hubWorkflowService: HubWorkflowService
  ) {}

  ngOnInit(): void {
    // eslint-disable-next-line rxjs-angular/prefer-takeuntil
    this.route.queryParams.subscribe(queryParams => {
      this.query = queryParams["q"];
      this.loadTotalResultsNum();
      this.loadWorkflows();
    });
  }

  private loadTotalResultsNum(): void {
    this.hubWorkflowService
      .getWorkflowCount()
      .pipe(untilDestroyed(this))
      .subscribe(count => {
        this.totalResults = count;
      });
  }

  private loadWorkflows(): void {
    const offset = (this.currentPage - 1) * this.resultsPerPage;

    const params: SearchFilterParameters = {
      createDateStart: null,
      createDateEnd: null,
      modifiedDateStart: null,
      modifiedDateEnd: null,
      owners: [],
      ids: [],
      operators: [],
      projectIds: [],
    };

    this.searchService
      .conditional_search([this.query], params, offset, this.resultsPerPage, "workflow", SortMethod.NameAsc, "public")
      .pipe(untilDestroyed(this))
      .subscribe((result: SearchResult) => {
        const listOfWorkflows = result.results
          .filter(item => item.resourceType === "workflow" && item.workflow !== undefined)
          .map(item => this.convertToHubWorkflow(item.workflow!));

        const wids = listOfWorkflows.map(workflow => workflow.wid).filter(wid => wid !== undefined) as number[];

        this.hubWorkflowService
          .getUserInfo(wids)
          .pipe(untilDestroyed(this))
          .subscribe(userMap => {
            this.listOfWorkflowsWithUserInfo = listOfWorkflows.map(workflow => {
              const wid = workflow.wid;
              if (wid !== undefined) {
                return {
                  ...workflow,
                  userName: userMap[wid]?.name,
                  userGoogleAvatar: userMap[wid]?.googleAvatar,
                  color: undefined,
                };
              } else {
                return workflow;
              }
            });
          });
      });
  }

  onPageChange(page: number): void {
    if (page !== this.currentPage) {
      this.currentPage = page;
      this.loadWorkflows();
    }
  }

  private convertToHubWorkflow(dashboardWorkflow: DashboardWorkflow): HubWorkflow {
    return {
      name: dashboardWorkflow.workflow.name,
      description: dashboardWorkflow.workflow.description,
      wid: dashboardWorkflow.workflow.wid,
      creationTime: dashboardWorkflow.workflow.creationTime,
      lastModifiedTime: dashboardWorkflow.workflow.lastModifiedTime,
      owner: undefined,
    };
  }
}
