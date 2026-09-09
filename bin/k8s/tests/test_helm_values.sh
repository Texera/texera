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
# Scans every rendered file under templates/ -- `_helpers.tpl` included, since that is
# where the shared naming logic lives -- and reads both the `.Values.a.b` and the
# `index .Values "a" "b"` accessors. References rooted at a subchart are checked only
# when that subchart has been vendored (`helm dependency build`), because otherwise its
# defaults are not on disk to check against; the run reports how many it left alone.
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
    # Exiting 0 here would make this suite a green no-op the moment the dependency
    # stops being installed -- a missing chart value would then merge unnoticed,
    # which is worse than not having the check at all. Fail instead, loudly.
    print(
        "FAIL: PyYAML is required by this check but is not installed.\n"
        "  Install it with: python -m pip install -r amber/dev-requirements.txt",
        file=sys.stderr,
    )
    sys.exit(1)


def load_yaml(path):
    with open(path, encoding="utf-8") as handle:
        return yaml.safe_load(handle) or {}


values = load_yaml(os.path.join(chart_dir, "values.yaml"))

# Subcharts own their own defaults. Helm renders `.Values.<dep>.x` against
# merge(subchart values.yaml, parent values.yaml), so a parent template may legitimately
# read a key this chart never overrides -- resolving those against values.yaml alone
# reports valid references as missing. If the dependencies have been vendored
# (`helm dependency build` leaves them under charts/) their defaults are merged in below
# and the references are checked for real; otherwise they are counted as unverifiable
# rather than failed.
chart = load_yaml(os.path.join(chart_dir, "Chart.yaml"))
subchart_roots = {}
for dependency in chart.get("dependencies") or []:
    name = dependency.get("name")
    if not name:
        continue
    # The values key is the alias when one is set; the vendored directory is always
    # named after the chart itself.
    subchart_roots[dependency.get("alias") or name] = name


def merge_defaults(base, defaults):
    """Overlay `base` onto `defaults`, the way Helm merges parent values over a subchart's."""
    if base is None:
        # The parent leaves this key alone, so the subchart's default is what renders.
        return defaults
    if not isinstance(base, dict) or not isinstance(defaults, dict):
        return base
    merged = dict(defaults)
    for key, value in base.items():
        merged[key] = merge_defaults(value, merged.get(key))
    return merged


unverifiable_roots = set()
for root, name in subchart_roots.items():
    vendored = os.path.join(chart_dir, "charts", name, "values.yaml")
    if os.path.isfile(vendored):
        values[root] = merge_defaults(values.get(root), load_yaml(vendored))
    else:
        unverifiable_roots.add(root)

# Optional by design: read only inside an `if eq .Values....type "NodePort"` guard, so a
# deployment that does not use NodePort services leaves them unset.
ALLOWED_ABSENT = {
    "fileService.service.nodePort",
    "webserver.service.nodePort",
    "workflowCompilingService.service.nodePort",
    "workflowComputingUnitManager.service.nodePort",
}

# Helm renders everything under templates/ except what .helmignore drops, so scan by
# exclusion rather than by extension -- an extension allowlist silently skipped
# _helpers.tpl, where the shared naming logic lives, and would skip NOTES.txt too.
# Mirrors .helmignore: editor and OS leftovers are not rendered, and a stale *.bak copy
# of a template would otherwise contribute references the chart no longer has.
IGNORED_SUFFIXES = (".md", ".bak", ".tmp", ".orig", ".swp", "~")

# `.Values.a.b` covers the dotted form; `index .Values "a" "b"` is the accessor Helm
# needs for keys a dotted path cannot express (hyphens) and reads the same values, so
# both have to be collected or a rename slips through the gap between them.
DOTTED = re.compile(r"\.Values\.([A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)*)")
INDEXED = re.compile(r"index\s+\$?\.Values\s+((?:\"[^\"]*\"\s*)+)")
QUOTED = re.compile(r"\"([^\"]*)\"")

references = set()
scanned = 0
for directory, _, filenames in os.walk(os.path.join(chart_dir, "templates")):
    for filename in sorted(filenames):
        if filename.startswith(".") or filename.endswith(IGNORED_SUFFIXES):
            continue
        scanned += 1
        with open(os.path.join(directory, filename), encoding="utf-8") as handle:
            text = handle.read()
        references.update(DOTTED.findall(text))
        references.update(".".join(QUOTED.findall(match)) for match in INDEXED.findall(text))


def resolve(reference):
    node = values
    for part in reference.split("."):
        if isinstance(node, dict) and part in node:
            node = node[part]
        else:
            return False
    return True


missing = []
checked = 0
skipped = []
for reference in sorted(references):
    if reference in ALLOWED_ABSENT:
        continue
    if reference.split(".")[0] in unverifiable_roots:
        skipped.append(reference)
        continue
    checked += 1
    if not resolve(reference):
        missing.append(reference)

# An exemption that no template reads any more is dead weight that would go on
# suppressing a real failure if the name were ever reused.
stale_exemptions = sorted(ALLOWED_ABSENT - references)

if missing or stale_exemptions:
    if missing:
        print(f"FAIL: {len(missing)} template value(s) missing from values.yaml:")
        for reference in missing:
            print(f"  .Values.{reference}")
    if stale_exemptions:
        print(f"FAIL: {len(stale_exemptions)} ALLOWED_ABSENT entr(y/ies) no template reads:")
        for reference in stale_exemptions:
            print(f"  {reference}")
    sys.exit(1)

print(
    f"PASS: all {checked} checked template value references exist in values.yaml "
    f"({scanned} template file(s); {len(ALLOWED_ABSENT)} exempt)"
)
if skipped:
    print(
        f"NOTE: {len(skipped)} reference(s) under subchart(s) "
        f"{', '.join(sorted(unverifiable_roots))} not checked -- their defaults live in the "
        "subchart. Run `helm dependency build` in the chart dir to have them checked too."
    )
PY
