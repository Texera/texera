#!/usr/bin/env python3
#
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
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
"""Las cifras que Texera si conserva de cada ejecucion.

A run's results are dropped after thirty seconds, but its runtime statistics
are not: they stay in one Iceberg table per run, with a row per operator per
sample. That is where the rows in and out of each step come from, how many
workers served it and how long it took. It turns any old run into something
that can still be compared, even when its data is long gone.

The figures in each sample are cumulative, not increments: an operator's total
is the maximum of its column, never the sum. Checked against the panel's own
snapshots, operator by operator.
"""
import json
import os
import subprocess

import core

# The catalog's paths are relative to the repository root, which is the working
# directory of the services when they write.
# The checkout this service reads its configuration from. Defaults to the
# repository this file lives in, so a normal checkout needs no setting at all.
ROOT = os.environ.get("SPY_TEXERA_ROOT", os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CATALOG = ["psql", "-h", core.HOST, "-p", core.PORT, "-U", core.USER,
           "-d", os.environ.get("SPY_ICEBERG_CATALOG", "texera_iceberg_catalog"), "-tAc"]
ENV = dict(core.ENV)
NAMESPACE = "workflow-runtime-statistics"


def _catalog(sql):
    out = subprocess.run(CATALOG + [sql], capture_output=True, text=True, env=ENV)
    if out.returncode != 0:
        return []
    return [l for l in out.stdout.split("\n") if l != ""]


def table_dir(wid, eid):
    """The directory of the Iceberg table holding one run's statistics."""
    name = "wid_%d_eid_%d_runtimestatistics" % (wid, eid)
    rows = _catalog("select metadata_location from iceberg_tables "
                    "where table_namespace='%s' and table_name='%s'" % (NAMESPACE, name))
    if not rows:
        return None
    location = rows[0]
    if location.startswith("file:"):
        location = location.split("file:", 1)[1].lstrip("/")
        location = "/" + location
    path = location if os.path.isabs(location) else os.path.join(ROOT, location)
    # .../<tabla>/metadata/00001-....metadata.json -> .../<tabla>
    return os.path.dirname(os.path.dirname(path))


def table_dirs(wid):
    """The directories of every run of a workflow, in a single query.

    One query per run shows when a workflow has twenty of them, and the view
    de experimentos las pide todas de golpe.
    """
    rows = _catalog("select table_name, metadata_location from iceberg_tables "
                    "where table_namespace='%s' and table_name like 'wid\\_%d\\_eid\\_%%'"
                    % (NAMESPACE, wid))
    out = {}
    for row in rows:
        name, _, location = row.partition("|")
        parts = name.split("_")
        try:
            eid = int(parts[parts.index("eid") + 1])
        except (ValueError, IndexError):
            continue
        path = location if os.path.isabs(location) else os.path.join(ROOT, location)
        out[eid] = os.path.dirname(os.path.dirname(path))
    return out


def _parquet_files(directory):
    if not directory or not os.path.isdir(directory):
        return []
    out = []
    for entry in sorted(os.listdir(directory)):
        if entry.startswith(".") or entry == "metadata":
            continue
        full = os.path.join(directory, entry)
        if os.path.isfile(full):
            out.append(full)
    return out


def totals(wid, eid, directory=None):
    """Per operator: rows in and out, workers, and processing time.

    Returns {} when there are no statistics or when pyarrow is missing: this
    enriches the view and must never bring it down.
    """
    try:
        import pyarrow.parquet as pq
    except ImportError:
        return {}
    files = _parquet_files(directory or table_dir(wid, eid))
    if not files:
        return {}
    per = {}
    first = last = None
    for path in files:
        try:
            rows = pq.read_table(path).to_pylist()
        except Exception:
            continue
        for r in rows:
            oid = r.get("operatorId")
            if not oid:
                continue
            acc = per.setdefault(oid, {"in": 0, "out": 0, "workers": 0,
                                       "nanos": 0, "idle": 0, "status": None})
            acc["in"] = max(acc["in"], r.get("inputTupleCnt") or 0)
            acc["out"] = max(acc["out"], r.get("outputTupleCnt") or 0)
            acc["workers"] = max(acc["workers"], r.get("numWorkers") or 0)
            acc["nanos"] = max(acc["nanos"], r.get("dataProcessingTime") or 0)
            acc["idle"] = max(acc["idle"], r.get("idleTime") or 0)
            acc["status"] = r.get("status")
            when = r.get("time")
            if when is not None:
                first = when if first is None or when < first else first
                last = when if last is None or when > last else last
    for acc in per.values():
        # El motor cuenta en nanosegundos; nadie lee nanosegundos.
        acc["seconds"] = round(acc.pop("nanos") / 1e9, 3)
        acc.pop("idle", None)
    if first is not None and last is not None:
        for acc in per.values():
            acc["elapsed"] = round((last - first).total_seconds(), 3)
    return per


if __name__ == "__main__":
    import sys
    wid, eid = int(sys.argv[1]), int(sys.argv[2])
    print(json.dumps(totals(wid, eid), indent=1, default=str))
