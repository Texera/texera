import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { Component, inject, Input, OnInit } from "@angular/core";
import { environment } from "../../../../environments/environment";
import { WorkflowResultExportService } from "../../service/workflow-result-export/workflow-result-export.service";
import { DashboardDataset } from "../../../dashboard/type/dashboard-dataset.interface";
import { DatasetService } from "../../../dashboard/service/user/dataset/dataset.service";
import { NZ_MODAL_DATA, NzModalRef } from "ng-zorro-antd/modal";

@UntilDestroy()
@Component({
  selector: "texera-result-exportation-modal",
  templateUrl: "./result-exportation.component.html",
  styleUrls: ["./result-exportation.component.scss"],
})
export class ResultExportationComponent implements OnInit {
  exportType: string = inject(NZ_MODAL_DATA).exportType;
  workflowName: string = inject(NZ_MODAL_DATA).workflowName;
  inputFileName: string = inject(NZ_MODAL_DATA).defaultFileName ?? "default_filename";
  rowIndex: number = inject(NZ_MODAL_DATA).rowIndex ?? -1;
  columnIndex: number = inject(NZ_MODAL_DATA).columnIndex ?? -1;

  inputDatasetName = "";

  userAccessibleDatasets: DashboardDataset[] = [];
  filteredUserAccessibleDatasets: DashboardDataset[] = [];

  constructor(
    private modalRef: NzModalRef,
    private workflowResultExportService: WorkflowResultExportService,
    private datasetService: DatasetService
  ) {}

  ngOnInit(): void {
    this.datasetService
      .retrieveAccessibleDatasets()
      .pipe(untilDestroyed(this))
      .subscribe(response => {
        const datasets = response.datasets;
        this.userAccessibleDatasets = datasets.filter(dataset => dataset.accessPrivilege === "WRITE");
        this.filteredUserAccessibleDatasets = [...this.userAccessibleDatasets];
      });
  }

  onUserInputDatasetName(event: Event): void {
    const value = this.inputDatasetName;

    if (value) {
      this.filteredUserAccessibleDatasets = this.userAccessibleDatasets.filter(
        dataset => dataset.dataset.did && dataset.dataset.name.toLowerCase().includes(value)
      );
    }
  }

  onClickSaveResultFileToDatasets(dataset: DashboardDataset) {
    if (dataset.dataset.did) {
      this.workflowResultExportService.exportWorkflowExecutionResult(
        this.exportType,
        this.workflowName,
        [dataset.dataset.did],
        this.rowIndex,
        this.columnIndex,
        this.inputFileName
      );
      this.modalRef.close();
    }
  }
}
