import { Component, OnInit } from "@angular/core";
import { HubWorkflowService } from "../../../service/workflow/hub-workflow.service";
import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { ActivatedRoute, Router } from "@angular/router";
import { SortMethod } from "../../../../dashboard/type/sort-method";
import { SearchResult } from "../../../../dashboard/type/search-result";
import { HubWorkflow } from "../../type/hub-workflow.interface";
import { DashboardWorkflow } from "../../../../dashboard/type/dashboard-workflow.interface";
import { UserService } from "src/app/common/service/user/user.service";

@UntilDestroy()
@Component({
  selector: "texera-hub-workflow-search",
  templateUrl: "hub-workflow-search.component.html",
  styleUrls: ["hub-workflow-search.component.scss"],
})
export class HubWorkflowSearchComponent implements OnInit {
  workflowCount: number | undefined;
  recentWorkflowList: HubWorkflow[] = [];
  popularWorkflowList: HubWorkflow[] = [];
  hasResults: boolean = false;
  public isLoggedIn: boolean = false;
  constructor(
    private hubWorkflowService: HubWorkflowService,
    private router: Router,
    private userService: UserService
  ) {
    this.isLoggedIn = this.userService.isLogin();
  }

  ngOnInit() {
    this.hubWorkflowService
      .getWorkflowCount()
      .pipe(untilDestroyed(this))
      .subscribe(count => {
        this.workflowCount = count;
      });

    if (this.isLoggedIn) {
      this.hubWorkflowService
        .getRecentWorkflows()
        .pipe(untilDestroyed(this))
        .subscribe(workflowList => {
          this.recentWorkflowList = workflowList;
        });
    }

    this.hubWorkflowService
      .getPopularWorkflows()
      .pipe(untilDestroyed(this))
      .subscribe(workflowList => {
        this.popularWorkflowList = workflowList;
      });
  }

  navigateToSearchResult(): void {
    this.router.navigate(["/dashboard/hub/workflow/search/result"], { queryParams: { q: "" } });
  }
}
