/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { Component, inject, Input, OnInit } from "@angular/core";
import { WorkflowResultExportService } from "../../service/workflow-result-export/workflow-result-export.service";
import { DashboardDataset } from "../../../dashboard/type/dashboard-dataset.interface";
import { DatasetService } from "../../../dashboard/service/user/dataset/dataset.service";
import { NZ_MODAL_DATA, NzModalRef } from "ng-zorro-antd/modal";
import { WorkflowActionService } from "../../service/workflow-graph/model/workflow-action.service";
import { WorkflowResultService } from "../../service/workflow-result/workflow-result.service";
import { ComputingUnitStatusService } from "../../service/computing-unit-status/computing-unit-status.service";
import { DashboardWorkflowComputingUnit } from "../../types/workflow-computing-unit";

@UntilDestroy()
@Component({
  selector: "texera-result-exportation-modal",
  templateUrl: "./result-exportation.component.html",
  styleUrls: ["./result-exportation.component.scss"],
})
export class ResultExportationComponent implements OnInit {
  /* Two sources can trigger this dialog, one from context-menu
   which only export highlighted operators
   and second is menu which wants to export all operators
   */
  sourceTriggered: string = inject(NZ_MODAL_DATA).sourceTriggered;
  workflowName: string = inject(NZ_MODAL_DATA).workflowName;
  inputFileName: string = inject(NZ_MODAL_DATA).defaultFileName ?? "";
  rowIndex: number = inject(NZ_MODAL_DATA).rowIndex ?? -1;
  columnIndex: number = inject(NZ_MODAL_DATA).columnIndex ?? -1;
  destination: string = "";
  exportType: string = inject(NZ_MODAL_DATA).exportType ?? "";
  isTableOutput: boolean = false;
  isVisualizationOutput: boolean = false;
  containsBinaryData: boolean = false;
  inputDatasetName = "";
  selectedComputingUnit: DashboardWorkflowComputingUnit | null = null;

  userAccessibleDatasets: DashboardDataset[] = [];
  filteredUserAccessibleDatasets: DashboardDataset[] = [];

  constructor(
    public workflowResultExportService: WorkflowResultExportService,
    private modalRef: NzModalRef,
    private datasetService: DatasetService,
    private workflowActionService: WorkflowActionService,
    private workflowResultService: WorkflowResultService,
    private computingUnitStatusService: ComputingUnitStatusService
  ) {}

  ngOnInit(): void {
    this.datasetService
      .retrieveAccessibleDatasets()
      .pipe(untilDestroyed(this))
      .subscribe(datasets => {
        this.userAccessibleDatasets = datasets.filter(dataset => dataset.accessPrivilege === "WRITE");
        this.filteredUserAccessibleDatasets = [...this.userAccessibleDatasets];
      });
    this.updateOutputType();

    this.computingUnitStatusService
      .getSelectedComputingUnit()
      .pipe(untilDestroyed(this))
      .subscribe(unit => {
        this.selectedComputingUnit = unit;
      });
  }

  updateOutputType(): void {
    // Determine if the caller of this component is menu or context menu
    // if its menu then we need to export all operators else we need to export only highlighted operators

    let operatorIds: readonly string[];
    if (this.sourceTriggered === "menu") {
      operatorIds = this.workflowActionService
        .getTexeraGraph()
        .getAllOperators()
        .map(op => op.operatorID);
    } else {
      operatorIds = this.workflowActionService.getJointGraphWrapper().getCurrentHighlightedOperatorIDs();
    }

    if (operatorIds.length === 0) {
      // No operators highlighted
      this.isTableOutput = false;
      this.isVisualizationOutput = false;
      this.containsBinaryData = false;
      return;
    }

    // Assume they're all table or visualization
    // until we find an operator that isn't
    let allTable = true;
    let allVisualization = true;
    let anyBinaryData = false;

    for (const operatorId of operatorIds) {
      const outputTypes = this.workflowResultService.determineOutputTypes(operatorId);
      if (!outputTypes.hasAnyResult) {
        continue;
      }
      if (!outputTypes.isTableOutput) {
        allTable = false;
      }
      if (!outputTypes.isVisualizationOutput) {
        allVisualization = false;
      }
      if (outputTypes.containsBinaryData) {
        anyBinaryData = true;
      }
    }

    this.isTableOutput = allTable;
    this.isVisualizationOutput = allVisualization;
    this.containsBinaryData = anyBinaryData;
  }

  onUserInputDatasetName(event: Event): void {
    const value = this.inputDatasetName;

    if (value) {
      this.filteredUserAccessibleDatasets = this.userAccessibleDatasets.filter(
        dataset => dataset.dataset.did && dataset.dataset.name.toLowerCase().includes(value)
      );
    }
  }

  onClickExportResult(destination: "dataset" | "local", dataset: DashboardDataset = {} as DashboardDataset) {
    const datasetIds =
      destination === "dataset" ? [dataset.dataset.did].filter((id): id is number => id !== undefined) : [];
    this.workflowResultExportService.exportWorkflowExecutionResult(
      this.exportType,
      this.workflowName,
      datasetIds,
      this.rowIndex,
      this.columnIndex,
      this.inputFileName,
      this.sourceTriggered === "menu",
      destination,
      this.selectedComputingUnit
    );
    this.modalRef.close();
  }
}
