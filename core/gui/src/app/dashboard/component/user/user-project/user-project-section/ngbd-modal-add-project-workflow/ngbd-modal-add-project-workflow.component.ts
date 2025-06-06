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

import { Component, inject, OnInit } from "@angular/core";
import { forkJoin, Observable } from "rxjs";
import { concatMap } from "rxjs/operators";
import { WorkflowPersistService } from "../../../../../../common/service/workflow-persist/workflow-persist.service";
import { DashboardWorkflow } from "../../../../../type/dashboard-workflow.interface";
import { UserProjectService } from "../../../../../service/user/project/user-project.service";
import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { NZ_MODAL_DATA } from "ng-zorro-antd/modal";

@UntilDestroy()
@Component({
  selector: "texera-add-project-workflow-modal",
  templateUrl: "./ngbd-modal-add-project-workflow.component.html",
  styleUrls: ["./ngbd-modal-add-project-workflow.component.scss"],
})
export class NgbdModalAddProjectWorkflowComponent implements OnInit {
  readonly projectId: number = inject(NZ_MODAL_DATA).projectId;

  public unaddedWorkflows: DashboardWorkflow[] = []; // tracks which workflows to display, the ones that have not yet been added to the project
  public checkedWorkflows: boolean[] = []; // used to implement check boxes
  private addedWorkflowKeys: Set<number> = new Set<number>(); // tracks which workflows to NOT display,  the workflow IDs of the workflows (if any) already inside the project
  private addedWorkflows: DashboardWorkflow[] = []; // for passing back to update the frontend cache, stores the new workflow list including newly added workflows

  constructor(
    private workflowPersistService: WorkflowPersistService,
    private userProjectService: UserProjectService
  ) {}

  ngOnInit(): void {
    this.refreshProjectWorkflowEntries();
  }

  public submitForm() {
    // data structure to track group of updates to make to backend
    let observables: Observable<Response>[] = [];

    // process any selected workflows, updating backend then frontend cache
    for (let index = 0; index < this.checkedWorkflows.length; ++index) {
      if (this.checkedWorkflows[index]) {
        // if workflow is checked
        observables.push(
          this.userProjectService.addWorkflowToProject(this.projectId, this.unaddedWorkflows[index].workflow.wid!)
        );
        this.addedWorkflows.push(this.unaddedWorkflows[index]); // for updating frontend cache
      }
    }

    // pass back data to update local cache after all changes propagated to backend
    forkJoin(observables).pipe(untilDestroyed(this)).subscribe();
  }

  public changeAll() {
    if (this.isAllChecked()) {
      this.checkedWorkflows.fill(false);
    } else {
      this.checkedWorkflows.fill(true);
    }
  }

  public isAllChecked() {
    return this.checkedWorkflows.length > 0 && this.checkedWorkflows.every(isChecked => isChecked);
  }

  private refreshProjectWorkflowEntries(): void {
    this.userProjectService
      .retrieveWorkflowsOfProject(this.projectId)
      .pipe(
        concatMap((dashboardWorkflowEntries: DashboardWorkflow[]) => {
          this.addedWorkflows = dashboardWorkflowEntries;
          dashboardWorkflowEntries.forEach(workflowEntry => this.addedWorkflowKeys.add(workflowEntry.workflow.wid!));
          return this.workflowPersistService.retrieveWorkflowsBySessionUser();
        }),
        untilDestroyed(this)
      )
      .subscribe(dashboardWorkflowEntries => {
        this.unaddedWorkflows = dashboardWorkflowEntries.filter(
          workflowEntry =>
            workflowEntry.workflow.wid !== undefined && !this.addedWorkflowKeys.has(workflowEntry.workflow.wid!)
        );
        this.checkedWorkflows = new Array(this.unaddedWorkflows.length).fill(false);
      });
  }
}
