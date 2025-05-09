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

import { CommentBox, OperatorLink, OperatorPredicate, Point } from "../../../types/workflow-common.interface";
import { VIEW_RESULT_OP_TYPE } from "./workflow-graph";

/**
 * Provides mock data related operators and links:
 *
 * Operators:
 *  - 1: ScanSource
 *  - 2: NlpSentiment
 *  - 3: ViewResults
 *  - 4: MultiInputOutputOperator
 *  - 5: PresetEnabledOperator
 *
 * Links:
 *  - link-1: ScanSource -> ViewResults
 *  - link-2: ScanSource -> NlpSentiment
 *  - link-3: NlpSentiment -> ScanSource
 *
 * Invalid links:
 *  - link-4: (no source port) -> NlpSentiment
 *  - link-5: (NlpSentiment) -> (no target port)
 *
 */

export const mockPoint: Point = {
  x: 100,
  y: 100,
};

export const mockScanPredicate: OperatorPredicate = {
  operatorID: "1",
  operatorType: "ScanSource",
  operatorVersion: "scan",
  operatorProperties: {},
  inputPorts: [],
  outputPorts: [{ portID: "output-0" }],
  showAdvanced: true,
  isDisabled: false,
};

export const mockSentimentPredicate: OperatorPredicate = {
  operatorID: "2",
  operatorType: "NlpSentiment",
  operatorVersion: "nlp1",
  operatorProperties: {},
  inputPorts: [{ portID: "input-0" }],
  outputPorts: [{ portID: "output-0" }],
  showAdvanced: true,
  isDisabled: false,
};

export const mockResultPredicate: OperatorPredicate = {
  operatorID: "3",
  operatorType: VIEW_RESULT_OP_TYPE,
  operatorVersion: "view1",
  operatorProperties: {},
  inputPorts: [{ portID: "input-0" }],
  outputPorts: [],
  showAdvanced: true,
  isDisabled: false,
};

export const mockMultiInputOutputPredicate: OperatorPredicate = {
  operatorID: "4",
  operatorType: "MultiInputOutput",
  operatorVersion: "m1",
  operatorProperties: {},
  inputPorts: [{ portID: "input-0" }, { portID: "input-1" }, { portID: "input-2" }],
  outputPorts: [{ portID: "output-0" }, { portID: "output-1" }, { portID: "output-2" }],
  showAdvanced: true,
  isDisabled: false,
};

export const mockPresetEnabledPredicate: OperatorPredicate = {
  operatorID: "5",
  operatorType: "PresetEnabledOp",
  operatorVersion: "p1",
  operatorProperties: {},
  inputPorts: [],
  outputPorts: [],
  showAdvanced: true,
};

export const mockJavaUDFPredicate: OperatorPredicate = {
  operatorID: "6",
  operatorType: "JavaUDF",
  operatorVersion: "p1",
  operatorProperties: {},
  inputPorts: [{ portID: "input-0" }],
  outputPorts: [{ portID: "output-0" }],
  showAdvanced: false,
  isDisabled: false,
};

export const mockPythonUDFPredicate: OperatorPredicate = {
  operatorID: "7",
  operatorType: "PythonUDF",
  operatorVersion: "p1",
  operatorProperties: {},
  inputPorts: [{ portID: "input-0" }],
  outputPorts: [{ portID: "output-0" }],
  showAdvanced: false,
  isDisabled: false,
};

export const mockScanResultLink: OperatorLink = {
  linkID: "link-1",
  source: {
    operatorID: mockScanPredicate.operatorID,
    portID: mockScanPredicate.outputPorts[0].portID,
  },
  target: {
    operatorID: mockResultPredicate.operatorID,
    portID: mockResultPredicate.inputPorts[0].portID,
  },
};

export const mockScanSentimentLink: OperatorLink = {
  linkID: "link-2",
  source: {
    operatorID: mockScanPredicate.operatorID,
    portID: mockScanPredicate.outputPorts[0].portID,
  },
  target: {
    operatorID: mockSentimentPredicate.operatorID,
    portID: mockSentimentPredicate.inputPorts[0].portID,
  },
};

export const mockSentimentResultLink: OperatorLink = {
  linkID: "link-3",
  source: {
    operatorID: mockSentimentPredicate.operatorID,
    portID: mockSentimentPredicate.outputPorts[0].portID,
  },
  target: {
    operatorID: mockResultPredicate.operatorID,
    portID: mockResultPredicate.inputPorts[0].portID,
  },
};

export const mockFalseResultSentimentLink: OperatorLink = {
  linkID: "link-4",
  source: {
    operatorID: mockResultPredicate.operatorID,
    portID: undefined as any,
  },
  target: {
    operatorID: mockSentimentPredicate.operatorID,
    portID: mockSentimentPredicate.inputPorts[0].portID,
  },
};

export const mockFalseSentimentScanLink: OperatorLink = {
  linkID: "link-5",
  source: {
    operatorID: mockSentimentPredicate.operatorID,
    portID: mockSentimentPredicate.outputPorts[0].portID,
  },
  target: {
    operatorID: mockScanPredicate.operatorID,
    portID: undefined as any,
  },
};

export const mockCommentBox: CommentBox = {
  commentBoxID: "1",
  comments: [],
  commentBoxPosition: mockPoint,
};
