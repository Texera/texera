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
"""Nucleo del boton espia: reconstruye el historial completo de un flujo.

Texera stores one *inverse* JSON patch per version in workflow_version:
applying the patch of version N to the current content gives the state before
it. Walking the patches from newest to oldest recovers every state the canvas
ever passed through.
"""
import json, os, subprocess, copy

# Every connection detail comes from the environment, with the defaults of the
# development bundle, so that nothing about one machine is written into the code.
HOST = os.environ.get("SPY_PG_HOST", "127.0.0.1")
PORT = os.environ.get("SPY_PG_PORT", "5432")
USER = os.environ.get("SPY_PG_USER", "texera")
PASSWORD = os.environ.get("SPY_PG_PASSWORD", "")
DATABASE = os.environ.get("SPY_PG_DATABASE", "texera_db")
SCHEMA = os.environ.get("SPY_PG_SCHEMA", "texera_db")

DB = ["psql", "-h", HOST, "-p", PORT, "-U", USER, "-d", DATABASE, "-tAc"]
ENV = {"PGPASSWORD": PASSWORD, "PATH": os.environ.get("PATH", "/usr/bin:/bin")}


def qraw(sql):
    out = subprocess.run(DB + [sql], capture_output=True, text=True, env=ENV, check=True)
    return out.stdout


def q(sql):
    return [l for l in qraw(sql).split("\n") if l != ""]


# ---------- JSON Patch (RFC 6902), only as much as Texera emits ----------

def _tokens(path):
    if path == "":
        return []
    return [t.replace("~1", "/").replace("~0", "~") for t in path.lstrip("/").split("/")]


def _resolve(doc, tokens):
    """Devuelve (contenedor, clave) del ultimo token."""
    cur = doc
    for t in tokens[:-1]:
        cur = cur[int(t)] if isinstance(cur, list) else cur[t]
    last = tokens[-1]
    if isinstance(cur, list) and last != "-":
        last = int(last)
    return cur, last


def apply_patch(doc, patch, tolerated=None):
    """Applies one inverse patch to the canvas.

    Texera guarda a veces la misma operacion repetida en versiones seguidas
    ("remove envName" on three consecutive saves). The first one applies and
    the rest cannot find the key, so the chain breaks and everything before it
    becomes unrecoverable. Removing what is already gone leaves exactly the
    document the patch describes, so it is tolerated here and counted in
    `tolerated`: the rebuilt state is still the one the patch asks for, and
    whoever opens the panel is told the record came that way.
    """
    doc = copy.deepcopy(doc)
    for op in patch:
        o, path = op["op"], op["path"]
        tokens = _tokens(path)
        if not tokens:                      # la raiz entera
            if o in ("replace", "add"):
                doc = copy.deepcopy(op["value"])
                continue
            if o == "remove":
                return {}
            raise ValueError("op %s en la raiz" % o)
        if o in ("add", "replace"):
            cont, key = _resolve(doc, tokens)
            if isinstance(cont, list):
                if o == "add":
                    cont.insert(len(cont) if key == "-" else key, copy.deepcopy(op["value"]))
                else:
                    cont[key] = copy.deepcopy(op["value"])
            else:
                cont[key] = copy.deepcopy(op["value"])
        elif o == "remove":
            cont, key = _resolve(doc, tokens)
            try:
                del cont[key]
            except (KeyError, IndexError):
                if tolerated is None:
                    raise
                tolerated.append(path)
        elif o in ("move", "copy"):
            src = _tokens(op["from"])
            sc, sk = _resolve(doc, src)
            val = copy.deepcopy(sc[sk])
            if o == "move":
                del sc[sk]
            cont, key = _resolve(doc, tokens)
            if isinstance(cont, list):
                cont.insert(len(cont) if key == "-" else key, val)
            else:
                cont[key] = val
        elif o == "test":
            pass
        else:
            raise ValueError("op desconocida: %s" % o)
    return doc


# ---------- historial ----------

def qjson(sql):
    """Runs a query and returns the result as JSON, without splitting text."""
    raw = qraw("select coalesce(json_agg(t), '[]'::json)::text from (%s) t" % sql)
    return json.loads(raw)


def workflow(wid):
    rows = qjson("select name, coalesce(description,'') as description, content "
                 "from texera_db.workflow where wid=%d" % wid)
    if not rows:
        raise SystemExit("no workflow with id %d" % wid)
    r = rows[0]
    return {"wid": wid, "name": r["name"], "description": r["description"],
            "content": json.loads(r["content"])}


def versions(wid):
    """Versions from oldest to newest: [{vid, time, patch}]."""
    rows = qjson("select vid, creation_time::text as time, content "
                 "from texera_db.workflow_version where wid=%d order by vid" % wid)
    return [{"vid": r["vid"], "time": r["time"], "patch": json.loads(r["content"])}
            for r in rows]


def history(wid):
    """Canvas states from oldest to newest.

    Each entry: {vid, time, state, broken}. The state of version N is the
    canvas as it stood *after* that edit. `broken` marks the version whose
    patch could not be applied: from there backwards, the history Texera keeps
    is inconsistent and cannot be rebuilt. The
    versiones anteriores a ese punto quedan con state=None.

    Equivalent to Texera's own applier (WorkflowVersionResource.applyPatch),
    contrastado version por version; ver verify.py.
    """
    wf = workflow(wid)
    vs = versions(wid)
    state = wf["content"]
    states = [None] * len(vs)
    broken_at = None
    # Per version, the operations that found nothing to remove.
    forgiven = {}
    for i in range(len(vs) - 1, -1, -1):
        states[i] = state
        if state is None:
            continue
        eased = []
        try:
            state = apply_patch(state, vs[i]["patch"], eased)
        except Exception as e:
            broken_at = {"vid": vs[i]["vid"], "error": "%s: %s" % (type(e).__name__, e)}
            state = None
        forgiven[vs[i]["vid"]] = eased
    origin = state
    out = []
    for v, s in zip(vs, states):
        out.append({"vid": v["vid"], "time": v["time"], "state": s,
                    "broken": bool(broken_at and v["vid"] == broken_at["vid"]),
                    "tolerated": forgiven.get(v["vid"], [])})
    return wf, out, origin, broken_at


def people(wid):
    """Who owns the workflow and who else has access, with their level."""
    return qjson("select u.name, u.email, a.privilege, "
                 "(o.uid is not null) as owner "
                 "from texera_db.workflow_user_access a "
                 "join texera_db.\"user\" u on u.uid=a.uid "
                 "left join texera_db.workflow_of_user o on o.uid=a.uid and o.wid=a.wid "
                 "where a.wid=%d order by owner desc, u.name" % wid)


def executions(wid):
    return qjson("select e.eid, e.vid, e.status, e.starting_time::text as start, "
                 "e.last_update_time::text as \"end\", e.runtime_stats_uri as stats_uri, "
                 "coalesce(e.runtime_stats_size,0) as stats_size, e.uid, "
                 "coalesce(e.name,'') as name, coalesce(u.name,'') as who "
                 "from texera_db.workflow_executions e "
                 "join texera_db.workflow_version v on v.vid=e.vid "
                 "left join texera_db.\"user\" u on u.uid=e.uid "
                 "where v.wid=%d order by e.eid" % wid)


if __name__ == "__main__":
    import sys
    wid = int(sys.argv[1])
    wf, hist, origin, broken = history(wid)
    usable = [h for h in hist if h["state"] is not None]
    print("flujo %d: %s" % (wid, wf["name"]))
    print("versiones: %d   reconstruidas: %d" % (len(hist), len(usable)))
    print("ultimo estado == contenido actual -> %s"
          % ("OK" if hist[-1]["state"] == wf["content"] else "FALLA"))
    if broken:
        print("CADENA ROTA en vid %s (%s)" % (broken["vid"], broken["error"]))
        print("  the %d versions before it cannot be rebuilt"
              % (len(hist) - len(usable)))
    else:
        print("cadena completa -> %s" % ("OK, llega al vacio" if origin in ({}, None) else "raro: %s" % json.dumps(origin)[:80]))
    for h in hist:
        st = h["state"]
        if st is None:
            print("  vid %-4d %s  (irrecuperable)" % (h["vid"], h["time"][:19]))
        else:
            print("  vid %-4d %s  operadores=%d links=%d%s" % (
                h["vid"], h["time"][:19], len(st.get("operators", [])),
                len(st.get("links", [])), "   <-- cadena rota aqui" if h["broken"] else ""))
