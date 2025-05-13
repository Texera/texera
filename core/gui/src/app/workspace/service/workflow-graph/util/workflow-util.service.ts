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

import {
  OperatorPredicate,
  CommentBox,
  Comment,
  Point,
  PortDescription,
} from "../../../types/workflow-common.interface";
import { OperatorMetadataService } from "../../operator-metadata/operator-metadata.service";
import { InputPortInfo, OperatorSchema, OutputPortInfo } from "../../../types/operator-schema.interface";
import { Injectable } from "@angular/core";
import { v4 as uuid } from "uuid";
import Ajv from "ajv";

import { Observable, Subject } from "rxjs";
import { Workflow, WorkflowContent } from "../../../../common/type/workflow";
import { jsonCast } from "../../../../common/util/storage";

/**
 * WorkflowUtilService provide utilities related to dealing with operator data.
 */
@Injectable({
  providedIn: "root",
})
export class WorkflowUtilService {
  private operatorSchemaList: ReadonlyArray<OperatorSchema> = [];

  // used to fetch default values in json schema to initialize new operator
  private ajv = new Ajv({ useDefaults: true, strict: false });

  private operatorSchemaListCreatedSubject: Subject<boolean> = new Subject<boolean>();

  constructor(private operatorMetadataService: OperatorMetadataService) {
    this.operatorMetadataService.getOperatorMetadata().subscribe(value => {
      this.operatorSchemaList = value.operators;
      this.operatorSchemaListCreatedSubject.next(true);
    });
  }

  public getOperatorSchemaListCreatedStream(): Observable<boolean> {
    return this.operatorSchemaListCreatedSubject.asObservable();
  }

  /**
   * Generates a new UUID for operator
   */
  public getOperatorRandomUUID(): string {
    return "operator-" + uuid();
  }

  /**
   * Generates a new UUID for link
   */
  public getLinkRandomUUID(): string {
    return "link-" + uuid();
  }

  /**
   * Generates a new UUID for group element
   */
  public getGroupRandomUUID(): string {
    return "group-" + uuid();
  }

  /**
   * Generates a new UUID for breakpoint
   */
  public getBreakpointRandomUUID(): string {
    return "breakpoint-" + uuid();
  }

  public getCommentBoxRandomUUID(): string {
    return "commentBox-" + uuid();
  }

  // TODO: change this to drag-and-drop
  public getNewCommentBox(): CommentBox {
    const commentBoxID = this.getCommentBoxRandomUUID();
    const comments: Comment[] = [];
    const commentBoxPosition: Point = { x: 500, y: 20 };
    return { commentBoxID, comments, commentBoxPosition };
  }

  /**
   * This method will use a unique ID and a operatorType to create and return a
   * new OperatorPredicate with default initial properties.
   *
   * @param operatorType type of an Operator
   * @returns a new OperatorPredicate of the operatorType
   */
  public getNewOperatorPredicate(operatorType: string): OperatorPredicate {
    const operatorSchema = this.operatorSchemaList.find(schema => schema.operatorType === operatorType);
    if (operatorSchema === undefined) {
      throw new Error(`operatorType ${operatorType} doesn't exist in operator metadata`);
    }

    const operatorId = operatorSchema.operatorType + "-" + this.getOperatorRandomUUID();
    const operatorProperties = {};

    // Remove the ID field for the schema to prevent warning messages from Ajv
    const { ...schemaWithoutId } = operatorSchema.jsonSchema;

    // value inserted in the data will be the deep clone of the default in the schema
    const validate = this.ajv.compile(schemaWithoutId);
    validate(operatorProperties);

    const inputPorts: PortDescription[] = [];
    const outputPorts: PortDescription[] = [];

    // by default, the operator will not show advanced option in the properties to the user
    const showAdvanced = false;

    // by default, the operator is not disabled
    const isDisabled = false;

    // by default, the operator name is the user friendly name
    const customDisplayName = operatorSchema.additionalMetadata.userFriendlyName;

    const dynamicInputPorts = operatorSchema.additionalMetadata.dynamicInputPorts ?? false;
    const dynamicOutputPorts = operatorSchema.additionalMetadata.dynamicOutputPorts ?? false;

    for (let i = 0; i < operatorSchema.additionalMetadata.inputPorts.length; i++) {
      const portID = "input-" + i.toString();
      const portInfo = operatorSchema.additionalMetadata.inputPorts[i];
      inputPorts.push(WorkflowUtilService.inputPortToPortDescription(portID, portInfo));
    }

    for (let i = 0; i < operatorSchema.additionalMetadata.outputPorts.length; i++) {
      const portID = "output-" + i.toString();
      const portInfo = operatorSchema.additionalMetadata.outputPorts[i];
      outputPorts.push(WorkflowUtilService.outputPortToPortDescription(portID, portInfo));
    }

    const operatorVersion = operatorSchema.operatorVersion;

    return {
      operatorID: operatorId,
      operatorType,
      operatorVersion,
      operatorProperties,
      inputPorts,
      outputPorts,
      showAdvanced,
      isDisabled,
      customDisplayName,
      dynamicInputPorts,
      dynamicOutputPorts,
    };
  }

  /**
   * helper function to parse WorkflowInfo from a JSON string. In some case, for example reading from backend,
   * the content would return as a JSON string.
   * @param workflow
   */
  public static parseWorkflowInfo(workflow: Workflow): Workflow {
    if (workflow != null && typeof workflow.content === "string") {
      workflow.content = jsonCast<WorkflowContent>(workflow.content);
    }
    return workflow;
  }

  private static inputPortToPortDescription(portID: string, inputPortInfo: InputPortInfo): PortDescription {
    return {
      portID,
      displayName: inputPortInfo.displayName ?? "",
      allowMultiInputs: inputPortInfo.allowMultiLinks ?? false,
      isDynamicPort: false,
      dependencies: inputPortInfo.dependencies ?? [],
    };
  }

  private static outputPortToPortDescription(portID: string, outputPortInfo: OutputPortInfo): PortDescription {
    return {
      portID,
      displayName: outputPortInfo.displayName ?? "",
      allowMultiInputs: false,
      isDynamicPort: false,
    };
  }

  public updateOperatorVersion(op: OperatorPredicate) {
    const operatorType = op.operatorType;
    const operatorSchema = this.operatorSchemaList.find(schema => schema.operatorType === operatorType);
    if (operatorSchema === undefined) {
      throw new Error(`operatorType ${operatorType} doesn't exist in operator metadata`);
    }

    let inputPorts: PortDescription[] = [];
    if (op.inputPorts.length === 0) {
      // use the operatorSchema as a template to create ports
      for (let i = 0; i < operatorSchema.additionalMetadata.inputPorts.length; i++) {
        const portID = "input-" + i.toString();
        const portInfo = operatorSchema.additionalMetadata.inputPorts[i];
        inputPorts.push(WorkflowUtilService.inputPortToPortDescription(portID, portInfo));
      }
    } else {
      inputPorts = op.inputPorts;
    }

    let outputPorts: PortDescription[] = [];
    if (op.outputPorts.length === 0) {
      // use the operatorSchema as a template to create ports
      for (let i = 0; i < operatorSchema.additionalMetadata.outputPorts.length; i++) {
        const portID = "output-" + i.toString();
        const portInfo = operatorSchema.additionalMetadata.outputPorts[i];
        outputPorts.push(WorkflowUtilService.outputPortToPortDescription(portID, portInfo));
      }
    } else {
      outputPorts = op.outputPorts;
    }

    return {
      ...op,
      operatorVersion: operatorSchema.operatorVersion,
      inputPorts,
      outputPorts,
    };
  }
}
