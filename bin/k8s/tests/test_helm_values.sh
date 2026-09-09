#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Every `.Values.x.y` a template reads must exist in values.yaml.
#
# Helm does not fail on a missing value, it renders an empty string -- so a chart with a
# typo, or one whose template was added without its values, installs and then misbehaves
# at runtime. A missing block is worse: `nil pointer evaluating interface {}` aborts the
# render, which at least fails loudly, but only if someone runs `helm template` first.
#
# Needs no helm and no cluster, so it runs anywhere the repo does.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHART_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

python3 - "$CHART_DIR" <<'PY'
import glob
import os
import re
import sys

chart_dir = sys.argv[1]

try:
    import yaml
except ImportError:
    print("SKIP: pyyaml not installed")
    sys.exit(0)

with open(os.path.join(chart_dir, "values.yaml"), encoding="utf-8") as handle:
    values = yaml.safe_load(handle)

# Optional by design: read only inside an `if eq .Values....type "NodePort"` guard, so a
# deployment that does not use NodePort services leaves them unset.
ALLOWED_ABSENT = {
    "fileService.service.nodePort",
    "webserver.service.nodePort",
    "workflowCompilingService.service.nodePort",
    "workflowComputingUnitManager.service.nodePort",
}

references = set()
pattern = re.compile(r"\.Values\.([A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)*)")
for path in glob.glob(os.path.join(chart_dir, "templates", "**", "*.yaml"), recursive=True):
    with open(path, encoding="utf-8") as handle:
        references.update(pattern.findall(handle.read()))

missing = []
for reference in sorted(references):
    if reference in ALLOWED_ABSENT:
        continue
    node = values
    for part in reference.split("."):
        if isinstance(node, dict) and part in node:
            node = node[part]
        else:
            missing.append(reference)
            break

if missing:
    print(f"FAIL: {len(missing)} template value(s) missing from values.yaml:")
    for reference in missing:
        print(f"  .Values.{reference}")
    sys.exit(1)

print(f"PASS: all {len(references)} template value references exist in values.yaml")
PY
