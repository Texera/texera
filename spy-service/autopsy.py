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
"""Autopsia: en que operador empiezan a diferir dos ejecuciones, y que edicion lo causo.

Compares the snapshots of two runs operator by operator, in topological order,
and names the first one whose output changes. It then crosses the workflow
versions each run came from to show the edit responsible.
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import core

# Where the rows kept from a run are written. Texera throws its own away
# thirty seconds after you stop working on the workflow.
SNAPS = os.environ.get("SPY_SNAPSHOTS", os.path.join(os.path.dirname(os.path.abspath(__file__)), "snapshots"))


def load(wid, eid):
    p = "%s/wid_%d_eid_%d.json" % (SNAPS, wid, eid)
    if not os.path.exists(p):
        raise SystemExit("no hay instantanea de eid %d" % eid)
    return json.load(open(p))


def topo(canvas):
    """Operators in topological order, following the canvas links."""
    ops = [o["operatorID"] for o in canvas.get("operators", [])]
    edges = {}
    indeg = {o: 0 for o in ops}
    for l in canvas.get("links", []):
        a, b = l["source"]["operatorID"], l["target"]["operatorID"]
        edges.setdefault(a, []).append(b)
        if b in indeg:
            indeg[b] += 1
    queue = [o for o in ops if indeg[o] == 0]
    out = []
    while queue:
        n = queue.pop(0)
        out.append(n)
        for m in edges.get(n, []):
            indeg[m] -= 1
            if indeg[m] == 0:
                queue.append(m)
    for o in ops:                       # ciclos o islas, al final
        if o not in out:
            out.append(o)
    return out


def rows_of(snap, oid):
    v = (snap.get("operators") or {}).get(oid) or {}
    rows = v.get("result") or []
    return [{k: x[k] for k in x if k != "__row_index__"} for x in rows]


def count_of(snap, oid):
    """How many rows the operator really produced, and whether the sample is cut.

    The engine does not return every row: there is a hard cap of 100,000
    characters per operator (MAX_OPERATOR_RESULT_CHARS in SyncExecutionResource)
    and past it the head and the tail are kept. But it declares the real total in
    outputTuples and, in newer snapshots, in totalRowCount. Counting the rows of
    the sample gives a false number; the engine is the one to believe.
    """
    v = (snap.get("operators") or {}).get(oid) or {}
    shown = len(v.get("result") or [])
    total = v.get("totalRowCount")
    if total is None:
        total = v.get("outputTuples")
    if total is None:
        total = shown
    cut = bool(v.get("truncated")) or shown < total
    return int(total), shown, cut


def compare_rows(a, b, total_a=None, total_b=None, cut=False):
    """Resumen de la diferencia entre dos salidas.

    The counts are the ones the engine declares; the rows are only a sample.
    If either side comes back cut, rows that appear on one side and not the
    other may be an artefact of the cut, and are marked inconclusive.
    """
    ka = [json.dumps(r, sort_keys=True, ensure_ascii=False) for r in a]
    kb = [json.dumps(r, sort_keys=True, ensure_ascii=False) for r in b]
    total_a = len(a) if total_a is None else total_a
    total_b = len(b) if total_b is None else total_b
    if ka == kb and total_a == total_b:
        return None
    sa, sb = set(ka), set(kb)
    return {
        "filas_a": total_a, "filas_b": total_b,
        "muestra_a": len(a), "muestra_b": len(b), "muestra_cortada": cut,
        "solo_en_a": [json.loads(x) for x in list(sa - sb)[:8]],
        "solo_en_b": [json.loads(x) for x in list(sb - sa)[:8]],
        "mismo_conjunto_distinto_orden": sa == sb and total_a == total_b,
    }


def operator_props(canvas):
    return {o["operatorID"]: o.get("operatorProperties", {})
            for o in canvas.get("operators", [])}


def prop_diff(pa, pb, path=""):
    """Diferencias entre dos arboles de propiedades, como lista de rutas."""
    out = []
    if type(pa) is not type(pb):
        return [(path, pa, pb)]
    if isinstance(pa, dict):
        for k in sorted(set(pa) | set(pb)):
            out += prop_diff(pa.get(k), pb.get(k), "%s/%s" % (path, k))
    elif isinstance(pa, list):
        if len(pa) != len(pb):
            return [(path, pa, pb)]
        for i, (x, y) in enumerate(zip(pa, pb)):
            out += prop_diff(x, y, "%s/%d" % (path, i))
    elif pa != pb:
        out.append((path, pa, pb))
    return out


def autopsy(wid, eid_a, eid_b):
    sa, sb = load(wid, eid_a), load(wid, eid_b)
    execs = {e["eid"]: e for e in core.executions(wid)}
    vid_a, vid_b = execs[eid_a]["vid"], execs[eid_b]["vid"]

    wf, hist, origin, broken = core.history(wid)
    states = {h["vid"]: h["state"] for h in hist}
    canvas_a, canvas_b = states.get(vid_a), states.get(vid_b)
    canvas = canvas_b or canvas_a or wf["content"]

    order = topo(canvas)
    findings = []
    # Two candidates to blame. A step that returns exactly the same rows in a
    # different order has not changed the data: the engine splits the work
    # entre varios trabajadores y el orden de salida baila entre ejecuciones,
    # so naming it would hide the real edit further down. A reordering is only
    # accused when there is no difference in content anywhere in the flow;
    # then it really is all that happened.
    first_content = None
    first_any = None
    for oid in order:
        ta, _, cut_a = count_of(sa, oid)
        tb, _, cut_b = count_of(sb, oid)
        d = compare_rows(rows_of(sa, oid), rows_of(sb, oid), ta, tb, cut_a or cut_b)
        # The counts live outside the diff because the panel draws them even
        # when both sides agree: an identical step still has volume.
        findings.append({"operatorID": oid, "diff": d,
                         "filas_a": ta, "filas_b": tb, "cortada": cut_a or cut_b})
        if d:
            if first_any is None:
                first_any = oid
            if first_content is None and not d["mismo_conjunto_distinto_orden"]:
                first_content = oid
    first = first_content or first_any

    # the edit to blame: properties that changed between the two versions
    culprit = []
    if canvas_a and canvas_b:
        pa, pb = operator_props(canvas_a), operator_props(canvas_b)
        for oid in order:
            for path, va, vb in prop_diff(pa.get(oid, {}), pb.get(oid, {})):
                culprit.append({"operatorID": oid, "path": path, "antes": va, "despues": vb})

    return {"wid": wid, "name": wf["name"],
            "a": {"eid": eid_a, "vid": vid_a, "name": sa.get("name")},
            "b": {"eid": eid_b, "vid": vid_b, "name": sb.get("name")},
            "orden": order, "hallazgos": findings,
            # The later version's whole canvas travels along because the panel
            # has to say what each step does, not only how many rows it emits.
            "lienzo": canvas,
            "primer_divergente": first, "ediciones": culprit}


if __name__ == "__main__":
    wid, ea, eb = int(sys.argv[1]), int(sys.argv[2]), int(sys.argv[3])
    r = autopsy(wid, ea, eb)
    print("flujo %d: %s" % (r["wid"], r["name"]))
    print("A = eid %d (version %d, %s)" % (r["a"]["eid"], r["a"]["vid"], r["a"]["name"]))
    print("B = eid %d (version %d, %s)" % (r["b"]["eid"], r["b"]["vid"], r["b"]["name"]))
    print("\norden topologico: %s" % " -> ".join(r["orden"]))
    print("\npor operador:")
    for h in r["hallazgos"]:
        if not h["diff"]:
            print("  %-5s identico" % h["operatorID"])
        else:
            d = h["diff"]
            marca = "  <-- PRIMERO QUE DIVERGE" if h["operatorID"] == r["primer_divergente"] else ""
            print("  %-5s DIFIERE  filas %d vs %d%s" % (
                h["operatorID"], d["filas_a"], d["filas_b"], marca))
            for r_ in d["solo_en_a"][:2]:
                print("        solo en A:", json.dumps(r_, ensure_ascii=False)[:90])
            for r_ in d["solo_en_b"][:2]:
                print("        solo en B:", json.dumps(r_, ensure_ascii=False)[:90])
    print("\nediciones entre las dos versiones:")
    if not r["ediciones"]:
        print("  none in the operators' properties")
    for e in r["ediciones"]:
        print("  %-5s %s : %s -> %s" % (e["operatorID"], e["path"],
                                        json.dumps(e["antes"], ensure_ascii=False),
                                        json.dumps(e["despues"], ensure_ascii=False)))
