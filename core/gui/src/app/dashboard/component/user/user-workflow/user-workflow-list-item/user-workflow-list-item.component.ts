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

import { Component, EventEmitter, Input, Output } from "@angular/core";
import { NzModalService } from "ng-zorro-antd/modal";
import { WorkflowExecutionHistoryComponent } from "../ngbd-modal-workflow-executions/workflow-execution-history.component";
import {
  DEFAULT_WORKFLOW_NAME,
  WorkflowPersistService,
} from "../../../../../common/service/workflow-persist/workflow-persist.service";
import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { ShareAccessComponent } from "../../share-access/share-access.component";
import { Workflow } from "../../../../../common/type/workflow";
import { DashboardProject } from "../../../../type/dashboard-project.interface";
import { UserProjectService } from "../../../../service/user/project/user-project.service";
import { DashboardEntry } from "../../../../type/dashboard-entry";
import { firstValueFrom } from "rxjs";
import { DownloadService } from "src/app/dashboard/service/user/download/download.service";
import { DASHBOARD_USER_PROJECT, DASHBOARD_USER_WORKSPACE } from "../../../../../app-routing.constant";
import { GuiConfigService } from "../../../../../common/service/gui-config.service";

@UntilDestroy()
@Component({
  selector: "texera-user-workflow-list-item",
  templateUrl: "./user-workflow-list-item.component.html",
  styleUrls: ["./user-workflow-list-item.component.scss"],
})
export class UserWorkflowListItemComponent {
  protected readonly DASHBOARD_USER_WORKSPACE = DASHBOARD_USER_WORKSPACE;
  protected readonly DASHBOARD_USER_PROJECT = DASHBOARD_USER_PROJECT;
  private _entry?: DashboardEntry;
  @Input() public keywords: string[] = [];

  @Input()
  get entry(): DashboardEntry {
    if (!this._entry) {
      throw new Error("entry property must be provided to UserWorkflowListItemComponent.");
    }
    return this._entry;
  }

  set entry(value: DashboardEntry) {
    this._entry = value;
  }

  get workflow(): Workflow {
    if (!this.entry.workflow) {
      throw new Error(
        "Incorrect type of DashboardEntry provided to UserWorkflowListItemComponent. Entry must be workflow."
      );
    }
    return this.entry.workflow.workflow;
  }

  @Input() editable = false;
  @Input() public pid: number = 0;
  userProjectsMap: ReadonlyMap<number, DashboardProject> = new Map();
  @Output() deleted = new EventEmitter<void>();
  @Output() duplicated = new EventEmitter<void>();

  editingName = false;
  editingDescription = false;

  constructor(
    private workflowPersistService: WorkflowPersistService,
    private modalService: NzModalService,
    protected config: GuiConfigService,
    private userProjectService: UserProjectService,
    private downloadService: DownloadService
  ) {
    this.userProjectService
      .getProjectList()
      .pipe(untilDestroyed(this))
      .subscribe(userProjectsList => {
        this.userProjectsMap = new Map(userProjectsList.map(userProject => [userProject.pid, userProject]));
      });
  }

  getProjectIds() {
    return new Set(this.entry.workflow.projectIDs);
  }

  /**
   * open the workflow executions page
   */
  public onClickGetWorkflowExecutions(): void {
    this.modalService.create({
      nzContent: WorkflowExecutionHistoryComponent,
      nzData: { wid: this.workflow.wid },
      nzTitle: "Execution results of Workflow: " + this.workflow.name,
      nzFooter: null,
      nzWidth: "80%",
      nzCentered: true,
    });
  }

  public confirmUpdateWorkflowCustomName(name: string): void {
    if (this.workflow.wid === undefined) {
      return;
    }

    this.workflowPersistService
      .updateWorkflowName(this.workflow.wid, name || DEFAULT_WORKFLOW_NAME)
      .pipe(untilDestroyed(this))
      .subscribe(() => {
        this.workflow.name = name || DEFAULT_WORKFLOW_NAME;
      })
      .add(() => {
        this.editingName = false;
      });
  }

  public confirmUpdateWorkflowCustomDescription(description: string): void {
    if (this.workflow.wid === undefined) {
      return;
    }

    this.workflowPersistService
      .updateWorkflowDescription(this.workflow.wid, description)
      .pipe(untilDestroyed(this))
      .subscribe(() => {
        this.workflow.description = description;
      })
      .add(() => {
        this.editingDescription = false;
      });
  }

  /**
   * open the Modal based on the workflow clicked on
   */
  public async onClickOpenShareAccess(): Promise<void> {
    this.modalService.create({
      nzContent: ShareAccessComponent,
      nzData: {
        writeAccess: this.entry.workflow.accessLevel === "WRITE",
        type: "workflow",
        id: this.workflow.wid,
        allOwners: await firstValueFrom(this.workflowPersistService.retrieveOwners()),
      },
      nzFooter: null,
      nzTitle: "Share this workflow with others",
      nzCentered: true,
    });
  }

  /**
   * Download the workflow as a json file
   */
  public onClickDownloadWorkfllow(): void {
    if (this.workflow.wid) {
      this.downloadService
        .downloadWorkflow(this.workflow.wid, this.workflow.name)
        .pipe(untilDestroyed(this))
        .subscribe();
    }
  }

  public isLightColor(color: string): boolean {
    return UserProjectService.isLightColor(color);
  }

  /**
   * For color tags, enable clicking 'x' to remove a workflow from a project
   */
  public removeWorkflowFromProject(pid: number): void {
    this.userProjectService
      .removeWorkflowFromProject(pid, this.workflow.wid!)
      .pipe(untilDestroyed(this))
      .subscribe(() => {
        this.entry.workflow.projectIDs = this.entry.workflow.projectIDs.filter(projectID => projectID != pid);
      });
  }
}
