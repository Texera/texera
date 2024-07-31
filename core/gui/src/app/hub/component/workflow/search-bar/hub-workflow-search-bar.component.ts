import { Component } from "@angular/core";
import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { Router } from "@angular/router";
import { SearchService } from "../../../../dashboard/service/user/search.service";
import { SearchFilterParameters } from "../../../../dashboard/type/search-filter-parameters";
import { SearchResult } from "../../../../dashboard/type/search-result";
import { SortMethod } from "../../../../dashboard/type/sort-method";
import { DashboardWorkflow } from "../../../../dashboard/type/dashboard-workflow.interface";

@UntilDestroy()
@Component({
  selector: "texera-hub-workflow-search-bar",
  templateUrl: "hub-workflow-search-bar.component.html",
  styleUrls: ["hub-workflow-search-bar.component.scss"],
})
export class HubWorkflowSearchBarComponent {
  inputValue?: string;
  workflowNames: string[] = [];
  filteredOptions: string[] = [];
  keywords: string[] = [];
  constructor(
    private searchService: SearchService,
    private router: Router
  ) {
    this.filteredOptions = this.workflowNames;
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
      .conditional_search([], params, 0, 3, "workflow", SortMethod.NameAsc, "public")
      .pipe(untilDestroyed(this))
      .subscribe((result: SearchResult) => {
        this.workflowNames = result.results
          .filter(item => item.resourceType === "workflow")
          .map(item => (item.workflow as DashboardWorkflow)?.workflow.name ?? "");
      });
  }

  onChange(value: string) {
    this.filteredOptions = this.workflowNames.filter(
      option => option.toLowerCase().indexOf(value.toLowerCase()) !== -1
    );
  }

  onSubmit() {
    this.router.navigate(["/dashboard/hub/workflow/search/result"], { queryParams: { q: this.inputValue } });
  }
}
