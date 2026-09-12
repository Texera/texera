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
"""Servidor del boton espia: sirve a la interfaz de Texera lo que el producto
guarda en silencio sobre un flujo.

Routes. Every one of them reads, except the last, which runs:

  GET /api/spy/brief?wid=N          today's workflow, step by step, for a newcomer
  GET /api/spy/tour?wid=N           the same, told: one sentence per step
  GET /api/spy/history?wid=N        the timeline: one frame per version
  GET /api/spy/executions?wid=N     the workflow's runs, with or without a snapshot
  GET /api/spy/experiments?wid=N    every run as a table of experiments
  GET /api/spy/narrate?wid=N&a=&b=  the comparison, told: one sentence per step
  GET /api/spy/autopsy?wid=N&a=&b=  the comparison: where and why two runs diverge
  GET /api/spy/report?wid=N         the whole handover document, written in one go
  POST /api/spy/ask                 a question about what the open view is showing
  POST /api/spy/run                 runs the workflow and keeps the rows for comparison

It leans on core.py (rebuilding the history), autopsy.py (comparing results),
brief.py (today's workflow and its experiments) and stats.py (the statistics
Texera does keep for every run). It reads the database directly, so it needs no
Texera session or token of its own.

This file is the boundary with the interface: everything that goes out over
HTTP is in English, keys and text alike.
"""
import json
import os
import sys
import traceback
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import brief
import core
import autopsy
import run_workflow

PORT = int(os.environ.get("SPY_PORT", "5055"))

# Status codes exactly as Utils.maptoStatusCode writes them into the table.
STATUS = {0: "ready", 1: "running", 2: "paused", 3: "completed",
          4: "failed", 5: "killed"}

# An operator's properties can carry a whole program inside.
# For the timeline a fragment is enough.
CLIP = 160


def clip(v):
    # An empty string would print as nothing and the change would look
    # half-written, so it is shown quoted.
    if v == "":
        return '""'
    s = v if isinstance(v, str) else json.dumps(v, ensure_ascii=False)
    s = s.replace("\n", "⏎ ")
    return s if len(s) <= CLIP else s[:CLIP] + "…"


# Which values are small enough to describe and which settings are form
# scaffolding: brief.py decides, since it is the one that reads the whole canvas
# for the orientation view.
DESCRIBABLE = brief.DESCRIBABLE
SILENT_SETTINGS = brief.SILENT_SETTINGS

# What the interface writes by itself, not a person. It is a shorter list than
# SILENT_SETTINGS on purpose: there it is decided what does not deserve
# describing, and here what does not deserve accusing. A user function's declared
# columns, for instance, are not described because they are long, but changing
# them does move the data, and the comparison has to be able to name them.
MACHINE_WRITTEN = {"envName", "dummyPropertyList", "limit_dummy", "defaultEnv"}


def machine_written(path):
    """Whether a property path is one the interface writes on its own."""
    return any(part in MACHINE_WRITTEN for part in path.split("/") if part)
shape = brief.shape


# ---------- la pelicula ----------

def canvas(state):
    """Just enough to draw the graph: operators with their position, and links."""
    pos = state.get("operatorPositions", {}) or {}
    operators = []
    for o in state.get("operators", []):
        oid = o["operatorID"]
        p = pos.get(oid) or {}
        operators.append({
            "id": oid,
            "type": o.get("operatorType", ""),
            "name": o.get("customDisplayName") or o.get("operatorType", ""),
            "x": p.get("x", 0),
            "y": p.get("y", 0),
            "disabled": bool(o.get("isDisabled")),
            # The names of the inputs, so a link can be said to arrive at one
            # of them when the operator has more than one.
            "inputs": [p.get("displayName") or "" for p in o.get("inputPorts", [])],
        })
    links = [{"from": l["source"]["operatorID"], "to": l["target"]["operatorID"]}
             for l in state.get("links", [])]
    return {"operators": operators, "links": links}


def changes(before, after):
    """What the user did between two consecutive states of the canvas."""
    if before is None or after is None:
        return []
    out = []
    was = {o["operatorID"]: o for o in before.get("operators", [])}
    now = {o["operatorID"]: o for o in after.get("operators", [])}

    # Every change carries the raw data as well as the text: the interface uses
    # it to write the sentence with the names the user sees on screen.
    for oid in sorted(now):
        if oid not in was:
            out.append({"kind": "operator_added", "operatorId": oid,
                        "operatorType": now[oid].get("operatorType", ""),
                        "name": now[oid].get("customDisplayName") or "",
                        "text": "added %s (%s)" % (oid, now[oid].get("operatorType", ""))})
    for oid in sorted(was):
        if oid not in now:
            out.append({"kind": "operator_deleted", "operatorId": oid,
                        "operatorType": was[oid].get("operatorType", ""),
                        "name": was[oid].get("customDisplayName") or "",
                        "text": "deleted %s (%s)" % (oid, was[oid].get("operatorType", ""))})

    def ends(l):
        # The target port tells apart a join's two inputs, which on the canvas
        # are different things even though the operator is the same.
        return (l["source"]["operatorID"], l["target"]["operatorID"],
                l["target"].get("portID", ""))

    old_links = {ends(l) for l in before.get("links", [])}
    new_links = {ends(l) for l in after.get("links", [])}
    for a, b, port in sorted(new_links - old_links):
        out.append({"kind": "link_added", "from": a, "to": b, "port": port,
                    "text": "connected %s → %s" % (a, b)})
    for a, b, port in sorted(old_links - new_links):
        out.append({"kind": "link_removed", "from": a, "to": b, "port": port,
                    "text": "disconnected %s → %s" % (a, b)})

    for oid in sorted(set(was) & set(now)):
        pa = was[oid].get("operatorProperties", {})
        pb = now[oid].get("operatorProperties", {})
        for path, va, vb in autopsy.prop_diff(pa, pb):
            change = {"kind": "property", "operatorId": oid, "path": path,
                      "operatorType": now[oid].get("operatorType", ""),
                      "before": clip(va), "after": clip(vb),
                      "text": "%s%s: %s → %s" % (oid, path, clip(va), clip(vb))}
            # The clipped text is fine to read but not to describe: the
            # interface needs the value as it is to say "sum of amount" instead
            # of dumping the JSON. It only travels when small, because a
            # propiedad puede traer un programa entero dentro.
            change.update(shape(va, "beforeValue"))
            change.update(shape(vb, "afterValue"))
            out.append(change)

        na, nb = was[oid].get("customDisplayName"), now[oid].get("customDisplayName")
        if na != nb:
            out.append({"kind": "renamed", "operatorId": oid,
                        "before": na, "after": nb,
                        "text": "renamed %s: %s → %s" % (oid, na, nb)})

        if bool(was[oid].get("isDisabled")) != bool(now[oid].get("isDisabled")):
            verb = "disabled" if now[oid].get("isDisabled") else "re-enabled"
            out.append({"kind": "disabled", "operatorId": oid,
                        "text": "%s %s" % (verb, oid)})

    pa = before.get("operatorPositions", {}) or {}
    pb = after.get("operatorPositions", {}) or {}
    for oid in sorted(set(pa) & set(pb)):
        if pa[oid] != pb[oid]:
            out.append({"kind": "moved", "operatorId": oid,
                        "text": "moved %s on the canvas" % oid})
    return out


def history(wid):
    wf, hist, origin, broken = core.history(wid)

    runs_by_version = {}
    for e in core.executions(wid):
        runs_by_version.setdefault(e["vid"], []).append(
            {"eid": e["eid"], "status": STATUS.get(e["status"], str(e["status"])),
             "started": e["start"]})

    frames = []
    previous = None
    for h in hist:
        st = h["state"]
        frame = {"vid": h["vid"], "time": h["time"], "broken": h["broken"],
                 "recoverable": st is not None,
                 # Texera asked to remove something that was already gone. The
                 # state is still the one its own patch describes, but say so.
                 "tolerated": len(h.get("tolerated") or []),
                 "runs": runs_by_version.get(h["vid"], [])}
        if st is not None:
            frame.update(canvas(st))
            frame["changes"] = changes(previous, st)
        else:
            frame.update({"operators": [], "links": [], "changes": []})
        frames.append(frame)
        previous = st

    return {
        "wid": wid,
        "name": wf["name"],
        "description": wf["description"],
        "total": len(frames),
        "recovered": sum(1 for f in frames if f["recoverable"]),
        "tolerated": sum(f["tolerated"] for f in frames),
        "broken": ({"vid": broken["vid"], "error": broken["error"]} if broken else None),
        # With the chain broken the rebuilder runs out of state, and that None
        # does not mean having reached the empty canvas at the start.
        "reachesOrigin": broken is None and origin in ({}, None),
        "frames": frames,
    }


# ---------- la autopsia ----------

def executions(wid):
    runs = []
    for e in core.executions(wid):
        path = "%s/wid_%d_eid_%d.json" % (autopsy.SNAPS, wid, e["eid"])
        snapshot = os.path.exists(path)
        name = None
        rows = 0
        if snapshot:
            try:
                s = json.load(open(path))
                name = s.get("name")
                rows = sum(len(v.get("result") or [])
                           for v in (s.get("operators") or {}).values())
            except Exception:
                snapshot = False
        runs.append({"eid": e["eid"], "vid": e["vid"],
                     "status": STATUS.get(e["status"], str(e["status"])),
                     "started": e["start"], "ended": e["end"],
                     "snapshot": snapshot, "name": name, "rows": rows})
    return {"wid": wid, "runs": runs}


def autopsy_report(wid, eid_a, eid_b):
    """Puts the report autopsy.py returns into the shape the interface reads."""
    r = autopsy.autopsy(wid, eid_a, eid_b)
    findings = []
    for h in r["hallazgos"]:
        d = h["diff"]
        findings.append({
            "operatorId": h["operatorID"],
            "rowsA": h["filas_a"], "rowsB": h["filas_b"], "truncated": h["cortada"],
            "diff": None if d is None else {
                "rowsA": d["filas_a"], "rowsB": d["filas_b"],
                "onlyInA": d["solo_en_a"], "onlyInB": d["solo_en_b"],
                "sameSetDifferentOrder": d["mismo_conjunto_distinto_orden"],
                # The engine cuts the output at 100,000 characters per operator
                # and keeps head and tail. When that happens the counts are still
                # good, but the sample rows do not prove absence.
                "sampleA": d["muestra_a"], "sampleB": d["muestra_b"],
                "sampleTruncated": d["muestra_cortada"],
            },
        })
    # What each step does, not only how many rows it emits: the small properties
    # of the later version, so the panel can say "amount > 400" or
    # "sum of amount, as total" en vez de dejar la caja muda. Las grandes
    # (a UDF's code, a pasted query) are left out on purpose.
    props = autopsy.operator_props(r.get("lienzo") or {})
    names = {o["operatorID"]: (o.get("customDisplayName") or o.get("operatorType", ""))
             for o in (r.get("lienzo") or {}).get("operators", [])}
    types = {o["operatorID"]: o.get("operatorType", "")
             for o in (r.get("lienzo") or {}).get("operators", [])}
    steps = []
    for oid in r["orden"]:
        settings = []
        for key, value in (props.get(oid) or {}).items():
            if key in SILENT_SETTINGS or value in ("", None, [], {}):
                continue
            described = shape(value, "value")
            if described:
                settings.append({"path": "/" + key, "value": described["value"]})
        steps.append({"operatorId": oid, "name": names.get(oid, oid),
                      "type": types.get(oid, ""), "settings": settings})

    return {
        "wid": r["wid"],
        "name": r["name"],
        "steps": steps,
        "a": {"eid": r["a"]["eid"], "vid": r["a"]["vid"], "name": r["a"]["name"]},
        "b": {"eid": r["b"]["eid"], "vid": r["b"]["vid"], "name": r["b"]["name"]},
        "order": r["orden"],
        "findings": findings,
        "firstDivergent": r["primer_divergente"],
        # `envName` shows up because opening a user function's property panel
        # writes it empty, and the next save removes it. Announcing that as the
        # edit between two runs would accuse a gesture nobody made, so it drops
        # out along with the rest of the form's scaffolding.
        "edits": [{"operatorId": e["operatorID"], "path": e["path"],
                   "before": e["antes"], "after": e["despues"]}
                  for e in r["ediciones"] if not machine_written(e["path"])],
    }


# ---------- la voz ----------
#
# A model writes, it never decides. It is handed the facts core.py and
# autopsy.py already derived and puts them into prose; which operator diverges
# and which edit caused it are worked out here, not asked. With no key, or on a
# timeout, or on a failure, the panel keeps its own text and nobody notices.

LITELLM = os.environ.get("SPY_LITELLM", "http://127.0.0.1:4000/v1/chat/completions")
MODEL = os.environ.get("SPY_MODEL", "claude-haiku-4.5")
# The step-by-step narration is what a person reads first, so it goes through
# the fast model: five seconds, and the panel never waits on it. With
# claude-sonnet-5 the same request spends all 4000 output tokens and LiteLLM
# returns empty content, so changing the model means raising the budget.
NARRATOR = os.environ.get("SPY_NARRATOR_MODEL", "claude-haiku-4.5")
NARRATION_BUDGET = int(os.environ.get("SPY_NARRATION_BUDGET", "6000"))
ENV_FILE = os.environ.get("SPY_LITELLM_ENV", "")
TIMEOUT = int(os.environ.get("SPY_LLM_TIMEOUT", "40"))
NARRATION_TIMEOUT = int(os.environ.get("SPY_NARRATION_TIMEOUT", "120"))

# Reopening the panel should not cost money all over again.
_said = {}


def master_key():
    key = os.environ.get("LITELLM_MASTER_KEY")
    if key:
        return key
    try:
        for line in open(ENV_FILE):
            if line.startswith("LITELLM_MASTER_KEY="):
                return line.split("=", 1)[1].strip()
    except OSError:
        pass
    return ""


def ask(system, user, model=None, timeout=None, budget=None):
    """One call, one answer. No tools and no conversation."""
    return converse(system, [{"role": "user", "content": user}], model, timeout, budget)


def converse(system, messages, model=None, timeout=None, budget=None):
    """The same, but carrying the previous turns when questions are being asked."""
    body = json.dumps({
        "model": model or MODEL,
        # Wide on purpose: the model reasons before writing and with a small
        # budget it spends the lot thinking, returning half a sentence.
        "max_tokens": budget or 4000,
        "messages": [{"role": "system", "content": system}] + messages,
    }).encode()
    req = urllib.request.Request(LITELLM, data=body, headers={
        "Content-Type": "application/json",
        "Authorization": "Bearer %s" % master_key(),
    })
    with urllib.request.urlopen(req, timeout=timeout or TIMEOUT) as r:
        answer = json.load(r)
    return (answer["choices"][0]["message"]["content"] or "").strip()


VOICE = """You write for someone who runs data workflows in Texera and does not program.

You are given facts that were derived from the workflow's own saved record. Your job \
is to put those facts into plain prose, and to draw out what they mean for the person's \
data. You are not the one who works out what changed or what caused what: that is \
already done and handed to you.

Rules you do not break:
- Use only the facts given. Never invent an operator, a number, a column or a version.
- Never assert a cause the facts do not state. If something is unexplained, say so.
- Never add a unit, a currency or a label the data does not carry. An amount of 377 \
is "377", not "377 dollars". Copy a threshold exactly: above 400 is not 400 or more.
- Use the canvas names, never the short internal ids. If a fact gives you an id \
such as "flt", find its canvas name and use that instead.
- No preamble, no headings, no bullet lists, no markdown. Plain sentences.
- Short words. Say "rows", not "records". Say "dropped", not "excluded"."""


def names_at(wid, vid=None):
    """The canvas names, which are the only ones a person recognises."""
    record = history(wid)
    frames = [f for f in record["frames"] if f["recoverable"]]
    frame = next((f for f in frames if f["vid"] == vid), None) or (frames[-1] if frames else None)
    return {o["id"]: o["name"] for o in (frame or {}).get("operators", [])}


def named(value, names):
    """Sustituye identificadores por nombres en claves, listas y cadenas sueltas."""
    if isinstance(value, dict):
        return {names.get(k, k): named(v, names) for k, v in value.items()}
    if isinstance(value, list):
        return [named(v, names) for v in value]
    if isinstance(value, str):
        return names.get(value, value)
    return value


def facts_autopsy(wid, eid_a, eid_b):
    report = autopsy_report(wid, eid_a, eid_b)
    first = report["firstDivergent"]
    rows = {f["operatorId"]: f["diff"] for f in report["findings"] if f["diff"]}
    names = names_at(wid, report["b"]["vid"])
    return {
        "workflow": report["name"],
        "the two runs": {"earlier": report["a"], "later": report["b"]},
        "order the data flows": named(report["order"], names),
        "where they first disagree": named(first, names),
        "row counts and sample rows per step": named(rows, names),
        "how to read those rows": "rowsA and rowsB are the true counts, as the engine reported "
                                  "them. onlyInA and onlyInB are drawn from a sample: when "
                                  "sampleTruncated is true the engine returned only part of that "
                                  "step's output, so a row listed on one side may still exist on "
                                  "the other. Never say a row is missing from a truncated step, "
                                  "and never say counts fail to add up because a sample is short.",
        "what was edited in between": [
            {"step": names.get(e["operatorId"], e["operatorId"]), "setting": e["path"],
             "before": e["before"], "after": e["after"]}
            for e in report["edits"]
        ],
    }


def explain_autopsy(wid, eid_a, eid_b):
    facts = facts_autopsy(wid, eid_a, eid_b)
    task = """Here are the facts about two runs of the same workflow.

Write two short paragraphs.

First: what the edit actually did to the data. Not that the numbers changed, but what \
kind of rows stopped coming through and what that does to every step after it. Use the \
sample rows to say what was lost in concrete terms.

Second: what each version is good for. Neither is simply better: say what the earlier \
run answers well, what the later one answers well, and which question each one suits. \
If the facts do not support a trade-off, say plainly that the change only narrows the \
data and leaves the rest alone.

FACTS:
""" + json.dumps(facts, ensure_ascii=False, indent=1)
    return ask(VOICE, task)


def facts_history(wid):
    record = history(wid)
    steps = [{"version": f["vid"], "when": f["time"],
              "changes": [c["text"] for c in f["changes"]]}
             for f in record["frames"] if f["changes"]]
    return {"workflow": record["name"], "versions": record["total"],
             "unreadable from": record["broken"],
             # The vid is a global counter on the table, shared by every
             # workflow. Without this note the model reads the gaps as lost
             # saves, which is exactly what they are not.
             "note on version numbers": "Version numbers come from a counter shared by every "
                                        "workflow in Texera, so gaps between them are normal and "
                                        "mean nothing. Never say saves are missing because of a gap.",
             "what each step is called on the canvas today": names_at(wid),
             "the edits in order": steps}


def explain_history(wid):
    facts = facts_history(wid)
    task = """Here is the full edit history of one workflow, oldest first.

Write one short paragraph telling the story of how it was built: what the person set out \
to do, where they changed their mind, and what the workflow ended up doing. Name the \
turning points, not every save. If part of the record is unreadable, say so at the end.

FACTS:
""" + json.dumps(facts, ensure_ascii=False, indent=1)
    return ask(VOICE, task)


ASKED = """You are answering a question about the record above.

Answer in two or three sentences. No preamble, no headings, no lists.

Your first sentence is your answer. Everything after it supports that sentence and \
never walks it back. If you find yourself about to write "however", the first sentence \
was wrong: fix it instead.

If the facts do not settle the question, say so in the first sentence and then say \
what would settle it. Never guess a cause, a number or a column. If the question is \
about something the record simply does not hold, such as money, people or why someone \
made a decision, say that the record does not say."""


# A sane cap: the box is for questions, not for pasting documents.
MAX_QUESTION = 500


def answer(wid, question, eid_a=None, eid_b=None, previous=None, mode=None):
    question = (question or "").strip()[:MAX_QUESTION]
    if not question:
        raise ValueError("empty question")
    # Each view asks about what it has in front of it: the comparison of two
    # runs, today's workflow, the table of experiments, or the history.
    if mode == "brief":
        facts = facts_brief(wid)
    elif mode == "experiments":
        facts = facts_experiments(wid)
    elif eid_a and eid_b:
        facts = facts_autopsy(wid, eid_a, eid_b)
    else:
        facts = facts_history(wid)
    messages = [{"role": "user", "content": "FACTS:\n" + json.dumps(facts, ensure_ascii=False, indent=1)},
                {"role": "assistant", "content": "Understood. Ask me about this record."}]
    for turn in (previous or [])[-3:]:
        if turn.get("question") and turn.get("answer"):
            messages.append({"role": "user", "content": turn["question"][:MAX_QUESTION]})
            messages.append({"role": "assistant", "content": turn["answer"]})
    messages.append({"role": "user", "content": question})
    return {"wid": wid, "model": MODEL, "text": converse(VOICE + "\n\n" + ASKED, messages)}


# ---------- what happened, step by step ----------
# The difference between showing figures and explaining: the rules say where the
# divergence starts and how many rows moved, but leave the person to translate
# that into their own problem. Here the model writes that translation, one
# sentence per step, over the same derived facts. It decides nothing: the step to
# blame, the counts and the edits were all settled before it was asked.

NARRATION = """Here are the facts about two runs of the same workflow, and what each \
step of it does.

Reply with JSON and nothing else, in this shape:

{"headline": "...", "steps": {"<step name>": "...", ...}, "takeaway": "..."}

headline: one sentence a person reads first, saying what changed about their results \
and why. Name the step that caused it and what it now does differently.

steps: one short sentence for EVERY step listed in "order the data flows", using the \
canvas name as the key. Say what that step does to the data and what is different \
between the two runs at that point, in the terms of the data itself: which rows, which \
values, which groups. For a step whose output is identical, say what it does and that \
it is untouched. For a step that returns the same rows in a different order, say that \
nothing about the data changed there. Never write a bare number without saying what it \
counts.

takeaway: one sentence saying which run answers which question, or, if the change only \
narrows or regroups the data, say that plainly.

A step's name is a label the person typed and it can be out of date: what the step \
actually does is in its settings and in the list of edits, never in its name. If a name \
no longer matches what the step does, say so in that step's sentence. Never say one step \
was replaced by another: every step in the list exists in both runs.

Every sentence must be readable by someone who does not know what a join or an \
aggregation is. No jargon, no markdown, no quotes around names.

Keep every sentence under 35 words. Never copy the facts back: write about them.

FACTS:
"""


def fence_free(text):
    """The model sometimes returns the JSON inside a code fence."""
    t = (text or "").strip()
    if t.startswith("```"):
        t = t.split("\n", 1)[-1]
        t = t.rsplit("```", 1)[0]
    return t.strip()


def narration_facts(wid, eid_a, eid_b):
    """One card per step, in the order the data flows.

    facts_autopsy is enough to write a paragraph, but it leaves out the counts
    of the steps that agree, and telling the story step by step then forces the
    model to invent the figures it is missing. Here every step carries its own:
    what it does, how many rows it emitted in each run, and how the comparison
    came out.
    """
    report = autopsy_report(wid, eid_a, eid_b)
    names = names_at(wid, report["b"]["vid"])
    found = {f["operatorId"]: f for f in report["findings"]}
    settings = {st["operatorId"]: {x["path"].lstrip("/"): x["value"] for x in st["settings"]}
                for st in report["steps"]}
    first = report["firstDivergent"]

    steps = []
    for position, oid in enumerate(report["order"], start=1):
        f = found.get(oid) or {}
        d = f.get("diff")
        if not d:
            verdict = "identical in both runs"
        elif d.get("sameSetDifferentOrder"):
            verdict = "same rows, only in a different order, which is not a change in the data"
        elif oid == first:
            verdict = "the first step whose data is genuinely different: the difference is born here"
        else:
            verdict = "different, but only because it is fed by a step that changed earlier"
        entry = {
            "position in the flow": position,
            "step": names.get(oid, oid),
            "what it is set to do": settings.get(oid) or "nothing worth reporting",
            "rows out in the earlier run": f.get("rowsA"),
            "rows out in the later run": f.get("rowsB"),
            "verdict": verdict,
        }
        if d and not d.get("sameSetDifferentOrder"):
            entry["rows only in the earlier run"] = d.get("onlyInA")
            entry["rows only in the later run"] = d.get("onlyInB")
            if d.get("sampleTruncated"):
                entry["warning"] = ("the engine returned only part of this step's output, so these "
                                    "sample rows do not prove a row is absent from the other run")
        steps.append(entry)

    return {
        "workflow": report["name"],
        "the two runs": {"earlier": report["a"], "later": report["b"]},
        "what was edited in between": [
            {"step": names.get(e["operatorId"], e["operatorId"]), "setting": e["path"],
             "before": e["before"], "after": e["after"]}
            for e in report["edits"]
        ],
        "how to read this": ("The steps are listed in the order the data flows through them. A step "
                             "only ever sees what the steps before it produced, so a step cannot "
                             "reduce or change rows it never received. The row counts are the true "
                             "ones reported by the engine."),
        "the steps": steps,
    }


def narrate_autopsy(wid, eid_a, eid_b):
    report = autopsy_report(wid, eid_a, eid_b)
    names = names_at(wid, report["b"]["vid"])
    facts = narration_facts(wid, eid_a, eid_b)
    raw = ask(VOICE, NARRATION + json.dumps(facts, ensure_ascii=False, indent=1),
              NARRATOR, NARRATION_TIMEOUT, NARRATION_BUDGET)
    try:
        told = json.loads(fence_free(raw))
    except (ValueError, TypeError):
        return {"headline": "", "steps": {}, "takeaway": "", "model": NARRATOR}
    # The sentences come back keyed by canvas name; the panel looks them up by
    # identifier, so they are translated back here.
    back = {v: k for k, v in names.items()}
    steps = {back.get(k, k): v for k, v in (told.get("steps") or {}).items()
             if isinstance(v, str)}
    return {"headline": str(told.get("headline") or ""),
            "steps": steps,
            "takeaway": str(told.get("takeaway") or ""),
            "model": NARRATOR}


_told = {}


def narrate(wid, eid_a, eid_b):
    key = (wid, eid_a, eid_b)
    if key not in _told:
        _told[key] = narrate_autopsy(wid, eid_a, eid_b)
    return dict(_told[key], wid=wid)


def explain(wid, eid_a=None, eid_b=None):
    key = (wid, eid_a, eid_b)
    if key not in _said:
        text = explain_autopsy(wid, eid_a, eid_b) if eid_a and eid_b else explain_history(wid)
        _said[key] = text
    return {"wid": wid, "model": MODEL, "text": _said[key]}


# ---------- el flujo explicado a quien acaba de llegar ----------
#
# The two original views answer the questions of someone who already knows the
# workflow. Whoever joins needs something else first: what this does, step by
# step, and what has been tried. brief.py derives the facts; here they are put
# into words under the same rule as always, the model writes and does not decide.

def told_setting(setting):
    """A setting as the model gets it: whole when short, measured when not."""
    value = setting.get("value")
    text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False, default=str)
    if value is None or len(text) > DESCRIBABLE:
        return "%d lines of it, too long to show here" % setting.get("lines", 0)
    return value


def compact_steps(orientation):
    """The steps as the model gets them: canvas names, no identifiers."""
    steps = []
    for step in orientation["steps"]:
        entry = {
            "position in the flow": step["position"],
            "step": step["name"],
            "kind of step": step["type"],
            # The model does not need a user function's whole program, and
            # paying for it on every step adds up: its size is enough.
            "what it is set to do": {s["path"].lstrip("/"): told_setting(s)
                                     for s in step["settings"]} or "nothing worth reporting",
            "where its data comes from": [orientation_name(orientation, f["id"]) for f in step["fedBy"]] or "it reads data itself, nothing feeds it",
            "where its data goes": [orientation_name(orientation, f) for f in step["feeds"]] or "nothing: this is where the flow ends",
            "rows it last produced": step["rowsOut"],
            "columns it produces": step["columns"] or "not recorded",
            "times it has been edited": step["edits"],
        }
        if step["sample"]:
            entry["a few of its rows"] = step["sample"]
            entry["where those rows came from"] = (
                "the run named %s, which %s the settings the workflow has today"
                % ((orientation.get("sampleFrom") or {}).get("name") or "unnamed",
                   "used" if (orientation.get("sampleFrom") or {}).get("current") else "did NOT use"))
        if step["disabled"]:
            entry["switched off"] = True
        steps.append(entry)
    return steps


def orientation_name(orientation, oid):
    for step in orientation["steps"]:
        if step["id"] == oid:
            return step["name"]
    return oid


def facts_brief(wid):
    o = brief.orientation(wid)
    return {
        "workflow": o["name"],
        "its description, written by whoever built it": o["description"] or "none",
        "who can open it": [p["name"] + (" (owner)" if p["owner"] else "") for p in o["people"]],
        "who has run it": o["ranBy"],
        "its shape": o["shape"],
        "the record": o["record"],
        "settings that have been changed more than once": [
            {"step": k["step"], "setting": k["path"], "changed": k["times"], "value now": k.get("value")}
            for k in o["knobs"]],
        "the steps, in the order the data flows": compact_steps(o),
        "how to read this": ("Row counts come from the engine's own statistics for the last run that "
                             "was measured. A step with no rows recorded simply was not measured, "
                             "which is not the same as producing nothing."),
    }


TOUR = """Here are the facts about a workflow. Someone has just joined the team and has \
never seen it. They are about to open it and they do not know what it does.

Reply with JSON and nothing else, in this shape:

{"headline": "...", "purpose": "...", "steps": {"<step name>": "...", ...}, "watchOut": "..."}

headline: one sentence saying what question this workflow answers, in the terms of \
whoever needs the answer, not in the terms of the software.

purpose: two or three sentences on what goes in, what comes out, and what happens in \
between. Say what the data is about, judging only by its columns and the rows you were \
given. Never name a unit, a currency or a meaning the data does not carry.

steps: one sentence for EVERY step listed, using the step name as the key. Say what that \
step does to the data in the terms of the data itself: which rows it keeps, what it \
groups, what it adds. Read its settings, never its name: a name is a label someone typed \
and it can be out of date. If a step's name no longer matches what it does, say so.

watchOut: one or two sentences on what a newcomer should be careful with here, drawn \
only from the facts: a setting that keeps being changed, a step that is switched off, a \
step that produces nothing, part of the record that cannot be read. If there is nothing \
to warn about, say what is worth knowing instead.

Keep every sentence under 35 words. No jargon, no markdown, no bullet lists.

FACTS:
"""


_toured = {}


def tour(wid):
    """The guided tour: what the workflow does, step by step, for a newcomer."""
    if wid in _toured:
        return dict(_toured[wid], wid=wid)
    o = brief.orientation(wid)
    facts = facts_brief(wid)
    told = {"headline": "", "purpose": "", "steps": {}, "watchOut": "", "model": NARRATOR}
    try:
        raw = ask(VOICE, TOUR + json.dumps(facts, ensure_ascii=False, indent=1),
                  NARRATOR, NARRATION_TIMEOUT, NARRATION_BUDGET)
        parsed = json.loads(fence_free(raw))
    except (ValueError, TypeError, OSError):
        parsed = None
    if parsed:
        back = {step["name"]: step["id"] for step in o["steps"]}
        told = {
            "headline": str(parsed.get("headline") or ""),
            "purpose": str(parsed.get("purpose") or ""),
            "steps": {back.get(k, k): v for k, v in (parsed.get("steps") or {}).items()
                      if isinstance(v, str)},
            "watchOut": str(parsed.get("watchOut") or ""),
            "model": NARRATOR,
        }
        _toured[wid] = told
    return dict(told, wid=wid)


def facts_experiments(wid):
    e = brief.experiments(wid)
    labels = {k["id"]: "%s, %s" % (k["step"], k["path"].lstrip("/")) for k in e["knobs"]}
    runs = []
    for run in e["runs"]:
        runs.append({
            "run": run["name"] or "unnamed",
            "number": run["eid"],
            "when": run["started"],
            "who ran it": run["who"],
            "how it ended": run["status"],
            "seconds it took": run["seconds"],
            "settings that differ between runs": {labels.get(k, k): v for k, v in run["settings"].items()}
                                                  or "none: every run used the same settings",
            "rows read in": run["readIn"],
            "rows it ended up producing": run["produced"],
            "rows out of each step": {k: v["out"] for k, v in run["steps"].items()},
        })
    return {
        "workflow": e["name"],
        "the settings that were tried at different values": [labels[k["id"]] for k in e["knobs"]]
                                                             or "none",
        "the runs, oldest first": runs,
        "what changed between one run and the next": [
            {"from run": m["from"], "to run": m["to"],
             "settings changed": [{"setting": labels.get(x["knob"], x["knob"]),
                                   "before": x["before"], "after": x["after"]} for x in m["edits"]],
             "steps added or removed": m["structure"],
             "rows produced before": m["producedBefore"],
             "rows produced after": m["producedAfter"]}
            for m in e["moves"]],
        "how to read this": ("The row counts come from the engine's own statistics, which Texera keeps "
                             "for every run, unlike the results themselves. Two runs with the same "
                             "settings and the same counts are the same experiment run twice."),
    }


# ---------- el informe ----------
#
# A report is not a longer paragraph: it is what somebody sends their team when
# they want another person to understand the workflow without opening it. It is
# asked for by hand, never on its own, because it costs money; and it is written
# with the good model, because it is the only thing here anyone reads off-screen.

REPORTER = os.environ.get("SPY_REPORT_MODEL", "claude-sonnet-5")
REPORT_BUDGET = int(os.environ.get("SPY_REPORT_BUDGET", "10000"))
REPORT_TIMEOUT = int(os.environ.get("SPY_REPORT_TIMEOUT", "180"))

REPORT = """Here is everything Texera has recorded about one workflow: how it is built \
today, how it was built over time, and every run of it with the settings each run used.

Write the report someone would hand to a colleague who has to take this workflow over.

Reply with JSON and nothing else, in this shape:

{"title": "...", "summary": "...", "sections": [{"heading": "...", "body": "..."}, ...]}

title: a short name for the report, naming the workflow.

summary: three or four sentences. What the workflow answers, what it reads, what it \
produces, and the one thing the next person most needs to know.

sections: write exactly these five, in this order, with these headings:
  "What it does" - the flow from its sources to its outputs, in the order the data \
moves. Say what each part contributes. Do not list every step mechanically: group them \
the way a person would explain it out loud.
  "How it was built" - the story the edit history tells: what was there first, what was \
changed later, where whoever built it changed their mind. Name the turning points only.
  "What has been tried" - the runs as experiments. Which settings were varied, what each \
value produced, and what that comparison does and does not establish. If the same settings \
were run twice, say that those two are the same experiment.
  "What to watch out for" - what would trip up the next person: settings that keep being \
changed, steps switched off, steps producing nothing, gaps in the record, anything the \
numbers do not add up to.
  "What the record cannot tell you" - the honest limits. Whether a result was good, why \
someone made a change, what the data means beyond its column names: the record does not \
hold any of that. Be specific about which questions here need a person to answer.

Each body is plain prose, two to five sentences, no markdown, no bullet lists, no \
headings inside it. Plain words a person who does not program can read.

Every number you write must come from the facts. Never invent a column, a step, a run or \
a count. If two facts disagree, say so rather than smoothing it over.

FACTS:
"""


_reported = {}


def report(wid, refresh=False):
    """The whole report, written in one go and kept until another is asked for."""
    if not refresh and wid in _reported:
        return dict(_reported[wid], wid=wid)
    facts = {
        "the workflow as it stands today": facts_brief(wid),
        "how it was edited, oldest first": facts_history(wid),
        "every run of it": facts_experiments(wid),
    }
    raw = ask(VOICE, REPORT + json.dumps(facts, ensure_ascii=False, indent=1),
              REPORTER, REPORT_TIMEOUT, REPORT_BUDGET)
    try:
        written = json.loads(fence_free(raw))
    except (ValueError, TypeError):
        written = {}
    sections = [{"heading": str(x.get("heading") or ""), "body": str(x.get("body") or "")}
                for x in (written.get("sections") or []) if isinstance(x, dict)]
    out = {
        "title": str(written.get("title") or ""),
        "summary": str(written.get("summary") or ""),
        "sections": sections,
        "model": REPORTER,
        "written": True if sections else False,
    }
    out["markdown"] = as_markdown(wid, out)
    if sections:
        _reported[wid] = out
    return dict(out, wid=wid)


def as_markdown(wid, written):
    """The same report as a file, for whoever wants it outside the panel."""
    record = brief.orientation(wid)
    lines = ["# %s" % (written["title"] or record["name"]), ""]
    lines += [written["summary"], ""]
    for section in written["sections"]:
        lines += ["## %s" % section["heading"], "", section["body"], ""]
    lines += ["## The record behind this report", ""]
    lines += ["- Workflow %d, %s" % (wid, record["name"])]
    lines += ["- %d saved versions, %d runs on record"
              % (record["record"]["saves"], record["record"]["runs"])]
    lines += ["- %d sources, %d steps in between, %d outputs"
              % (record["shape"]["sources"], record["shape"]["middle"], record["shape"]["outputs"])]
    lines += ["", "Every figure above was derived from Texera's own saved versions and run "
                  "statistics. The prose was written by %s from those figures and nothing else."
                  % written["model"], ""]
    return "\n".join(lines)


# ---------- plomeria HTTP ----------

ROUTES = {
    "/api/spy/history": lambda q: history(int(q["wid"][0])),
    "/api/spy/brief": lambda q: brief.orientation(int(q["wid"][0])),
    "/api/spy/experiments": lambda q: brief.experiments(int(q["wid"][0])),
    "/api/spy/tour": lambda q: tour(int(q["wid"][0])),
    # The report is asked for by hand and costs money, so it is only rewritten when
    # alguien lo pide expresamente.
    "/api/spy/report": lambda q: report(int(q["wid"][0]), "refresh" in q),
    "/api/spy/executions": lambda q: executions(int(q["wid"][0])),
    "/api/spy/autopsy": lambda q: autopsy_report(int(q["wid"][0]), int(q["a"][0]), int(q["b"][0])),
    "/api/spy/narrate": lambda q: narrate(int(q["wid"][0]), int(q["a"][0]), int(q["b"][0])),
    "/api/spy/explain": lambda q: explain(int(q["wid"][0]),
                                         int(q["a"][0]) if "a" in q else None,
                                         int(q["b"][0]) if "b" in q else None),
}


def run_and_keep(payload):
    """Runs the workflow and keeps that run's rows, so it can be compared.

    This is the only route that writes anything, and it exists for one concrete
    reason: Texera drops a run's results thirty seconds after the workflow goes
    idle, so by the time somebody wants to compare two runs there are no rows
    left to compare. The engine's synchronous route does return them, so they are
    asked for on every operator, not only the final ones, and written to disk as
    they arrive.

    The run is asked for with the token of the user who has Texera open, not one
    of our own: whoever cannot run the workflow themselves must not be able to
    poder ejecutarlo desde aqui.
    """
    wid = int(payload["wid"])
    cuid = int(payload["cuid"])
    name = (payload.get("name") or "").strip() or "kept for comparison"
    snap = run_workflow.run_and_snapshot(wid, cuid, name, token=payload.get("token"))
    if "__http__" in snap:
        return {"kept": False, "status": snap["__http__"],
                "error": "Texera refused the run: %s" % snap.get("body", "")[:300]}
    steps = snap.get("operators") or {}
    rows = sum(len(v.get("result") or []) for v in steps.values())
    return {
        "kept": True,
        "eid": snap.get("eid"),
        "name": snap.get("name"),
        "state": snap.get("state"),
        "success": bool(snap.get("success")),
        "steps": len(steps),
        "rows": rows,
        # The engine's errors travel as they are: a failed run is kept too, and
        # the panel has to be able to say what went wrong.
        "errors": snap.get("errors"),
    }


def posted(payload):
    return answer(int(payload["wid"]), payload.get("question", ""),
                  payload.get("a"), payload.get("b"), payload.get("previous"),
                  payload.get("mode"))


POSTS = {"/api/spy/ask": posted, "/api/spy/run": run_and_keep}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _cors(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")

    def _json(self, code, payload):
        body = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self._cors()
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204)
        self._cors()
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_GET(self):
        u = urlparse(self.path)
        fn = ROUTES.get(u.path)
        if fn is None:
            self._json(404, {"error": "unknown route", "routes": sorted(ROUTES)})
            return
        try:
            self._json(200, fn(parse_qs(u.query)))
        except (KeyError, ValueError, IndexError) as e:
            self._json(400, {"error": "bad parameters: %s" % e})
        except SystemExit as e:
            self._json(404, {"error": str(e)})
        except Exception as e:
            traceback.print_exc()
            self._json(500, {"error": "%s: %s" % (type(e).__name__, e)})

    def do_POST(self):
        u = urlparse(self.path)
        fn = POSTS.get(u.path)
        if fn is None:
            self._json(404, {"error": "unknown route", "routes": sorted(POSTS)})
            return
        try:
            size = int(self.headers.get("Content-Length") or 0)
            payload = json.loads(self.rfile.read(size) or b"{}")
            self._json(200, fn(payload))
        except (KeyError, ValueError, TypeError) as e:
            self._json(400, {"error": "bad request: %s" % e})
        except SystemExit as e:
            self._json(404, {"error": str(e)})
        except Exception as e:
            traceback.print_exc()
            self._json(500, {"error": "%s: %s" % (type(e).__name__, e)})

    def log_message(self, fmt, *args):
        sys.stderr.write("%s %s\n" % (self.address_string(), fmt % args))


if __name__ == "__main__":
    srv = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print("boton espia escuchando en http://127.0.0.1:%d" % PORT, flush=True)
    srv.serve_forever()
