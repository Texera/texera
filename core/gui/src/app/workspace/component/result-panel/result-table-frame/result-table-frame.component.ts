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

import { Component, Input, OnChanges, OnInit, SimpleChanges, ViewChild, TemplateRef } from "@angular/core";
import { NzModalRef, NzModalService } from "ng-zorro-antd/modal";
import { NzTableQueryParams } from "ng-zorro-antd/table";
import { WorkflowActionService } from "../../../service/workflow-graph/model/workflow-action.service";
import { WorkflowResultService } from "../../../service/workflow-result/workflow-result.service";
import { PanelResizeService } from "../../../service/workflow-result/panel-resize/panel-resize.service";
import { isWebPaginationUpdate } from "../../../types/execute-workflow.interface";
import { IndexableObject, TableColumn } from "../../../types/result-table.interface";
import { RowModalComponent } from "../result-panel-modal.component";
import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { ResultExportationComponent } from "../../result-exportation/result-exportation.component";
import { ChangeDetectorRef } from "@angular/core";
import { SchemaAttribute } from "../../../types/workflow-compiling.interface";
import { WorkflowStatusService } from "../../../service/workflow-status/workflow-status.service";
import {
  TableProfile,
  ColumnProfile,
  ColumnStatistics,
} from "../../../../common/type/proto/edu/uci/ics/amber/engine/architecture/worker/tableprofile";
import { WorkflowSuggestionService } from "../../../service/workflow-suggestion/workflow-suggestion.service";
import { finalize } from "rxjs";
import { WorkflowSuggestion, WorkflowSuggestionList } from "../../../types/workflow-suggestion.interface";

/**
 * The Component will display the result in an excel table format,
 *  where each row represents a result from the workflow,
 *  and each column represents the type of result the workflow returns.
 *
 * Clicking each row of the result table will create an pop-up window
 *  and display the detail of that row in a pretty json format.
 */
@UntilDestroy()
@Component({
  selector: "texera-result-table-frame",
  templateUrl: "./result-table-frame.component.html",
  styleUrls: ["./result-table-frame.component.scss"],
})
export class ResultTableFrameComponent implements OnInit, OnChanges {
  @Input() operatorId?: string;
  // display result table
  currentColumns?: TableColumn[];
  currentResult: IndexableObject[] = [];
  //   for more details
  //   see https://ng.ant.design/components/table/en#components-table-demo-ajax
  isFrontPagination: boolean = true;

  isLoadingResult: boolean = false;

  // paginator section, used when displaying rows

  // this attribute stores whether front-end should handle pagination
  //   if false, it means the pagination is managed by the server
  // this starts from **ONE**, not zero
  currentPageIndex: number = 1;
  totalNumTuples: number = 0;
  pageSize = 5;
  panelHeight = 0;
  widthPercent: string = "";
  sinkStorageMode: string = "";
  private schema: ReadonlyArray<SchemaAttribute> = [];
  tableProfile: TableProfile | undefined;

  // For Column Details Modal
  @ViewChild("columnDetailModalContent") columnDetailModalContent!: TemplateRef<any>;
  selectedColumnProfileForModal: ColumnProfile | undefined;
  columnNumericStatsForTable: Array<{ metric: string; value: string | number | undefined }> = [];
  barChartData: Array<{ name: string; value: number }> = [];
  // ngx-charts options
  view: [number, number] = [550, 300];
  showXAxis = true;
  showYAxis = true;
  gradient = false;
  showLegend = false;
  showXAxisLabel = true;
  xAxisLabel = "Category";
  showYAxisLabel = true;
  yAxisLabel = "Count";
  colorScheme = {
    domain: ["#5AA454", "#A10A28", "#C7B42C", "#AAAAAA"],
  };

  // For Global Stats Modal
  @ViewChild("globalStatsModalContent") globalStatsModalContent!: TemplateRef<any>;

  // For Data Cleaning Suggestions
  dataCleaningSuggestions: WorkflowSuggestion[] = [];
  isLoadingDataCleaningSuggestions: boolean = false;
  dataCleaningSuggestionsCache: Map<string, WorkflowSuggestion[]> = new Map();
  // Keep track of the column for which suggestions were last fetched to avoid redundant calls if modal isn't fully closed/reopened.
  lastFetchedSuggestionsForColumn: string | null = null;

  constructor(
    private modalService: NzModalService,
    private workflowActionService: WorkflowActionService,
    private workflowResultService: WorkflowResultService,
    private resizeService: PanelResizeService,
    private changeDetectorRef: ChangeDetectorRef,
    private workflowStatusService: WorkflowStatusService,
    private workflowSuggestionService: WorkflowSuggestionService
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    this.operatorId = changes.operatorId?.currentValue;
    if (this.operatorId) {
      const paginatedResultService = this.workflowResultService.getPaginatedResultService(this.operatorId);
      if (paginatedResultService) {
        this.isFrontPagination = false;
        this.totalNumTuples = paginatedResultService.getCurrentTotalNumTuples();
        this.currentPageIndex = paginatedResultService.getCurrentPageIndex();
        this.changePaginatedResultData();
        this.schema = paginatedResultService.getSchema();
      }
      this.subscribeToTableProfile();
    }
  }

  ngOnInit(): void {
    this.workflowResultService
      .getResultUpdateStream()
      .pipe(untilDestroyed(this))
      .subscribe(update => {
        if (!this.operatorId) {
          return;
        }
        const opUpdate = update[this.operatorId];
        if (!opUpdate || !isWebPaginationUpdate(opUpdate)) {
          return;
        }
        let columnCount = this.currentColumns?.length;
        if (columnCount) this.widthPercent = (1 / columnCount) * 100 + "%";
        this.isFrontPagination = false;
        this.totalNumTuples = opUpdate.totalNumTuples;
        if (opUpdate.dirtyPageIndices.includes(this.currentPageIndex)) {
          this.changePaginatedResultData();
        }
        this.changeDetectorRef.detectChanges();
      });

    this.workflowResultService
      .getSinkStorageMode()
      .pipe(untilDestroyed(this))
      .subscribe(sinkStorageMode => {
        this.sinkStorageMode = sinkStorageMode;
        this.adjustPageSizeBasedOnPanelSize(this.panelHeight);
      });

    this.resizeService.currentSize.pipe(untilDestroyed(this)).subscribe(size => {
      this.panelHeight = size.height;
      this.adjustPageSizeBasedOnPanelSize(size.height);
      let currentPageNum: number = Math.ceil(this.totalNumTuples / this.pageSize);
      while (this.currentPageIndex > currentPageNum && this.currentPageIndex > 1) {
        this.currentPageIndex -= 1;
      }
    });

    if (this.operatorId) {
      const paginatedResultService = this.workflowResultService.getPaginatedResultService(this.operatorId);
      if (paginatedResultService) {
        this.schema = paginatedResultService.getSchema();
      }
    }

    if (this.operatorId) {
      this.subscribeToTableProfile();
    }
  }

  private adjustPageSizeBasedOnPanelSize(panelHeight: number) {
    const rowHeight = 39; // use the rendered height of a row.
    let extra: number;

    extra = Math.floor((panelHeight - 170) / rowHeight);

    if (extra < 0) {
      extra = 0;
    }
    this.pageSize = 1 + extra;
    this.resizeService.pageSize = this.pageSize;
  }

  /**
   * Callback function for table query params changed event
   *   params containing new page index, new page size, and more
   *   (this function will be called when user switch page)
   *
   * @param params new parameters
   */
  onTableQueryParamsChange(params: NzTableQueryParams) {
    if (this.isFrontPagination) {
      return;
    }
    if (!this.operatorId) {
      return;
    }
    this.currentPageIndex = params.pageIndex;

    this.changePaginatedResultData();
  }

  /**
   * Opens the model to display the row details in
   *  pretty json format when clicked. User can view the details
   *  in a larger, expanded format.
   */
  open(indexInPage: number, rowData: IndexableObject): void {
    const currentRowIndex = indexInPage + (this.currentPageIndex - 1) * this.pageSize;
    // open the modal component
    const modalRef: NzModalRef<RowModalComponent> = this.modalService.create({
      // modal title
      nzTitle: "Row Details",
      nzContent: RowModalComponent,
      nzData: { operatorId: this.operatorId, rowIndex: currentRowIndex }, // set the index value and page size to the modal for navigation
      // prevent browser focusing close button (ugly square highlight)
      nzAutofocus: null,
      // modal footer buttons
      nzFooter: [
        {
          label: "<",
          onClick: () => {
            const component = modalRef.componentInstance;
            if (component) {
              component.rowIndex -= 1;
              this.currentPageIndex = Math.floor(component.rowIndex / this.pageSize) + 1;
              component.ngOnChanges();
            }
          },
          disabled: () => modalRef.componentInstance?.rowIndex === 0,
        },
        {
          label: ">",
          onClick: () => {
            const component = modalRef.componentInstance;
            if (component) {
              component.rowIndex += 1;
              this.currentPageIndex = Math.floor(component.rowIndex / this.pageSize) + 1;
              component.ngOnChanges();
            }
          },
          disabled: () => modalRef.componentInstance?.rowIndex === this.totalNumTuples - 1,
        },
        {
          label: "OK",
          onClick: () => {
            modalRef.destroy();
          },
          type: "primary",
        },
      ],
    });
  }

  // frontend table data must be changed, because:
  // 1. result panel is opened - must display currently selected page
  // 2. user selects a new page - must display new page data
  // 3. current page is dirty - must re-fetch data
  changePaginatedResultData(): void {
    if (!this.operatorId) {
      return;
    }
    const paginatedResultService = this.workflowResultService.getPaginatedResultService(this.operatorId);
    if (!paginatedResultService) {
      return;
    }
    this.isLoadingResult = true;
    paginatedResultService
      .selectPage(this.currentPageIndex, this.pageSize)
      .pipe(untilDestroyed(this))
      .subscribe(pageData => {
        if (this.currentPageIndex === pageData.pageIndex) {
          this.setupResultTable(pageData.table, paginatedResultService.getCurrentTotalNumTuples());
          this.schema = pageData.schema;
          this.changeDetectorRef.detectChanges();
        }
      });
  }

  /**
   * Updates all the result table properties based on the execution result,
   *  displays a new data table with a new paginator on the result panel.
   *
   * @param resultData rows of the result (may not be all rows if displaying result for workflow completed event)
   * @param totalRowCount
   */
  setupResultTable(resultData: ReadonlyArray<IndexableObject>, totalRowCount: number) {
    if (!this.operatorId) {
      return;
    }
    if (resultData.length < 1) {
      return;
    }

    this.isLoadingResult = false;
    this.changeDetectorRef.detectChanges();

    // creates a shallow copy of the readonly response.result,
    //  this copy will be has type object[] because MatTableDataSource's input needs to be object[]
    this.currentResult = resultData.slice();

    //  1. Get all the column names except '_id', using the first tuple
    //  2. Use those names to generate a list of display columns
    //  3. Pass the result data as array to generate a new data table

    let columns: { columnKey: any; columnText: string }[];

    const columnKeys = Object.keys(resultData[0]).filter(x => x !== "_id");
    columns = columnKeys.map(v => ({ columnKey: v, columnText: v }));

    // generate columnDef from first row, column definition is in order
    this.currentColumns = this.generateColumns(columns);
    this.totalNumTuples = totalRowCount;
  }

  /**
   * Generates all the column information for the result data table
   *
   * @param columns
   */
  generateColumns(columns: { columnKey: any; columnText: string }[]): TableColumn[] {
    return columns.map((col, index) => ({
      columnDef: col.columnKey,
      header: col.columnText,
      getCell: (row: IndexableObject) => row[col.columnKey].toString(),
    }));
  }

  downloadData(data: any, rowIndex: number, columnIndex: number, columnName: string): void {
    const realRowNumber = (this.currentPageIndex - 1) * this.pageSize + rowIndex;
    const defaultFileName = `${columnName}_${realRowNumber}`;
    const modal = this.modalService.create({
      nzTitle: "Export Data and Save to a Dataset",
      nzContent: ResultExportationComponent,
      nzData: {
        exportType: "data",
        workflowName: this.workflowActionService.getWorkflowMetadata.name,
        defaultFileName: defaultFileName,
        rowIndex: realRowNumber,
        columnIndex: columnIndex,
      },
      nzFooter: null,
    });
  }

  private subscribeToTableProfile(): void {
    if (!this.operatorId) {
      return;
    }

    // 1. set existing cached profile (if any)
    const cached = this.workflowStatusService.getCurrentTableProfiles();
    if (cached && cached[this.operatorId]) {
      this.tableProfile = cached[this.operatorId];
    }
    // 2. listen to subsequent updates
    this.workflowStatusService
      .getTableProfilesUpdateStream()
      .pipe(untilDestroyed(this))
      .subscribe(profiles => {
        const prof = profiles[this.operatorId!];
        if (prof) {
          this.tableProfile = prof;
          this.changeDetectorRef.detectChanges();
        }
      });
  }

  getColumnProfile(columnName: string): ColumnProfile | undefined {
    if (!this.tableProfile || !this.tableProfile.columnProfiles) return undefined;

    const target = columnName.trim();

    // exact match
    let profile = this.tableProfile.columnProfiles.find(p => p.columnName.trim() === target);

    // case-insensitive fallback
    if (!profile) {
      profile = this.tableProfile.columnProfiles.find(p => p.columnName.trim().toLowerCase() === target.toLowerCase());
    }

    return profile;
  }

  prepareColumnNumericStats(profile: ColumnProfile | undefined): void {
    this.columnNumericStatsForTable = [];
    if (!profile || !profile.statistics) return;

    const stats = profile.statistics;
    const dataType = profile.dataType.toLowerCase();
    const numericTypes = ["int", "integer", "float", "double", "numeric", "long"];

    // Always show Null Count and Unique Count
    this.columnNumericStatsForTable.push({ metric: "Null Count", value: stats.nullCount });
    if (stats.uniqueCount !== undefined && typeof stats.uniqueCount === "number") {
      this.columnNumericStatsForTable.push({ metric: "Unique Count", value: stats.uniqueCount.toLocaleString() });
    } else if (stats.uniqueCount !== undefined) {
      this.columnNumericStatsForTable.push({ metric: "Unique Count", value: stats.uniqueCount });
    }

    // Add total row count from global profile if available
    if (this.tableProfile && this.tableProfile.globalProfile) {
      this.columnNumericStatsForTable.push({
        metric: "Total Rows in Table",
        value: this.tableProfile.globalProfile.rowCount.toLocaleString(),
      });
    }

    // Show Min, Max, Mean, Std Dev only for numeric types and if the value is a number
    if (numericTypes.includes(dataType)) {
      if (stats.min !== undefined && typeof stats.min === "number") {
        this.columnNumericStatsForTable.push({ metric: "Min", value: stats.min.toLocaleString() });
      } else if (stats.min !== undefined) {
        this.columnNumericStatsForTable.push({ metric: "Min", value: stats.min });
      }

      if (stats.max !== undefined && typeof stats.max === "number") {
        this.columnNumericStatsForTable.push({ metric: "Max", value: stats.max.toLocaleString() });
      } else if (stats.max !== undefined) {
        this.columnNumericStatsForTable.push({ metric: "Max", value: stats.max });
      }

      if (stats.mean !== undefined && typeof stats.mean === "number") {
        this.columnNumericStatsForTable.push({ metric: "Mean", value: stats.mean.toFixed(2) });
      } else if (stats.mean !== undefined) {
        this.columnNumericStatsForTable.push({ metric: "Mean", value: stats.mean });
      }

      if (stats.stddev !== undefined && typeof stats.stddev === "number") {
        this.columnNumericStatsForTable.push({ metric: "Std Dev", value: stats.stddev.toFixed(2) });
      } else if (stats.stddev !== undefined) {
        this.columnNumericStatsForTable.push({ metric: "Std Dev", value: stats.stddev });
      }
    }
  }

  prepareBarChartData(categoricalCount: { [key: string]: number } | undefined): void {
    this.barChartData = [];
    if (!categoricalCount) return;

    this.barChartData = Object.entries(categoricalCount)
      .map(([name, value]) => ({ name, value }))
      .sort((a, b) => b.value - a.value) // Sort by count descending
      .slice(0, 10); // Take top 10 for display
  }

  showColumnDetails(columnName: string, event: MouseEvent): void {
    event.stopPropagation();
    this.selectedColumnProfileForModal = this.getColumnProfile(columnName);

    if (!this.selectedColumnProfileForModal) return;

    this.prepareColumnNumericStats(this.selectedColumnProfileForModal);
    if (this.selectedColumnProfileForModal.categorical && this.selectedColumnProfileForModal.statistics) {
      this.prepareBarChartData(this.selectedColumnProfileForModal.statistics.categoricalCount);
    } else {
      this.barChartData = [];
    }

    // Handle Data Cleaning Suggestions
    this.dataCleaningSuggestions = []; // Clear previous suggestions
    const cachedSuggestions = this.dataCleaningSuggestionsCache.get(this.selectedColumnProfileForModal.columnName);
    if (cachedSuggestions) {
      this.dataCleaningSuggestions = cachedSuggestions;
      this.lastFetchedSuggestionsForColumn = this.selectedColumnProfileForModal.columnName; // Mark as fetched from cache
      // Optionally, you might still want to trigger a background refresh or rely on the refresh button
    } else {
      this.fetchDataCleaningSuggestions(this.selectedColumnProfileForModal);
    }

    this.modalService.create({
      nzTitle: `Column Details: ${this.selectedColumnProfileForModal.columnName}`,
      nzContent: this.columnDetailModalContent,
      nzWidth: "700px",
      nzFooter: [
        {
          label: "OK",
          type: "primary",
          onClick: () => {
            this.modalService.closeAll();
            this.lastFetchedSuggestionsForColumn = null; // Reset when modal closes
            this.dataCleaningSuggestions = []; // Clear suggestions on modal close
          },
        },
      ],
      nzOnCancel: () => {
        // Also handle if modal is closed via ESC or 'x' button
        this.lastFetchedSuggestionsForColumn = null;
        this.dataCleaningSuggestions = [];
      },
    });
  }

  handleDataCleaningSuggestionClick(suggestion: WorkflowSuggestion): void {
    console.log("Clicked data cleaning suggestion:", suggestion);
    // Placeholder for future action:
    // - Apply changes described in suggestion.changes
    // - Open a new dialog for confirmation
    // - Trigger another service call
    // For now, we just log it.
    // You might want to close the modal or keep it open depending on the action.
    // this.modalService.closeAll();
  }

  fetchDataCleaningSuggestions(columnProfile: ColumnProfile | undefined) {
    if (!columnProfile || !this.tableProfile) {
      this.dataCleaningSuggestions = [];
      return;
    }

    // Simplified logic for fetching, always clear and fetch if not from cache immediately
    // The `lastFetchedSuggestionsForColumn` and the more complex conditional
    // were making it hard to ensure a refresh always happens when `fetchData...` is called directly.
    // Cache is still used in `showColumnDetails`.

    this.isLoadingDataCleaningSuggestions = true;
    this.dataCleaningSuggestions = []; // Clear previous before fetching new
    this.workflowSuggestionService
      .getDataCleaningSuggestions(this.tableProfile, columnProfile.columnName)
      .pipe(
        finalize(() => {
          this.isLoadingDataCleaningSuggestions = false;
          this.changeDetectorRef.detectChanges(); // Ensure UI updates
        }),
        untilDestroyed(this)
      )
      .subscribe(
        (response: WorkflowSuggestionList) => {
          this.dataCleaningSuggestions = response.suggestions;
          this.dataCleaningSuggestionsCache.set(columnProfile.columnName, response.suggestions);
          this.lastFetchedSuggestionsForColumn = columnProfile.columnName;
        },
        (error: unknown) => {
          console.error("Error fetching data cleaning suggestions:", error);
          this.dataCleaningSuggestions = []; // Clear on error
        }
      );
  }

  refreshDataCleaningSuggestions(): void {
    if (this.selectedColumnProfileForModal) {
      // No need to set lastFetchedSuggestionsForColumn to null here,
      // as fetchDataCleaningSuggestions will be called directly.
      // Clearing the cache ensures a fresh fetch.
      this.dataCleaningSuggestionsCache.delete(this.selectedColumnProfileForModal.columnName);
      this.fetchDataCleaningSuggestions(this.selectedColumnProfileForModal);
    }
  }

  showGlobalStats(): void {
    if (!this.tableProfile || !this.tableProfile.globalProfile) return;

    this.modalService.create({
      nzTitle: "Table Statistics",
      nzContent: this.globalStatsModalContent,
      nzWidth: 600,
      nzFooter: [
        {
          label: "OK",
          type: "primary",
          onClick: () => this.modalService.closeAll(),
        },
      ],
    });
  }
}
