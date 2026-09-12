<!--
  ~ Licensed to the Apache Software Foundation (ASF) under one
  ~ or more contributor license agreements.  See the NOTICE file
  ~ distributed with this work for additional information
  ~ regarding copyright ownership.  The ASF licenses this file
  ~ to you under the Apache License, Version 2.0 (the
  ~ "License"); you may not use this file except in compliance
  ~ with the License.  You may obtain a copy of the License at
  ~
  ~   http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing,
  ~ software distributed under the License is distributed on an
  ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  ~ KIND, either express or implied.  See the License for the
  ~ specific language governing permissions and limitations
  ~ under the License.
-->

# spy-service

Serves the workflow panel in the GUI with what Texera already records about a
workflow and does not show: every save, and every run.

Nothing here is new data. `workflow_version` keeps one inverse JSON patch per
save, and the runtime statistics of every run stay in Iceberg long after the
results themselves are dropped. This service reads both and answers questions
about them.

## What each module does

| Module | What it does |
| --- | --- |
| `core.py` | Rebuilds a workflow's whole history from the inverse patches in `workflow_version`. Tolerates a patch that removes what is already gone, and reports where that happened. |
| `autopsy.py` | Compares two runs operator by operator in topological order and names the edit behind the first difference. |
| `brief.py` | Today's workflow step by step, and every run read as an experiment. |
| `stats.py` | Reads the Iceberg runtime statistics of a run: rows in and out of each step, workers, time. Needs `pyarrow`. |
| `server.py` | The HTTP boundary. Everything it returns is in English, keys and text alike. |

## Running it

```bash
python3 spy-service/server.py
```

It listens on `127.0.0.1:5055` and reads the database directly, so it holds no
Texera session or token of its own. The one route that writes, `POST
/api/spy/run`, carries the token of the user who has Texera open, so whoever
cannot run a workflow themselves cannot run it from here either.

`stats.py` needs `pyarrow`; without it the panel still works and simply shows no
per-step row counts for older runs.

## Configuration

Every setting is an environment variable with the development bundle's default,
so a standard checkout needs none of them.

| Variable | Default | What it is |
| --- | --- | --- |
| `SPY_PORT` | `5055` | Port to listen on. |
| `SPY_PG_HOST` / `SPY_PG_PORT` | `127.0.0.1` / `5432` | Where PostgreSQL is. |
| `SPY_PG_USER` / `SPY_PG_PASSWORD` | `texera` / empty | Credentials to read with. |
| `SPY_PG_DATABASE` / `SPY_PG_SCHEMA` | `texera_db` | Database and schema. |
| `SPY_ICEBERG_CATALOG` | `texera_iceberg_catalog` | Catalog holding the runtime statistics. |
| `SPY_TEXERA_ROOT` | this checkout | Root the catalog's relative paths resolve against. |
| `SPY_SNAPSHOTS` | `spy-service/snapshots` | Where rows kept from a run are written. See the note below. |
| `SPY_LITELLM` | `http://127.0.0.1:4000/v1/chat/completions` | LiteLLM endpoint for the written explanations. |
| `SPY_LITELLM_ENV` | unset | File to read `LITELLM_MASTER_KEY` from, when it is not in the environment. |
| `SPY_MODEL` / `SPY_NARRATOR_MODEL` | `claude-haiku-4.5` | Models that write the prose. |

Without a model the panel loses only its sentences: every count, every state and
the edit to blame are derived by the rules and shown either way.

## Open question: where a kept run should live

A run whose rows the user wants to compare is written here as one JSON file per
run under `SPY_SNAPSHOTS`. That is the weakest part of this service and is meant
to change before this is anything but a draft.

The rows do not need copying at all. When a run finishes they are already in an
Iceberg table; the reason they cannot be compared later is that
`WorkflowService.clearExecutionResources` drops them once the workflow has been
idle for `executionStateCleanUpInSecs`, thirty seconds by default. Keeping a run
for comparison is therefore not a write but a flag: mark the execution as
retained, and let the cleanup skip it the way it already skips per-user
warehouses through `WarehouseReadGuard.skipWhileDisabled`.

That would drop this directory entirely, keep the workflow's own access control
in force, and stop the service needing a disk of its own. It also needs a
retention policy, since nothing would otherwise ever free the space.
