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
"""Lo que hace falta para entender un flujo que uno no ha construido.

The panel started out answering "what did I change" and "why did my results
change", which are the questions of someone who already knows the workflow.
Whoever has just joined has two others: "how does this work" and "what has been
tried". This
modulo deriva ambas del mismo registro.

Dos salidas:

  orientation(wid)  the workflow as it stands today, step by step in the order
                    the data flows, with what each one does, how many rows it
                    moves, who has touched it and which settings are the ones
                    cambia de verdad.
  experiments(wid)  every run as a table of experiments: which dial was set to
                    what in each one, and what came out the other end.

The keys travel in English because this goes out over HTTP as it is.
"""
import json
import os

import autopsy
import core
import stats

# Above this, describing a value costs more than it gives: they are
# programas, consultas o tablas pegadas.
DESCRIBABLE = 600

# The panel does want them whole, because a user function's code is precisely
# the only thing that step does. It shows them folded, not on one line.
SHOWABLE = 4000

# Settings that say nothing about what a step does: form scaffolding or
# del reparto de trabajo.
# `envName` belongs here because nobody writes it: opening a user function's
# property panel leaves it empty and the next save removes it, so it shows up as
# the most edited setting in the workflow without being one.
SILENT_SETTINGS = {"dummyPropertyList", "defaultEnv", "workers", "columns",
                   "limit_dummy", "attributeType", "envName"}

# Half an hour of silence between two saves reads as having come back later.
SESSION_GAP = 30 * 60


def shape(value, key):
    """A property's raw value, when it is small enough to describe."""
    try:
        if len(json.dumps(value, ensure_ascii=False)) <= DESCRIBABLE:
            return {key: value}
    except (TypeError, ValueError):
        pass
    return {}


def settings_of(operator):
    """An operator's settings worth showing, uncut.

    They travel raw on purpose: the panel words them with the same JSON schema
    that draws the form, so it says "amount > 100" instead of dumping JSON.
    """
    out = []
    for key, value in (operator.get("operatorProperties") or {}).items():
        if key in SILENT_SETTINGS or value in ("", None, [], {}):
            continue
        text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False)
        entry = {"path": "/" + key, "lines": text.count("\n") + 1, "size": len(text)}
        if len(text) <= SHOWABLE:
            entry["value"] = value
        out.append(entry)
    return out


def leaves(value, prefix=""):
    """The leaves of a property tree, as {path: value}.

    A dial is a leaf: a filter's threshold, the column something groups by. A
    whole subtree is not, because changing it changes several at once.
    """
    out = {}
    if isinstance(value, dict):
        for key, sub in value.items():
            if key in SILENT_SETTINGS:
                continue
            out.update(leaves(sub, "%s/%s" % (prefix, key)))
    elif isinstance(value, list):
        for i, sub in enumerate(value):
            out.update(leaves(sub, "%s/%d" % (prefix, i)))
    else:
        out[prefix] = value
    return out


def noisy(path):
    """Paths that are not a setting anyone ever decided to touch."""
    return any(part in SILENT_SETTINGS for part in path.split("/") if part)


def props_of(state):
    """The leaves of every operator on a canvas: {oid: {path: value}}."""
    return {o["operatorID"]: leaves(o.get("operatorProperties") or {})
            for o in (state or {}).get("operators", [])}


# ---------- the workflow as it stands today ----------

def latest(hist):
    """The last rebuildable state, which is today's canvas."""
    for h in reversed(hist):
        if h["state"] is not None:
            return h
    return None


def wiring(state):
    """Who feeds whom, with the target port when there is more than one."""
    fed_by = {}
    feeds = {}
    for link in (state or {}).get("links", []):
        a = link["source"]["operatorID"]
        b = link["target"]["operatorID"]
        fed_by.setdefault(b, []).append({"id": a, "port": link["target"].get("portID", "")})
        feeds.setdefault(a, []).append(b)
    return fed_by, feeds


def activity(hist):
    """How many times each operator was touched, and when it last was.

    This is not the narrated log of the timeline: here it is only counted, so
    that one step can be called untouched for months and another daily.
    """
    touched = {}
    previous = None
    for h in hist:
        state = h["state"]
        if state is None:
            continue
        if previous is not None:
            was = {o["operatorID"]: o for o in previous.get("operators", [])}
            now = {o["operatorID"]: o for o in state.get("operators", [])}
            for oid in now:
                entry = touched.setdefault(oid, {"count": 0, "last": None, "born": h["time"],
                                                 "settings": {}})
                if oid not in was:
                    entry["born"] = h["time"]
                    entry["count"] += 1
                    entry["last"] = h["time"]
                    continue
                changed = False
                for path, before, after in autopsy.prop_diff(
                        was[oid].get("operatorProperties", {}),
                        now[oid].get("operatorProperties", {})):
                    entry["settings"][path] = entry["settings"].get(path, 0) + 1
                    changed = True
                if was[oid].get("customDisplayName") != now[oid].get("customDisplayName"):
                    changed = True
                if bool(was[oid].get("isDisabled")) != bool(now[oid].get("isDisabled")):
                    changed = True
                if changed:
                    entry["count"] += 1
                    entry["last"] = h["time"]
        previous = state
    return touched


def snapshot_of(wid, eid):
    path = "%s/wid_%d_eid_%d.json" % (autopsy.SNAPS, wid, eid)
    if not os.path.exists(path):
        return None
    try:
        return json.load(open(path))
    except Exception:
        return None


def sample_of(snapshot, oid, rows=3):
    """The first rows a step emitted, with its columns, if they were kept."""
    entry = ((snapshot or {}).get("operators") or {}).get(oid) or {}
    result = entry.get("result") or []
    clean = [{k: v for k, v in row.items() if k != "__row_index__"} for row in result[:rows]]
    columns = []
    for row in clean:
        for key in row:
            if key not in columns:
                columns.append(key)
    return columns, clean


def sessions_of(times):
    """How many sittings this was built in, counting the long silences."""
    stamps = []
    for value in times:
        try:
            from datetime import datetime
            stamps.append(datetime.fromisoformat(value))
        except (ValueError, TypeError):
            continue
    if not stamps:
        return 0
    count = 1
    for before, after in zip(stamps, stamps[1:]):
        if (after - before).total_seconds() > SESSION_GAP:
            count += 1
    return count


def orientation(wid):
    """The workflow explained to whoever did not build it, sources to outputs."""
    wf, hist, _origin, broken = core.history(wid)
    runs = core.executions(wid)
    frame = latest(hist)
    state = (frame or {}).get("state") or wf["content"]
    states_by_vid = {h["vid"]: h["state"] for h in hist}
    order = autopsy.topo(state)
    fed_by, feeds = wiring(state)
    touched = activity(hist)

    # The figures of the last attempt that completed: it is what someone
    # recien llegado vera si le da al play hoy.
    dirs = stats.table_dirs(wid)
    last_run = None
    volumes = {}
    for run in reversed(runs):
        found = stats.totals(wid, run["eid"], dirs.get(run["eid"]))
        if found:
            last_run, volumes = run, found
            break

    # The sample rows come from the most recent snapshot there is; it need not
    # be the one of the last run, because Texera drops results after thirty
    # seconds and this service only keeps its own.
    snapshot = None
    sampled = None
    for run in reversed(runs):
        snapshot = snapshot_of(wid, run["eid"])
        if snapshot:
            sampled = run
            break

    steps = []
    for position, oid in enumerate(order, start=1):
        operator = next((o for o in state.get("operators", []) if o["operatorID"] == oid), {})
        columns, sample = sample_of(snapshot, oid)
        seen = touched.get(oid) or {}
        volume = volumes.get(oid) or {}
        steps.append({
            "id": oid,
            "position": position,
            "name": operator.get("customDisplayName") or operator.get("operatorType", oid),
            "type": operator.get("operatorType", ""),
            "role": ("source" if not fed_by.get(oid) else
                     "output" if not feeds.get(oid) else "step"),
            "disabled": bool(operator.get("isDisabled")),
            "settings": settings_of(operator),
            "fedBy": fed_by.get(oid, []),
            "feeds": feeds.get(oid, []),
            "rowsIn": volume.get("in"),
            "rowsOut": volume.get("out"),
            "workers": volume.get("workers"),
            "seconds": volume.get("seconds"),
            "columns": columns,
            "sample": sample,
            "edits": seen.get("count", 0),
            "lastEdited": seen.get("last"),
            "addedOn": seen.get("born"),
        })

    # The dials: the settings somebody changed more than once. They are the map
    # of what gets touched in this workflow and what was left alone.
    knobs = []
    names = {s["id"]: s["name"] for s in steps}
    types = {s["id"]: s["type"] for s in steps}
    now = props_of(state)
    # A setting that differs between two runs is a dial even if it was touched
    # only once: it is the variable of an experiment somebody ran.
    tried = set()
    seen_values = {}
    for vid in dict.fromkeys(r["vid"] for r in runs):
        ran = states_by_vid.get(vid)
        if not ran:
            continue
        for oid, tree in props_of(ran).items():
            for path, value in tree.items():
                key = (oid, path)
                seen_values.setdefault(key, set()).add(
                    json.dumps(value, ensure_ascii=False, sort_keys=True)[:DESCRIBABLE])
    for key, values in seen_values.items():
        if len(values) > 1:
            tried.add(key)
    for oid, seen in touched.items():
        if oid not in names:
            continue
        for path, times in (seen.get("settings") or {}).items():
            if noisy(path):
                continue
            if times < 2 and (oid, path) not in tried:
                continue
            entry = {"stepId": oid, "step": names[oid], "type": types[oid],
                     "path": path, "times": times,
                     "tried": (oid, path) in tried}
            entry.update(shape(now.get(oid, {}).get(path), "value"))
            knobs.append(entry)
    # First what somebody really tried in a run, then what was edited most.
    knobs.sort(key=lambda k: (not k["tried"], -k["times"]))

    times = [h["time"] for h in hist]
    completed = [r for r in runs if r["status"] == 3]
    return {
        "wid": wid,
        "name": wf["name"],
        "description": wf["description"],
        "people": [{"name": p["name"], "access": p["privilege"], "owner": p["owner"]}
                   for p in core.people(wid)],
        "ranBy": sorted({r["who"] for r in runs if r["who"]}),
        "shape": {
            "sources": sum(1 for s in steps if s["role"] == "source"),
            "middle": sum(1 for s in steps if s["role"] == "step"),
            "outputs": sum(1 for s in steps if s["role"] == "output"),
        },
        "steps": steps,
        "knobs": knobs[:8],
        # Which attempt the sample rows come from. Worth saying: it may not be
        # the last one, and its settings may not be today's.
        "sampleFrom": ({"eid": sampled["eid"], "name": sampled["name"] or "",
                        "when": sampled["start"],
                        "current": sampled["vid"] == (frame or {}).get("vid")}
                       if sampled else None),
        "record": {
            "saves": len(hist),
            "sessions": sessions_of(times),
            "started": times[0] if times else None,
            "lastEdited": times[-1] if times else None,
            "runs": len(runs),
            "completed": len(completed),
            "lastRun": last_run["start"] if last_run else None,
            "lastRunName": (last_run.get("name") or "") if last_run else "",
            "unreadable": (len(hist) - sum(1 for h in hist if h["state"] is not None)),
            "broken": bool(broken),
        },
    }


# ---------- what has been tried already ----------

STATUS = {0: "ready", 1: "running", 2: "paused", 3: "completed",
          4: "failed", 5: "killed"}


def seconds_between(start, end):
    try:
        from datetime import datetime
        return round((datetime.fromisoformat(end) - datetime.fromisoformat(start)).total_seconds(), 1)
    except (ValueError, TypeError):
        return None


def experiments(wid):
    """Every run as what it is: an attempt with one dial set differently.

    What makes an old run comparable is not its results, which Texera drops
    after thirty seconds, but its statistics, which it keeps. That is where the
    rows of each step of each attempt come from.
    """
    wf, hist, _origin, _broken = core.history(wid)
    runs = core.executions(wid)
    states = {h["vid"]: h["state"] for h in hist}
    dirs = stats.table_dirs(wid)

    # Only the versions somebody ran from: comparing the settings of every
    # version would mix in edits nobody ever tried.
    ran_versions = [vid for vid in dict.fromkeys(r["vid"] for r in runs) if states.get(vid)]
    props = {vid: props_of(states[vid]) for vid in ran_versions}

    # A dial is a leaf whose value is not the same across every version that
    # was run. That is exactly the variable of an experiment.
    knobs = []
    everywhere = {}
    for vid in ran_versions:
        for oid, tree in props[vid].items():
            for path, value in tree.items():
                everywhere.setdefault((oid, path), {})[vid] = value
    last_state = states.get(ran_versions[-1]) if ran_versions else None
    names = {o["operatorID"]: (o.get("customDisplayName") or o.get("operatorType", ""))
             for o in (last_state or {}).get("operators", [])}
    types = {o["operatorID"]: o.get("operatorType", "")
             for o in (last_state or {}).get("operators", [])}
    for (oid, path), by_version in everywhere.items():
        values = [json.dumps(v, ensure_ascii=False, sort_keys=True) for v in by_version.values()]
        if len(set(values)) < 2:
            continue
        # A huge value is not a dial anyone reads off a table.
        if max(len(v) for v in values) > DESCRIBABLE:
            continue
        knobs.append({"id": "%s%s" % (oid, path), "stepId": oid,
                      "step": names.get(oid, oid), "type": types.get(oid, ""),
                      "path": path})
    knobs.sort(key=lambda k: (k["step"], k["path"]))

    out = []
    for run in runs:
        state = states.get(run["vid"])
        totals = stats.totals(wid, run["eid"], dirs.get(run["eid"]))
        _fed_by, feeds = wiring(state)
        sinks = [o["operatorID"] for o in (state or {}).get("operators", [])
                 if not feeds.get(o["operatorID"])]
        sources = [o["operatorID"] for o in (state or {}).get("operators", [])
                   if not _fed_by.get(o["operatorID"])]
        tree = props.get(run["vid"]) or props_of(state)
        settings = {}
        for knob in knobs:
            value = tree.get(knob["stepId"], {}).get(knob["path"])
            settings.update(shape(value, knob["id"]))
        out.append({
            "eid": run["eid"],
            "vid": run["vid"],
            "name": run["name"] or "",
            "who": run["who"] or "",
            "started": run["start"],
            "ended": run["end"],
            "seconds": seconds_between(run["start"], run["end"]),
            "status": STATUS.get(run["status"], str(run["status"])),
            "snapshot": snapshot_of(wid, run["eid"]) is not None,
            "measured": bool(totals),
            "steps": {oid: {"in": v["in"], "out": v["out"], "workers": v["workers"],
                            "seconds": v["seconds"]}
                      for oid, v in totals.items()},
            "readIn": sum((totals.get(oid) or {}).get("out", 0) for oid in sources),
            "produced": sum((totals.get(oid) or {}).get("out", 0) for oid in sinks),
            "settings": settings,
            "canvasSteps": len((state or {}).get("operators", [])),
        })

    # What changed from one attempt to the next, so the table reads as a
    # sequence and not as a loose list.
    moves = []
    for before, after in zip(out, out[1:]):
        if before["vid"] == after["vid"]:
            continue
        edits = []
        for knob in knobs:
            was = before["settings"].get(knob["id"])
            now = after["settings"].get(knob["id"])
            if json.dumps(was, sort_keys=True) != json.dumps(now, sort_keys=True):
                edits.append({"knob": knob["id"], "step": knob["step"], "stepId": knob["stepId"],
                              "type": knob["type"], "path": knob["path"],
                              "before": was, "after": now})
        # Entre dos intentos puede no haberse tocado ninguna perilla y aun asi
        # have changed the workflow: a new step, a deleted one, a rename.
        was = {o["operatorID"]: o for o in (states.get(before["vid"]) or {}).get("operators", [])}
        now = {o["operatorID"]: o for o in (states.get(after["vid"]) or {}).get("operators", [])}
        structure = []
        for oid in now:
            if oid not in was:
                structure.append({"kind": "added", "stepId": oid,
                                  "step": now[oid].get("customDisplayName") or now[oid].get("operatorType", oid)})
        for oid in was:
            if oid not in now:
                structure.append({"kind": "removed", "stepId": oid,
                                  "step": was[oid].get("customDisplayName") or was[oid].get("operatorType", oid)})
        for oid in set(was) & set(now):
            if was[oid].get("customDisplayName") != now[oid].get("customDisplayName"):
                structure.append({"kind": "renamed", "stepId": oid,
                                  "step": now[oid].get("customDisplayName") or oid,
                                  "before": was[oid].get("customDisplayName") or ""})
            if bool(was[oid].get("isDisabled")) != bool(now[oid].get("isDisabled")):
                structure.append({"kind": "disabled" if now[oid].get("isDisabled") else "enabled",
                                  "stepId": oid,
                                  "step": now[oid].get("customDisplayName") or oid})
        moves.append({"from": before["eid"], "to": after["eid"], "edits": edits,
                      "structure": structure,
                      "producedBefore": before["produced"], "producedAfter": after["produced"]})

    return {"wid": wid, "name": wf["name"], "knobs": knobs, "runs": out, "moves": moves}


if __name__ == "__main__":
    import sys
    what = sys.argv[1]
    wid = int(sys.argv[2])
    print(json.dumps(orientation(wid) if what == "brief" else experiments(wid),
                     indent=1, ensure_ascii=False, default=str))
