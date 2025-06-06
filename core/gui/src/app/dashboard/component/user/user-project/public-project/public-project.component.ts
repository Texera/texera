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
import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { PublicProjectService } from "../../../../service/user/public-project/public-project.service";
import { PublicProject } from "../../../../type/dashboard-project.interface";
import { NZ_MODAL_DATA, NzModalRef } from "ng-zorro-antd/modal";

@UntilDestroy()
@Component({
  templateUrl: "public-project.component.html",
})
export class PublicProjectComponent implements OnInit {
  readonly modal = inject(NzModalRef);
  readonly disabledList: Set<number> = inject(NZ_MODAL_DATA).disabledList;
  publicProjectEntries: PublicProject[] = [];
  checked = false;
  indeterminate = false;
  checkedList = new Set<number>();
  constructor(private publicProjectService: PublicProjectService) {}

  ngOnInit(): void {
    this.publicProjectService
      .getPublicProjects()
      .pipe(untilDestroyed(this))
      .subscribe(publicProjects => (this.publicProjectEntries = publicProjects));
  }

  updateCheckedSet(id: number, checked: boolean): void {
    if (checked) {
      this.checkedList.add(id);
    } else {
      this.checkedList.delete(id);
    }
  }

  onItemChecked(id: number, checked: boolean): void {
    this.updateCheckedSet(id, checked);
    this.refreshCheckedStatus();
  }

  onAllChecked(value: boolean): void {
    this.publicProjectEntries.forEach(item => this.updateCheckedSet(item.pid, value));
    this.refreshCheckedStatus();
  }

  refreshCheckedStatus(): void {
    this.checked = this.publicProjectEntries.every(item => this.checkedList.has(item.pid));
    this.indeterminate = this.publicProjectEntries.some(item => this.checkedList.has(item.pid)) && !this.checked;
  }
  addPublicProjects(): void {
    this.publicProjectService
      .addPublicProjects(Array.from(this.checkedList))
      .pipe(untilDestroyed(this))
      .subscribe(() => this.modal.destroy());
  }
}
