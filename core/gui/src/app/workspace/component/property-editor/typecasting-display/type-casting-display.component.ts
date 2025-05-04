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

import { Component, Input, OnChanges, OnInit } from "@angular/core";
import { WorkflowActionService } from "src/app/workspace/service/workflow-graph/model/workflow-action.service";
import { WorkflowCompilingService } from "../../../service/compile-workflow/workflow-compiling.service";
import { filter, map } from "rxjs/operators";
import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { AttributeType, SchemaAttribute } from "../../../types/workflow-compiling.interface";

// correspond to operator type specified in backend OperatorDescriptor
export const TYPE_CASTING_OPERATOR_TYPE = "TypeCasting";

@UntilDestroy()
@Component({
  selector: "texera-type-casting-display",
  templateUrl: "./type-casting-display.component.html",
})
export class TypeCastingDisplayComponent implements OnInit, OnChanges {
  @Input() currentOperatorId: string | undefined;

  schemaToDisplay: Partial<SchemaAttribute>[] = [];
  displayTypeCastingSchemaInformation: boolean = false;

  constructor(
    private workflowActionService: WorkflowActionService,
    private workflowCompilingService: WorkflowCompilingService
  ) {}

  ngOnInit(): void {
    this.registerTypeCastingPropertyChangeHandler();
    this.registerInputSchemaChangeHandler();
  }

  // invoke on first init and every time the input binding is changed
  ngOnChanges(): void {
    if (!this.currentOperatorId) {
      this.displayTypeCastingSchemaInformation = false;
      return;
    }
    const op = this.workflowActionService.getTexeraGraph().getOperator(this.currentOperatorId);
    if (op.operatorType !== TYPE_CASTING_OPERATOR_TYPE) {
      this.displayTypeCastingSchemaInformation = false;
      return;
    }
    this.displayTypeCastingSchemaInformation = true;
    this.rerender();
  }

  registerTypeCastingPropertyChangeHandler(): void {
    this.workflowActionService
      .getTexeraGraph()
      .getOperatorPropertyChangeStream()
      .pipe(
        filter(op => op.operator.operatorID === this.currentOperatorId),
        filter(op => op.operator.operatorType === TYPE_CASTING_OPERATOR_TYPE),
        map(event => event.operator)
      )
      .pipe(untilDestroyed(this))
      .subscribe(_ => {
        this.rerender();
      });
  }

  private registerInputSchemaChangeHandler() {
    this.workflowCompilingService
      .getCompilationStateInfoChangedStream()
      .pipe(untilDestroyed(this))
      .subscribe(_ => {
        this.rerender();
      });
  }

  rerender(): void {
    if (!this.currentOperatorId) {
      return;
    }
    this.schemaToDisplay = [];
    const inputSchema = this.workflowCompilingService.getOperatorInputSchema(this.currentOperatorId);

    const operatorPredicate = this.workflowActionService.getTexeraGraph().getOperator(this.currentOperatorId);

    const castUnits: ReadonlyArray<{ attribute: string; resultType: AttributeType }> =
      operatorPredicate.operatorProperties["typeCastingUnits"] ?? [];

    const castTypeMap: Map<string, AttributeType> = new Map(castUnits.map(unit => [unit.attribute, unit.resultType]));
    inputSchema?.forEach(schema =>
      schema?.forEach(attr => {
        if (castTypeMap.has(attr.attributeName)) {
          const castedAttr: Partial<SchemaAttribute> = {
            attributeName: attr.attributeName,
            attributeType: castTypeMap.get(attr.attributeName),
          };
          this.schemaToDisplay.push(castedAttr);
        } else {
          this.schemaToDisplay.push(attr);
        }
      })
    );
  }
}
