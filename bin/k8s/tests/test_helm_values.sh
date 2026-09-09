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
# Every value a template reads must exist in values.yaml, or be listed as deliberately
# absent below.
#
# Helm does not fail on a missing value, it renders an empty string -- so a chart with a
# typo, or one whose template was added without its values, installs and then misbehaves
# at runtime with nothing pointing at the cause.
#
# Needs no helm and no cluster, so it runs anywhere the repo does.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHART_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

python3 - "$CHART_DIR" <<'PY'
import os
import re
import sys

chart_dir = sys.argv[1]

try:
    import yaml
except ImportError:
    # Skipping here would turn this suite into a green no-op the moment the dependency
    # goes missing, which is worse than not having the check.
    print(
        "FAIL: PyYAML is required by this check but is not installed.\n"
        "  Install it with: python -m pip install -r amber/dev-requirements.txt",
        file=sys.stderr,
    )
    sys.exit(1)

with open(os.path.join(chart_dir, "values.yaml"), encoding="utf-8") as handle:
    values = yaml.safe_load(handle) or {}

# Read by a template but deliberately not defined here: a value behind a guard a default
# deployment never enters, or a subchart default this chart inherits. Every entry needs
# its reason -- an unexplained one is indistinguishable from a suppressed break.
ALLOWED_ABSENT = {
    # Read only inside `if eq .Values....type "NodePort"`, so a deployment that does not
    # use NodePort services leaves them unset.
    "fileService.service.nodePort",
    "webserver.service.nodePort",
    "workflowCompilingService.service.nodePort",
    "workflowComputingUnitManager.service.nodePort",
}

# Helm renders everything under templates/ except what .helmignore drops, so scan by
# exclusion: an extension allowlist skips _helpers.tpl, where the shared naming logic
# lives, and a stale *.bak would contribute references the chart no longer has.
IGNORED_SUFFIXES = (".md", ".bak", ".tmp", ".orig", ".swp", "~")

# Both accessors reach the same values; `index` is the only form for keys a dotted path
# cannot express.
DOTTED = re.compile(r"\.Values\.([A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)*)")
INDEXED = re.compile(r"index\s+\$?\.Values\s+((?:\"[^\"]*\"\s*)+)")
QUOTED = re.compile(r"\"([^\"]*)\"")
DOTTABLE = re.compile(r"[A-Za-z0-9_]+\Z")

# Held as tuples of key segments: a key may itself contain a dot, so joining and
# re-splitting on "." would take `index .Values "a" "b.c"` apart at the wrong place.
references = set()
scanned = 0
templates_dir = os.path.join(chart_dir, "templates")
for directory, _, filenames in os.walk(templates_dir):
    for filename in sorted(filenames):
        if filename.startswith(".") or filename.endswith(IGNORED_SUFFIXES):
            continue
        scanned += 1
        with open(os.path.join(directory, filename), encoding="utf-8") as handle:
            text = handle.read()
        references.update(tuple(match.split(".")) for match in DOTTED.findall(text))
        references.update(tuple(QUOTED.findall(match)) for match in INDEXED.findall(text))

if not scanned:
    # Otherwise a moved or renamed templates/ reports "0 references, all fine".
    print(f"FAIL: no template files found under {templates_dir}", file=sys.stderr)
    sys.exit(1)


def render(reference):
    if all(DOTTABLE.match(part) for part in reference):
        return ".Values." + ".".join(reference)
    return "index .Values " + " ".join(f'"{part}"' for part in reference)


def resolve(reference):
    node = values
    for part in reference:
        if not isinstance(node, dict) or part not in node:
            return False
        node = node[part]
    return True


allowed = {tuple(reference.split(".")) for reference in ALLOWED_ABSENT}
missing = sorted(r for r in references if r not in allowed and not resolve(r))
# An exemption no template reads any more would go on suppressing a real break if the
# name were reused.
stale = sorted(allowed - references)

if missing or stale:
    if missing:
        print(f"FAIL: {len(missing)} template value(s) missing from values.yaml:")
        for reference in missing:
            print(f"  {render(reference)}")
        print("  Define each in values.yaml, or add it to ALLOWED_ABSENT with the reason.")
    if stale:
        print(f"FAIL: {len(stale)} ALLOWED_ABSENT entr(y/ies) no template reads:")
        for reference in stale:
            print(f"  {render(reference)}")
    sys.exit(1)

print(
    f"PASS: {len(references) - len(allowed)} template value reference(s) checked "
    f"across {scanned} file(s); {len(allowed)} exempt"
)
PY
