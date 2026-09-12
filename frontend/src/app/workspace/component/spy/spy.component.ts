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

/**
 * The spy button: opens the record Texera keeps of a workflow without showing it.
 *
 * Two tabs. THE FOOTAGE replays the canvas one saved version at a time, rebuilt
 * from the inverse patches in `workflow_version`. THE AUTOPSY compares two runs
 * operator by operator and names the edit that moved the numbers.
 *
 * The data comes from spy/server.py, which reads the database directly. That
 * server only reads, with one exception: the autopsy tab can ask it to run the
 * workflow and keep every step's rows, because Texera throws them away.
 */
import { HttpClient } from "@angular/common/http";
import { Component, HostListener, OnDestroy, OnInit, inject } from "@angular/core";
import { NgClass, NgFor, NgIf, NgTemplateOutlet } from "@angular/common";
import { NZ_MODAL_DATA } from "ng-zorro-antd/modal";
import { OperatorMetadataService } from "../../service/operator-metadata/operator-metadata.service";
import { CustomJSONSchema7 } from "../../types/custom-json-schema.interface";
import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { map, switchMap, tap } from "rxjs/operators";
import { Observable, of } from "rxjs";
import { AuthService } from "../../../common/service/user/auth.service";
import { ComputingUnitStatusService } from "../../../common/service/computing-unit/computing-unit-status/computing-unit-status.service";
import { WorkflowPersistService } from "../../../common/service/workflow-persist/workflow-persist.service";
import { WorkflowActionService } from "../../service/workflow-graph/model/workflow-action.service";
import { Workflow } from "../../../common/type/workflow";

export const SPY_API = "http://127.0.0.1:5055/api/spy";

/** The five views. Each one is a question somebody actually asks. */
export type SpyTab = "brief" | "footage" | "experiments" | "autopsy" | "report";

/** Milliseconds each version is held on screen while the footage plays. */
const FRAME_MS = 950;

const BOX_WIDTH = 150;
const BOX_HEIGHT = 46;
const BOX_MARGIN = 40;

// The walkthrough's own diagram, which is laid out here rather than taken from
// the canvas: bigger boxes, because each one carries an icon and a count.
const NODE_WIDTH = 178;
const NODE_HEIGHT = 66;
const NODE_GAP_X = 70;
const NODE_GAP_Y = 26;

// The kinds of step whose job is to let some rows through and not others.
const DISCARDS = new Set(["filter", "limit"]);

// The autopsy's own boxes: wider and taller, because each one carries two
// volume bars and the counts behind them.

interface SpyOperator {
  id: string;
  type: string;
  name: string;
  x: number;
  y: number;
  disabled: boolean;
  /** Display names of its input ports, to tell one input from another. */
  inputs?: string[];
}

interface SpyLink {
  from: string;
  to: string;
}

/** One edit the workflow's author made, read back from the saved patch. */
interface SpyChange {
  kind: string;
  text: string;
  operatorId?: string;
  operatorType?: string;
  name?: string;
  from?: string;
  to?: string;
  path?: string;
  before?: string;
  after?: string;
  /** The value itself, when it was small enough to travel, for describing it. */
  beforeValue?: unknown;
  afterValue?: unknown;
  /** Which input of the target operator the link lands on. */
  port?: string;
}

/** One question put to the record, and what came back. */
interface SpyTurn {
  question: string;
  answer?: string;
  failed?: boolean;
}

/** A change written the way the person who made it would say it. */
interface Told {
  lead: string;
  before?: string;
  after?: string;
  join?: string;
  detail: string;
}

interface SpyRunStub {
  eid: number;
  status: string;
  started: string;
}

/** The canvas as it stood right after one saved version. */
interface SpyFrame {
  vid: number;
  time: string;
  broken: boolean;
  recoverable: boolean;
  operators: SpyOperator[];
  links: SpyLink[];
  changes: SpyChange[];
  runs: SpyRunStub[];
}

interface SpyHistory {
  wid: number;
  name: string;
  description: string;
  total: number;
  recovered: number;
  /** Undo steps Texera saved that had nothing left to undo. */
  tolerated: number;
  broken: { vid: number; error: string } | null;
  reachesOrigin: boolean;
  frames: SpyFrame[];
}

interface SpyRun {
  eid: number;
  vid: number;
  status: string;
  started: string;
  ended: string;
  snapshot: boolean;
  name: string | null;
  rows: number;
}

/** What the spy server reports after running the workflow and keeping its rows. */
interface SpyKept {
  kept: boolean;
  eid?: number;
  name?: string;
  state?: string;
  success?: boolean;
  steps?: number;
  rows?: number;
  error?: string;
}

interface SpyDiff {
  rowsA: number;
  rowsB: number;
  /** How many rows the engine actually handed back for this step. */
  sampleA?: number;
  sampleB?: number;
  /** True when the engine cut the output, so the sample proves nothing absent. */
  sampleTruncated?: boolean;
  onlyInA: object[];
  onlyInB: object[];
  sameSetDifferentOrder: boolean;
}

interface SpyFinding {
  /** Real counts as the engine reported them, present even when the two match. */
  rowsA: number;
  rowsB: number;
  truncated: boolean;
  operatorId: string;
  diff: SpyDiff | null;
}

interface SpyEdit {
  operatorId: string;
  path: string;
  before: unknown;
  after: unknown;
}

interface SpyAutopsy {
  wid: number;
  name: string;
  a: { eid: number; vid: number; name: string };
  b: { eid: number; vid: number; name: string };
  order: string[];
  findings: SpyFinding[];
  firstDivergent: string | null;
  edits: SpyEdit[];
}

/** An operator box already placed inside the drawn canvas. */
interface CanvasBox {
  op: SpyOperator;
  x: number;
  y: number;
  mark: "added" | "edited" | "untouched";
  note: string;
}

interface CanvasArrow {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

/** An operator that is not on the canvas yet, drawn where it will land. */
interface GhostBox {
  id: string;
  x: number;
  y: number;
}

/** One block of the filmstrip: a save, coloured by what it did to the canvas. */
interface TapeBlock {
  index: number;
  tone: "added" | "removed" | "edited" | "quiet" | "broken";
  label: string;
  ran: boolean;
}

/** One step of the run comparison, drawn as two bars instead of two numbers. */
interface PipelineStep {
  id: string;
  position: number;
  name: string;
  rowsA: number;
  rowsB: number;
  barA: number;
  barB: number;
  state: "same" | "starts" | "carried" | "reordered";
  note: string;
  truncated: boolean;
}

/** One setting of a step, as the panel is given it: raw, to be worded here. */
interface BriefSetting {
  path: string;
  value?: unknown;
  /** Set instead of the value when the value is too big to describe. */
  lines?: number;
  size?: number;
}

/** One step of the workflow as it stands today, with what it does and moves. */
interface BriefStep {
  id: string;
  position: number;
  name: string;
  type: string;
  role: "source" | "step" | "output";
  disabled: boolean;
  settings: BriefSetting[];
  fedBy: { id: string; port: string }[];
  feeds: string[];
  rowsIn: number | null;
  rowsOut: number | null;
  workers: number | null;
  seconds: number | null;
  columns: string[];
  sample: Record<string, unknown>[];
  edits: number;
  lastEdited: string | null;
  addedOn: string | null;
}

/** A setting people actually turn, either repeatedly or between two runs. */
interface BriefKnob {
  stepId: string;
  step: string;
  type: string;
  path: string;
  times: number;
  tried: boolean;
  value?: unknown;
}

interface SpyBrief {
  wid: number;
  name: string;
  description: string;
  people: { name: string; access: string; owner: boolean }[];
  ranBy: string[];
  shape: { sources: number; middle: number; outputs: number };
  steps: BriefStep[];
  knobs: BriefKnob[];
  sampleFrom: { eid: number; name: string; when: string; current: boolean } | null;
  record: {
    saves: number;
    sessions: number;
    started: string | null;
    lastEdited: string | null;
    runs: number;
    completed: number;
    lastRun: string | null;
    lastRunName: string;
    unreadable: number;
    broken: boolean;
  };
}

/** The workflow walked through for someone who has never opened it. */
interface Tour {
  headline: string;
  purpose: string;
  steps: Record<string, string>;
  watchOut: string;
  model: string;
}

/** What one step did in one run, as the engine's own statistics recorded it. */
interface RunStep {
  in: number;
  out: number;
  workers: number;
  seconds: number;
}

/** One run of the workflow, read as an experiment: settings in, rows out. */
interface ExperimentRun {
  eid: number;
  vid: number;
  name: string;
  who: string;
  started: string;
  ended: string;
  seconds: number | null;
  status: string;
  snapshot: boolean;
  measured: boolean;
  steps: Record<string, RunStep>;
  readIn: number;
  produced: number;
  settings: Record<string, unknown>;
  canvasSteps: number;
}

/** A setting that was not the same in every run: the dial of an experiment. */
interface KnobDef {
  id: string;
  stepId: string;
  step: string;
  type: string;
  path: string;
}

/** What changed between one run and the next one after it. */
interface RunMove {
  from: number;
  to: number;
  edits: { knob: string; step: string; stepId: string; type: string; path: string; before: unknown; after: unknown }[];
  structure: { kind: string; step: string; stepId: string; before?: string }[];
  producedBefore: number;
  producedAfter: number;
}

interface SpyExperiments {
  wid: number;
  name: string;
  knobs: KnobDef[];
  runs: ExperimentRun[];
  moves: RunMove[];
}

interface SpyReport {
  title: string;
  summary: string;
  sections: { heading: string; body: string }[];
  markdown: string;
  model: string;
  written: boolean;
}

/** One step drawn as a box in the flow diagram, with its own volume. */
/** One run's place on a metric line: a dot, and what it says when hovered. */
interface TrendDot {
  x: number;
  y: number;
  eid: number;
  label: string;
  value: string;
  /** A run that produced nothing is a different event, not a low point. */
  empty: boolean;
}

/** One metric read across every run that measured it. */
interface Trend {
  key: string;
  label: string;
  /** The line is cut wherever a run produced nothing, so it never lies. */
  segments: string[];
  dots: TrendDot[];
  high: string;
  low: string;
  latest: string;
  change: string;
  /** Only latency is judged. More rows is not better or worse, it is different. */
  verdict: "" | "better" | "worse" | "same";
}

interface FlowNode {
  step: BriefStep;
  x: number;
  y: number;
  kind: string;
  glyph: string;
  rows: number | null;
  /** How many rows this step swallows, when it swallows a noticeable share. */
  drop: number;
}

/** One pipe between two boxes, as thick as the data that runs through it. */
interface FlowPipe {
  path: string;
  width: number;
  rows: number;
  midX: number;
  midY: number;
}

/** One row of the experiments table, already worked out for the template. */
interface ExperimentRow {
  run: ExperimentRun;
  values: { knob: KnobDef; text: string; changed: boolean }[];
  bar: number;
  move?: RunMove;
  repeatOf?: number;
}

@UntilDestroy()
@Component({
  selector: "texera-spy",
  templateUrl: "spy.component.html",
  styleUrls: ["spy.component.scss"],
  imports: [NgIf, NgFor, NgClass, NgTemplateOutlet],
})
export class SpyComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly metadata = inject(OperatorMetadataService);
  private readonly computingUnits = inject(ComputingUnitStatusService);
  private readonly workflowPersist = inject(WorkflowPersistService);
  private readonly workflowAction = inject(WorkflowActionService);
  private readonly modalData = inject<{ wid: number }>(NZ_MODAL_DATA, { optional: true });

  public wid = 0;
  public error = "";
  /**
   * The tabs are the questions they answer. The first one is where someone who
   * has never opened this workflow lands, so it is the one that opens first.
   */
  public tab: SpyTab = "brief";

  // ----- the workflow as it stands today -----
  public brief?: SpyBrief;
  public loadingBrief = true;
  public tour: Tour = { headline: "", purpose: "", steps: {}, watchOut: "", model: "" };
  public tourPending = false;
  /** The step whose detail is open in the walkthrough. */
  public opened: string | null = null;

  // ----- what has been tried -----
  public experiments?: SpyExperiments;
  public loadingExperiments = false;
  // ----- the report -----
  public report?: SpyReport;
  public reportPending = false;
  public reportFailed = false;
  public copied = false;

  // ----- the footage -----
  public history?: SpyHistory;
  public loadingHistory = true;
  public index = 0;
  public playing = false;
  private clock?: ReturnType<typeof setInterval>;

  /** Fixed camera: the frame is computed once over every version at once. */
  public viewBox = "0 0 800 400";
  public boxes: CanvasBox[] = [];
  public arrows: CanvasArrow[] = [];
  public ghosts: GhostBox[] = [];
  /** When set, the activity log only shows what touched this operator. */
  public focused: string | null = null;
  /** Whether saves that changed nothing visible are listed too. */
  public showEverySave = false;
  /** Whether the note on how the rebuild works is unfolded. */
  public showMethod = false;

  /** The exact counts, kept behind a disclosure: the bars say it first. */
  public showNumbers = false;

  // ----- the autopsy -----
  public runs: SpyRun[] = [];
  public comparable: SpyRun[] = [];
  public eidA?: number;
  public eidB?: number;
  public autopsy?: SpyAutopsy;
  public loadingAutopsy = false;

  // ----- running a workflow and keeping its rows -----
  /** Whether a run asked for from this panel is still on. */
  public keeping = false;
  /** What that run is doing right now, for the line under the button. */
  public keepingStep = "";
  public keptError?: string;
  public kept?: SpyKept;
  public expanded = new Set<string>();

  public readonly boxWidth = BOX_WIDTH;
  public readonly boxHeight = BOX_HEIGHT;

  ngOnInit(): void {
    this.wid = this.modalData?.wid ?? 0;
    if (!this.wid) {
      this.error = "this workflow has not been saved yet, so there is nothing on record to open.";
      this.loadingHistory = false;
      return;
    }
    this.http
      .get<SpyHistory>(`${SPY_API}/history?wid=${this.wid}`)
      .pipe(untilDestroyed(this))
      .subscribe({
        next: h => {
          this.history = h;
          this.loadingHistory = false;
          this.frameCamera();
          this.goTo(this.frames.length - 1);
          this.readOut("footage", `wid=${this.wid}`);
        },
        error: (e: unknown) => {
          this.loadingHistory = false;
          this.error = this.explain(e);
        },
      });
    this.http
      .get<SpyBrief>(`${SPY_API}/brief?wid=${this.wid}`)
      .pipe(untilDestroyed(this))
      .subscribe({
        next: b => {
          this.brief = b;
          this.loadingBrief = false;
          this.layOut();
          this.walkThrough();
        },
        error: (e: unknown) => {
          this.loadingBrief = false;
          this.error = this.explain(e);
        },
      });
    this.loadRuns()
      .pipe(untilDestroyed(this))
      .subscribe({
        next: () => this.pickLastTwo(),
        error: (e: unknown) => {
          this.error = this.explain(e);
        },
      });
  }

  ngOnDestroy(): void {
    this.stop();
  }

  private explain(e: unknown): string {
    const failure = e as { status?: number; error?: { error?: string } };
    if (failure?.status === 0) {
      return "the spy server is not answering. Start it with: /home/bruno/texera/start-spy.sh";
    }
    return failure?.error?.error ?? "the request to the spy server failed.";
  }

  // ---------- keyboard ----------

  @HostListener("document:keydown", ["$event"])
  public onKey(event: KeyboardEvent): void {
    if (this.tab !== "footage" || !this.history) {
      return;
    }
    const target = event.target as HTMLElement | null;
    // The scrubber already moves itself with the arrows; stepping twice skips.
    if (target && (target.tagName === "INPUT" || target.tagName === "SELECT")) {
      return;
    }
    switch (event.key) {
      case "ArrowLeft":
        this.previous();
        break;
      case "ArrowRight":
        this.next();
        break;
      case "Home":
        this.stop();
        this.goTo(0);
        break;
      case "End":
        this.stop();
        this.goTo(this.last);
        break;
      case " ":
        this.play();
        break;
      default:
        return;
    }
    event.preventDefault();
  }

  // ---------- the footage ----------

  /**
   * Saves that changed nothing visible are noise for anyone who is not reading
   * the database, so they are left out unless asked for.
   */
  public get frames(): SpyFrame[] {
    const all = this.history?.frames ?? [];
    if (this.showEverySave) {
      return all;
    }
    return all.filter(
      (f, i) => i === 0 || i === all.length - 1 || f.changes.length > 0 || f.runs.length > 0 || !f.recoverable
    );
  }

  public get hiddenCount(): number {
    return (this.history?.frames.length ?? 0) - this.frames.length;
  }

  public get frame(): SpyFrame | undefined {
    return this.frames[this.index];
  }

  public get last(): number {
    return Math.max(0, this.frames.length - 1);
  }

  /** Keeps the same version on screen when saves are hidden or shown again. */
  public toggleEverySave(): void {
    const vid = this.frame?.vid;
    this.showEverySave = !this.showEverySave;
    const frames = this.frames;
    const found = frames.findIndex(f => f.vid === vid);
    this.goTo(found >= 0 ? found : frames.length - 1);
  }

  /** The changes to list: all of them, or only those touching the focus. */
  public get log(): SpyChange[] {
    const changes = this.frame?.changes ?? [];
    if (!this.focused) {
      return changes;
    }
    const id = this.focused;
    return changes.filter(c => c.operatorId === id || c.text.includes(id));
  }

  /** The log already written out, so the template does not compose sentences. */
  public get toldLog(): { change: SpyChange; told: Told }[] {
    return this.fold(this.log);
  }

  /**
   * One save often holds several patches that were a single gesture: dropping
   * an operator into the middle of a connection is a cut and two joins. Read
   * back one by one they hide what happened, so they are told as one sentence.
   */
  private fold(changes: SpyChange[]): { change: SpyChange; told: Told }[] {
    const added = changes.filter(c => c.kind === "link_added");
    const spent = new Set<SpyChange>();
    // The sentence is filed under the member that comes first, so it lands
    // where the reader expects it and the rest of the group drops out.
    const grouped = new Map<SpyChange, Told>();

    const file = (members: SpyChange[], lead: string) => {
      members.forEach(member => spent.add(member));
      const first = changes.find(c => members.includes(c))!;
      grouped.set(first, { lead, detail: members.map(m => m.text).join(" · ") });
    };

    for (const change of changes) {
      // Cut A into C, then A into B and B into C: something was put in between.
      if (change.kind === "link_removed" && !spent.has(change)) {
        const intoMiddle = added.find(l => l.from === change.from && !spent.has(l));
        const outOfMiddle =
          intoMiddle && added.find(l => l.from === intoMiddle.to && l.to === change.to && !spent.has(l));
        if (intoMiddle && outOfMiddle) {
          file(
            [change, intoMiddle, outOfMiddle],
            `You put ${this.nameOf(intoMiddle.to)} between ${this.nameOf(change.from)} and ${this.inputOf(
              change.to,
              outOfMiddle.port
            )}.`
          );
        }
      }
    }

    for (const change of changes) {
      // An operator added and wired up in the same save.
      if (change.kind !== "operator_added" || spent.has(change) || !change.operatorId) {
        continue;
      }
      const id = change.operatorId;
      const feeding = added.find(l => l.to === id && !spent.has(l));
      const feeds = added.find(l => l.from === id && !spent.has(l));
      if (!feeding && !feeds) {
        continue;
      }
      const opening = this.tell(change).lead.replace(/\.$/, "");
      let wiring: string;
      if (feeding && feeds) {
        wiring = `, between ${this.nameOf(feeding.from)} and ${this.inputOf(feeds.to, feeds.port)}`;
      } else if (feeding) {
        wiring = ` and fed ${this.nameOf(feeding.from)} into it`;
      } else {
        wiring = ` and sent it into ${this.inputOf(feeds!.to, feeds!.port)}`;
      }
      file(
        [change, feeding, feeds].filter((c): c is SpyChange => !!c),
        `${opening}${wiring}.`
      );
    }

    const out: { change: SpyChange; told: Told }[] = [];
    for (const change of changes) {
      const told = grouped.get(change);
      if (told) {
        out.push({ change, told });
      } else if (!spent.has(change)) {
        out.push({ change, told: this.tell(change) });
      }
    }
    return out;
  }

  /** One line saying what this version did, for someone not reading the log. */
  public get headline(): string {
    const f = this.frame;
    if (!f) {
      return "";
    }
    if (!f.recoverable) {
      return "Version could not be rebuilt";
    }
    if (f.changes.length === 0) {
      return this.index === 0 ? "Empty canvas" : "No change on the canvas";
    }
    const counted = new Map<string, number>();
    for (const c of f.changes) {
      counted.set(c.kind, (counted.get(c.kind) ?? 0) + 1);
    }
    const phrases: string[] = [];
    const say = (kind: string, singular: string, plural: string) => {
      const n = counted.get(kind);
      if (n) {
        phrases.push(`${n} ${n === 1 ? singular : plural}`);
      }
    };
    say("operator_added", "operator added", "operators added");
    say("operator_deleted", "operator deleted", "operators deleted");
    say("link_added", "link added", "links added");
    say("link_removed", "link removed", "links removed");
    say("property", "setting changed", "settings changed");
    say("renamed", "operator renamed", "operators renamed");
    say("disabled", "operator toggled", "operators toggled");
    say("moved", "operator moved", "operators moved");
    const text = phrases.join(", ");
    return text.charAt(0).toUpperCase() + text.slice(1);
  }

  /** How long the author waited before saving this version. */
  public get sincePrevious(): string {
    const frames = this.frames;
    if (!frames.length || this.index === 0) {
      return "the very beginning";
    }
    const before = Date.parse(frames[this.index - 1].time.replace(" ", "T"));
    const now = Date.parse(frames[this.index].time.replace(" ", "T"));
    if (isNaN(before) || isNaN(now)) {
      return "";
    }
    return this.spell(Math.max(0, Math.round((now - before) / 1000))) + " later";
  }

  /** Fixed camera so the graph does not jump around as versions advance. */
  private frameCamera(): void {
    const ops = (this.history?.frames ?? []).flatMap(f => f.operators);
    if (ops.length === 0) {
      return;
    }
    const minX = Math.min(...ops.map(o => o.x));
    const minY = Math.min(...ops.map(o => o.y));
    const maxX = Math.max(...ops.map(o => o.x));
    const maxY = Math.max(...ops.map(o => o.y));
    const width = maxX - minX + BOX_WIDTH + 2 * BOX_MARGIN;
    const height = maxY - minY + BOX_HEIGHT + 2 * BOX_MARGIN;
    this.viewBox = `${minX - BOX_MARGIN} ${minY - BOX_MARGIN} ${width} ${height}`;
  }

  private draw(): void {
    const f = this.frame;
    if (!f) {
      this.boxes = [];
      this.arrows = [];
      this.ghosts = [];
      return;
    }
    const added = new Set(f.changes.filter(c => c.kind === "operator_added").map(c => c.operatorId));
    const notes = new Map<string, string[]>();
    for (const c of f.changes) {
      if (!c.operatorId || c.kind === "operator_deleted") {
        continue;
      }
      const list = notes.get(c.operatorId) ?? [];
      list.push(c.text);
      notes.set(c.operatorId, list);
    }
    this.boxes = f.operators.map(o => {
      const touched = notes.get(o.id) ?? [];
      return {
        op: o,
        x: o.x,
        y: o.y,
        mark: added.has(o.id) ? "added" : touched.length ? "edited" : "untouched",
        note: [`${o.id} · ${o.type}`, ...touched].join("\n"),
      };
    });
    const placed = new Map(this.boxes.map(b => [b.op.id, b]));
    this.arrows = [];
    for (const link of f.links) {
      const from = placed.get(link.from);
      const to = placed.get(link.to);
      if (from && to) {
        this.arrows.push({
          x1: from.x + BOX_WIDTH,
          y1: from.y + BOX_HEIGHT / 2,
          x2: to.x,
          y2: to.y + BOX_HEIGHT / 2,
        });
      }
    }
    if (this.focused && !placed.has(this.focused)) {
      this.focused = null;
    }
    // Everything the workflow will end up with but does not have yet, drawn as
    // an outline. Otherwise the early frames are a nearly empty screen and the
    // fixed camera looks like a bug.
    const final = this.frames[this.last]?.operators ?? [];
    this.ghosts = final.filter(o => !placed.has(o.id)).map(o => ({ id: o.id, x: o.x, y: o.y }));
  }

  public goTo(i: number): void {
    this.index = Math.max(0, Math.min(this.last, i));
    this.draw();
  }

  public previous(): void {
    this.stop();
    this.goTo(this.index - 1);
  }

  public next(): void {
    this.stop();
    this.goTo(this.index + 1);
  }

  public onScrub(value: string | number): void {
    this.stop();
    this.goTo(typeof value === "number" ? value : parseInt(value, 10));
  }

  public play(): void {
    if (this.playing) {
      this.stop();
      return;
    }
    if (this.index >= this.last) {
      this.goTo(0);
    }
    this.playing = true;
    this.clock = setInterval(() => {
      if (this.index >= this.last) {
        this.stop();
        return;
      }
      this.goTo(this.index + 1);
    }, FRAME_MS);
  }

  public stop(): void {
    this.playing = false;
    if (this.clock) {
      clearInterval(this.clock);
      this.clock = undefined;
    }
  }

  public focus(id: string): void {
    this.focused = this.focused === id ? null : id;
  }

  /** Rewinds to the first version where this operator was touched. */
  public rewindTo(id: string): void {
    const frames = this.frames;
    const found = frames.findIndex(f => f.changes.some(c => c.operatorId === id));
    if (found >= 0) {
      this.stop();
      this.goTo(found);
    }
  }

  // ---------- the autopsy ----------

  /**
   * The written-out reading of the record. The panel never waits for it: the
   * derived text stands on its own and this arrives underneath when it can.
   * Facts are worked out here; the model only puts them into words.
   */
  /**
   * What the model writes about the comparison: a headline, one sentence per
   * step and a closing line. The rules still decide which step is to blame and
   * how many rows moved; this only puts that into words, so that nobody has to
   * translate a row count into what it means for their data.
   */
  public narration: { headline: string; steps: Record<string, string>; takeaway: string } = {
    headline: "",
    steps: {},
    takeaway: "",
  };
  public narrationPending = false;

  public reading: { footage?: string; autopsy?: string } = {};
  public readingPending = { footage: false, autopsy: false };

  /** Pide la lectura de la comparacion: titular, una frase por paso y final. */
  private narrate(): void {
    this.narration = { headline: "", steps: {}, takeaway: "" };
    this.narrationPending = true;
    this.http
      .get<{ headline: string; steps: Record<string, string>; takeaway: string }>(
        `${SPY_API}/narrate?wid=${this.wid}&a=${this.eidA}&b=${this.eidB}`
      )
      .pipe(untilDestroyed(this))
      .subscribe({
        next: r => {
          this.narration = { headline: r.headline ?? "", steps: r.steps ?? {}, takeaway: r.takeaway ?? "" };
          this.narrationPending = false;
        },
        // With no key or no server there are no sentences, and the panel keeps
        // what it worked out on its own: the counts and the step to blame.
        error: () => (this.narrationPending = false),
      });
  }

  /** La frase escrita para un paso, si llego. */
  public sentenceFor(id: string): string {
    return this.narration.steps[id] ?? "";
  }

  private readOut(which: "footage" | "autopsy", query: string): void {
    if (this.reading[which] || this.readingPending[which]) {
      return;
    }
    this.readingPending[which] = true;
    this.http
      .get<{ text: string }>(`${SPY_API}/explain?${query}`)
      .pipe(untilDestroyed(this))
      .subscribe({
        next: r => {
          this.reading[which] = (r.text ?? "").trim() || undefined;
          this.readingPending[which] = false;
        },
        // Silence is the right failure here: no key, no server, no paragraph.
        error: () => (this.readingPending[which] = false),
      });
  }

  // ---------- asking it things ----------

  /** What has been asked and answered, kept per tab so they do not mix. */
  public chat: Record<string, SpyTurn[]> = { brief: [], footage: [], experiments: [], autopsy: [] };
  public draft = "";
  public asking = false;

  /** Which set of facts a question is answered from, per tab. */
  private get asked(): string {
    return this.tab === "report" ? "brief" : this.tab;
  }

  public get turns(): SpyTurn[] {
    return this.chat[this.asked] ?? [];
  }

  /** Openers, for the person who does not know what can be asked. */
  public get suggestions(): string[] {
    const asked = new Set(this.turns.map(t => t.question));
    if (this.tab === "brief") {
      const output = this.steps.filter(x => x.role === "output")[0];
      return [
        "What does this workflow actually answer?",
        output ? `Where do the rows in ${output.name} come from?` : "Which step decides how many rows come out?",
        "Which settings here are the ones worth changing?",
        "Is there anything in this workflow that does nothing?",
      ].filter(q => !asked.has(q));
    }
    if (this.tab === "experiments") {
      const knob = this.knobs[0];
      return [
        knob ? `What did changing ${knob.step} do to the results?` : "What has actually been varied between runs?",
        "Which of these runs are the same experiment repeated?",
        "Has anything been tried that made no difference at all?",
        "What has never been tried here?",
      ].filter(q => !asked.has(q));
    }
    if (this.tab === "autopsy") {
      const a = this.autopsy;
      if (!a) {
        return [];
      }
      const step = a.firstDivergent ? this.opName(a.firstDivergent) : "the first step that differs";
      return [
        `What kind of rows did ${step} stop letting through?`,
        "Which of the two runs should I trust for my question?",
        "Do the row counts add up from one step to the next?",
        "What else changed besides the edit you found?",
      ].filter(q => !asked.has(q));
    }
    return [
      "What was this workflow built to answer?",
      "Where did I change my mind while building it?",
      "Is there anything here that does nothing?",
      "What was the last real change, ignoring the automatic saves?",
    ].filter(q => !asked.has(q));
  }

  public askAbout(question: string): void {
    const text = (question ?? "").trim();
    if (!text || this.asking) {
      return;
    }
    const turn: SpyTurn = { question: text };
    const where = this.asked;
    this.chat[where] = this.chat[where] ?? [];
    this.chat[where].push(turn);
    this.draft = "";
    this.asking = true;
    const body: Record<string, unknown> = {
      wid: this.wid,
      question: text,
      // The facts a question is answered from are the ones on screen: the
      // walkthrough, the experiments table, the comparison or the history.
      mode: where === "footage" ? "history" : where,
      previous: this.chat[where].filter(t => t.answer).map(t => ({ question: t.question, answer: t.answer })),
    };
    if (where === "autopsy" && this.autopsy) {
      body["a"] = this.autopsy.a.eid;
      body["b"] = this.autopsy.b.eid;
    }
    this.http
      .post<{ text: string }>(`${SPY_API}/ask`, body)
      .pipe(untilDestroyed(this))
      .subscribe({
        next: r => {
          turn.answer = (r.text ?? "").trim() || "No answer came back.";
          this.asking = false;
        },
        error: () => {
          turn.failed = true;
          this.asking = false;
        },
      });
  }

  /** The reading of one paragraph at a time, so it lays out as prose. */
  public paragraphs(text: string | undefined): string[] {
    return (text ?? "").split(/\n\s*\n/).filter(p => p.trim().length > 0);
  }

  /**
   * Fills the list of runs. Only the ones whose rows were kept can be compared,
   * so those are the ones the two pickers offer.
   */
  private loadRuns(): Observable<SpyRun[]> {
    return this.http.get<{ runs: SpyRun[] }>(`${SPY_API}/executions?wid=${this.wid}`).pipe(
      map(r => r.runs),
      tap(runs => {
        this.runs = runs;
        this.comparable = runs.filter(run => run.snapshot);
      })
    );
  }

  /** The two runs the panel opens with: the last two, which is what people ask about. */
  private pickLastTwo(): void {
    if (this.comparable.length >= 2) {
      this.eidA = this.comparable[this.comparable.length - 2].eid;
      this.eidB = this.comparable[this.comparable.length - 1].eid;
    }
  }

  /**
   * Runs the workflow and keeps every step's rows, so this run can be compared.
   *
   * Texera throws a run's results away 30 seconds after the workflow goes
   * quiet, which is why the list of comparable runs is shorter than the list of
   * runs. The engine's synchronous route hands the rows back in its own reply,
   * so they are written down on the way past. Nothing here disables or delays
   * that cleanup.
   *
   * The workflow is saved first on purpose. A run is recorded against whichever
   * version was current when it started, so running with unsaved edits would
   * have the autopsy blame an edit that never ran.
   */
  public runAndKeep(): void {
    if (this.keeping) {
      return;
    }
    const cuid = this.computingUnits.getSelectedComputingUnitValue()?.computingUnit.cuid;
    if (cuid === undefined) {
      this.keptError = "choose a computing unit first. This run goes to the same engine as the Run button.";
      return;
    }
    const token = AuthService.getAccessToken();
    if (!token) {
      this.keptError = "your session has no token, so the run cannot be asked for on your behalf.";
      return;
    }
    this.keeping = true;
    this.keptError = undefined;
    this.kept = undefined;
    this.keepingStep = "Saving the workflow, so the run is recorded against what you have on screen…";
    this.workflowPersist
      .persistWorkflow(this.workflowAction.getWorkflow())
      .pipe(
        tap((saved: Workflow) => this.workflowAction.setWorkflowMetadata(saved)),
        switchMap(() => {
          this.keepingStep = "Running every step and keeping its rows. A full run takes as long as it takes…";
          return this.http.post<SpyKept>(`${SPY_API}/run`, {
            wid: this.wid,
            cuid,
            token,
            name: "kept from the spy panel",
          });
        }),
        switchMap(kept => {
          this.kept = kept;
          if (!kept.kept) {
            return of([] as SpyRun[]);
          }
          this.keepingStep = "Adding it to the list…";
          return this.loadRuns();
        }),
        untilDestroyed(this)
      )
      .subscribe({
        next: () => {
          this.keeping = false;
          this.keepingStep = "";
          const eid = this.kept?.eid;
          if (!this.kept?.kept || eid === undefined) {
            this.keptError = this.kept?.error ?? "the run did not finish, so nothing was kept.";
            return;
          }
          // The new run becomes the later side of the comparison, and the one
          // kept before it the earlier side. That is the comparison someone who
          // just pressed this button wants to see.
          const spot = this.comparable.findIndex(r => r.eid === eid);
          this.eidB = eid;
          if (spot > 0) {
            this.eidA = this.comparable[spot - 1].eid;
            this.compare();
          }
        },
        error: (e: unknown) => {
          this.keeping = false;
          this.keepingStep = "";
          this.keptError = this.explain(e);
        },
      });
  }

  public compare(): void {
    if (this.eidA === undefined || this.eidB === undefined) {
      return;
    }
    this.loadingAutopsy = true;
    this.autopsy = undefined;
    this.expanded.clear();
    this.http
      .get<SpyAutopsy>(`${SPY_API}/autopsy?wid=${this.wid}&a=${this.eidA}&b=${this.eidB}`)
      .pipe(untilDestroyed(this))
      .subscribe({
        next: r => {
          this.autopsy = r;
          this.loadingAutopsy = false;
          if (r.firstDivergent) {
            this.expanded.add(r.firstDivergent);
          }
          this.narrate();
        },
        error: (e: unknown) => {
          this.loadingAutopsy = false;
          this.error = this.explain(e);
        },
      });
  }

  public finding(id: string): SpyFinding | undefined {
    return this.autopsy?.findings.find(f => f.operatorId === id);
  }

  public isExpanded(id: string): boolean {
    return this.expanded.has(id);
  }

  public toggle(id: string): void {
    if (!this.finding(id)?.diff) {
      return;
    }
    if (this.expanded.has(id)) {
      this.expanded.delete(id);
    } else {
      this.expanded.add(id);
    }
  }

  /** How many operators came out clean, for the summary line. */
  public get cleanCount(): number {
    return (this.autopsy?.findings ?? []).filter(f => !f.diff).length;
  }

  /** The finding in plain words, so nobody has to read the table to get it. */
  /**
   * The two numbers worth reading before anything else. What was changed is a
   * card of its own below, drawn rather than counted, so this stops saying it.
   */
  public get figures(): { value: string; label: string; tone: string }[] {
    const a = this.autopsy;
    if (!a?.firstDivergent) {
      return [];
    }
    const out: { value: string; label: string; tone: string }[] = [];

    const diff = this.finding(a.firstDivergent)?.diff;
    const delta = diff ? diff.rowsB - diff.rowsA : 0;
    out.push(
      delta === 0
        ? { value: "same", label: "row count, but different rows", tone: "flat" }
        : {
            value: this.count(Math.abs(delta)),
            label: delta < 0 ? "rows stopped coming through" : "rows arrived that did not before",
            tone: "loud",
          }
    );

    // A step that returns the same rows in a different order does not count as
    // different: the order comes from how work was split between workers, not
    // from the edit.
    const affected = a.findings.filter(f => f.diff && !f.diff.sameSetDifferentOrder).length;
    out.push({ value: `${affected} of ${a.findings.length}`, label: "steps left different", tone: "flat" });

    return out;
  }

  // ---------- the comparison, drawn ----------

  /**
   * The two runs as a picture: one row per step, a bar each for how many rows
   * came out of it, and a mark on the first step whose bars stop matching.
   * Reading it needs no numbers at all, which is the point.
   */
  public get pipeline(): PipelineStep[] {
    const a = this.autopsy;
    if (!a) {
      return [];
    }
    const top = Math.max(1, ...a.findings.flatMap(f => [f.rowsA ?? 0, f.rowsB ?? 0]));
    // A straight share of the largest step, so the narrowing of the pipe is the
    // real one. A floor keeps the last steps, which end in a handful of rows,
    // from disappearing altogether; their counts are printed beside them.
    const bar = (n: number) => (n <= 0 ? 0 : Math.max(1.2, (n / top) * 100));
    const firstAt = a.firstDivergent ? a.order.indexOf(a.firstDivergent) : -1;
    return a.order.map((oid, i) => {
      const found = this.finding(oid);
      const rowsA = found?.rowsA ?? 0;
      const rowsB = found?.rowsB ?? 0;
      const state: PipelineStep["state"] = !found?.diff
        ? "same"
        : i === firstAt
          ? "starts"
          : found.diff.sameSetDifferentOrder
            ? "reordered"
            : "carried";
      return {
        id: oid,
        position: i + 1,
        name: this.opName(oid),
        rowsA,
        rowsB,
        barA: bar(rowsA),
        barB: bar(rowsB),
        state,
        note: this.stepNote(state, rowsA, rowsB, found?.diff?.sameSetDifferentOrder ?? false),
        truncated: !!found?.truncated,
      };
    });
  }

  /** What one step's pair of bars says, in the words you would say out loud. */
  private stepNote(state: PipelineStep["state"], rowsA: number, rowsB: number, sameSet: boolean): string {
    if (state === "same") {
      return "same rows both times";
    }
    if (state === "reordered") {
      return "same rows, in a different order";
    }
    const moved = rowsB - rowsA;
    if (moved === 0) {
      return sameSet ? "same rows, different order" : "same amount, different rows";
    }
    return moved < 0 ? `${this.count(-moved)} fewer rows` : `${this.count(moved)} more rows`;
  }

  /** The name of each run, for the legend over the bars. */
  public get earlierName(): string {
    const a = this.autopsy;
    return a ? a.a.name || `run ${a.a.eid}` : "";
  }

  public get laterName(): string {
    const a = this.autopsy;
    return a ? a.b.name || `run ${a.b.eid}` : "";
  }

  // ---------- the edits, drawn ----------

  /** The filmstrip: every save as a block, coloured by what it did. */
  public get tape(): TapeBlock[] {
    return this.frames.map((f, i) => {
      const kinds = new Set(f.changes.map(c => c.kind));
      const tone: TapeBlock["tone"] = !f.recoverable
        ? "broken"
        : kinds.has("operator_deleted") || kinds.has("link_removed")
          ? "removed"
          : kinds.has("operator_added") || kinds.has("link_added")
            ? "added"
            : f.changes.length
              ? "edited"
              : "quiet";
      return { index: i, tone, ran: f.runs.length > 0, label: `${this.when(f.time)} · ${this.summaryOf(f)}` };
    });
  }

  /** One line about a save, for the tooltip of its block on the filmstrip. */
  private summaryOf(f: SpyFrame): string {
    if (!f.recoverable) {
      return "this moment can no longer be read back";
    }
    if (f.changes.length === 0) {
      return "nothing changed";
    }
    const first = this.tell(f.changes[0]).lead.trim();
    const rest = f.changes.length - 1;
    return rest > 0 ? `${first} (+${rest} more)` : first;
  }

  /** A small drawing for each kind of edit, so the list reads without reading. */
  public glyphOf(kind: string): string {
    switch (kind) {
      case "operator_added":
        return "M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6z";
      case "operator_deleted":
        return "M5 11h14v2H5z";
      case "link_added":
        return "M4 11h11V7.2L20 12l-5 4.8V13H4z";
      case "link_removed":
        return "M6.4 5 19 17.6 17.6 19 5 6.4zM17.6 5 19 6.4 6.4 19 5 17.6z";
      case "renamed":
        return "M12.4 3H5a2 2 0 0 0-2 2v7.4c0 .6.2 1.1.6 1.5l7.5 7.5a2 2 0 0 0 2.8 0l7.4-7.4a2 2 0 0 0 0-2.8L13.9 3.6a2 2 0 0 0-1.5-.6zM7.5 9.5a2 2 0 1 1 0-4 2 2 0 0 1 0 4z";
      case "property":
        return "M3 17.2V21h3.8L17.9 9.9l-3.8-3.8zM20.7 7.1a1 1 0 0 0 0-1.4l-2.4-2.4a1 1 0 0 0-1.4 0l-1.8 1.8 3.8 3.8z";
      default:
        return "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z";
    }
  }

  /** Which of the four colours an edit belongs to. */
  public toneOf(kind: string): string {
    if (kind === "operator_added" || kind === "link_added") {
      return "added";
    }
    if (kind === "operator_deleted" || kind === "link_removed") {
      return "removed";
    }
    if (kind === "property" || kind === "renamed") {
      return "edited";
    }
    return "quiet";
  }

  public get verdict(): string {
    const a = this.autopsy;
    if (!a) {
      return "";
    }
    if (!a.firstDivergent) {
      return "Both runs produced identical output at every operator, so nothing that changed between the two versions moved the data.";
    }
    const downstream = a.order.length - a.order.indexOf(a.firstDivergent) - 1;
    const inherited = downstream > 0 ? ", and every step after it carries that along" : "";
    return `It starts at ${this.opName(a.firstDivergent)}${inherited}.`;
  }

  /** The second half of the verdict: what was edited in between. */
  public get blame(): string {
    const a = this.autopsy;
    if (!a || !a.firstDivergent) {
      return "";
    }
    const first = this.when(this.timeOfVersion(a.a.vid));
    const second = this.when(this.timeOfVersion(a.b.vid));
    // When both runs come from the same minute, naming the time twice is noise.
    const opening = first === second ? "In between" : `Between ${first} and ${second}`;
    if (a.edits.length === 0) {
      return `${opening} you changed nothing on the canvas, so the cause is somewhere else: the incoming data, a random draw, or the engine itself.`;
    }
    const mine = a.edits.filter(e => e.operatorId === a.firstDivergent);
    if (a.edits.length === 1 && mine.length === 1) {
      return `${opening}, that step was the only thing you touched.`;
    }
    return `${opening} you made ${a.edits.length} changes, ${mine.length} of them on ${this.opName(a.firstDivergent)}.`;
  }

  /** Signed row difference for the table, or an em dash when both are absent. */
  public delta(id: string): string {
    const diff = this.finding(id)?.diff;
    if (!diff) {
      return "—";
    }
    const d = diff.rowsB - diff.rowsA;
    if (d === 0) {
      return "0";
    }
    return `${d > 0 ? "+" : "\u2212"}${this.count(Math.abs(d))}`;
  }

  // ---------- saying it in plain words ----------

  /** A duration in words: nobody reads "+540 s". */
  private spell(seconds: number): string {
    const unit = (n: number, word: string) => `${n} ${word}${n === 1 ? "" : "s"}`;
    if (seconds < 60) {
      return unit(seconds, "second");
    }
    if (seconds < 3600) {
      return unit(Math.round(seconds / 60), "minute");
    }
    if (seconds < 86400) {
      return unit(Math.round(seconds / 3600), "hour");
    }
    return unit(Math.round(seconds / 86400), "day");
  }

  /** A timestamp the way you would say it out loud. */
  public when(value: string): string {
    const at = new Date(value.replace(" ", "T"));
    if (isNaN(at.getTime())) {
      return value;
    }
    const clock = at.toTimeString().slice(0, 5);
    const midnight = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
    const days = Math.round((midnight(new Date()) - midnight(at)) / 86400000);
    if (days === 0) {
      return `today at ${clock}`;
    }
    if (days === 1) {
      return `yesterday at ${clock}`;
    }
    if (days < 7) {
      return `${at.toLocaleDateString("en-US", { weekday: "long" })} at ${clock}`;
    }
    return `${at.toLocaleDateString("en-US", { day: "numeric", month: "long" })} at ${clock}`;
  }

  /** The whole record in one or two sentences, before any detail. */
  public get story(): string {
    const frames = this.history?.frames ?? [];
    if (frames.length === 0) {
      return "";
    }
    const parsed = frames.map(f => Date.parse(f.time.replace(" ", "T"))).filter(t => !isNaN(t));
    let sessions = parsed.length ? 1 : 0;
    for (let i = 1; i < parsed.length; i++) {
      // Half an hour of silence reads as having come back to it later.
      if (parsed[i] - parsed[i - 1] > 30 * 60 * 1000) {
        sessions++;
      }
    }
    const started = this.when(frames[0].time);
    const touched = this.when(frames[frames.length - 1].time);
    const sitting = sessions === 1 ? "in one sitting" : `over ${sessions} separate sessions`;
    const runs = this.runs.length;
    const ran =
      runs === 0 ? "It has never been run." : runs === 1 ? "It has been run once." : `It has been run ${runs} times.`;
    return `You started this workflow ${started} and last changed it ${touched}, ${sitting}. ${ran}`;
  }

  /**
   * What the workflow is, in the only terms the record can vouch for: where
   * data comes in, how many steps it goes through, where it ends up. Drawn
   * rather than said, because three groups of boxes need no reading.
   */
  public get shapeCounts(): { sources: number; middle: number; outputs: number } | null {
    const last = this.history?.frames.filter(f => f.recoverable).pop();
    const operators = last?.operators ?? [];
    if (operators.length === 0) {
      return null;
    }
    const fed = new Set((last?.links ?? []).map(l => l.to));
    const feeding = new Set((last?.links ?? []).map(l => l.from));
    const sources = operators.filter(o => !fed.has(o.id)).length;
    const outputs = operators.filter(o => !feeding.has(o.id)).length;
    return { sources, middle: operators.length - sources - outputs, outputs };
  }

  /** Counting to n, for drawing n little boxes in a template. */
  public range(n: number): number[] {
    return Array.from({ length: Math.max(0, n) }, (_, i) => i);
  }

  /** The record in three numbers, read before the sentence underneath. */
  public get recordFigures(): { value: string; label: string; tone: string }[] {
    const h = this.history;
    if (!h) {
      return [];
    }
    const last = h.frames.filter(f => f.recoverable).pop();
    const out = [
      { value: this.count(h.total), label: "times you saved this workflow", tone: "flat" },
      { value: this.count(last?.operators.length ?? 0), label: "steps on the canvas now", tone: "flat" },
      {
        value: this.count(this.runs.length),
        label: this.runs.length === 1 ? "run on record" : "runs on record",
        tone: "flat",
      },
    ];
    if (h.broken) {
      out.push({
        value: this.count(h.total - h.recovered),
        label: "versions Texera can no longer rebuild",
        tone: "loud",
      });
    }
    return out;
  }

  /** The name shown on the canvas for an operator, at this point in time. */
  public nameOf(id: string | undefined, frame = this.frame): string {
    if (!id) {
      return "an operator";
    }
    const operator = frame?.operators.find(o => o.id === id);
    if (!operator) {
      return id;
    }
    // Two operators can carry the same name until they are renamed; the id is
    // the only thing that tells them apart.
    const twins = frame?.operators.filter(o => o.name === operator.name).length ?? 1;
    return twins > 1 ? `${operator.name} (${operator.id})` : operator.name;
  }

  /**
   * Where a link lands. An operator with two inputs, like a join, is two
   * different places on the canvas, so the input gets named.
   */
  private inputOf(id: string | undefined, port: string | undefined): string {
    const name = this.nameOf(id);
    if (!id || !port) {
      return name;
    }
    const inputs = this.frame?.operators.find(o => o.id === id)?.inputs ?? [];
    if (inputs.length < 2) {
      return name;
    }
    const index = Number(port.replace(/\D/g, ""));
    if (isNaN(index) || index < 0 || index >= inputs.length) {
      return name;
    }
    // Most ports have no display name of their own; then it is just the order.
    return inputs[index] ? `the ${inputs[index]} input of ${name}` : `the ${this.ordinal(index + 1)} input of ${name}`;
  }

  /** Small ordinals in words, because "the 2nd input" reads like a form. */
  private ordinal(position: number): string {
    return ["first", "second", "third", "fourth", "fifth"][position - 1] ?? `${position}th`;
  }

  /** The name Texera puts in the operator palette, e.g. "Hash Join". */
  private friendlyType(operatorType: string | undefined): string {
    if (!operatorType) {
      return "an operator";
    }
    try {
      return this.metadata.getOperatorSchema(operatorType).additionalMetadata.userFriendlyName || operatorType;
    } catch {
      return operatorType;
    }
  }

  /** camelCase field names, when the schema has no title of its own. */
  private humanize(segment: string): string {
    const spaced = segment.replace(/([a-z0-9])([A-Z])/g, "$1 $2").replace(/[_-]+/g, " ");
    return spaced.charAt(0).toUpperCase() + spaced.slice(1);
  }

  /**
   * Turns a JSON pointer into the labels the person actually saw in the
   * operator's form, by walking the same schema the form is built from.
   */
  private labelFor(operatorType: string | undefined, path: string | undefined): string {
    if (!path) {
      return "a setting";
    }
    let node: CustomJSONSchema7 | undefined;
    let root: CustomJSONSchema7 | undefined;
    if (operatorType) {
      try {
        root = this.metadata.getOperatorSchema(operatorType).jsonSchema as CustomJSONSchema7;
        node = root;
      } catch {
        node = undefined;
      }
    }
    const deref = (schema: unknown): CustomJSONSchema7 | undefined => {
      const s = schema as CustomJSONSchema7 & { $ref?: string };
      if (!s) {
        return undefined;
      }
      if (s.$ref && root) {
        const key = s.$ref.replace("#/definitions/", "");
        return (root as { definitions?: Record<string, CustomJSONSchema7> }).definitions?.[key];
      }
      return s;
    };

    const parts: string[] = [];
    for (const segment of path.split("/").filter(Boolean)) {
      if (/^\d+$/.test(segment)) {
        // An index belongs to the list above it: "Predicates" + 0 reads as
        // "Predicate 1", which is what the form shows.
        const list = parts.pop() ?? "item";
        parts.push(`${list.replace(/s$/, "")} ${Number(segment) + 1}`);
        node = deref((node as { items?: unknown })?.items);
        continue;
      }
      const property = (node as { properties?: Record<string, CustomJSONSchema7> })?.properties?.[segment];
      parts.push(property?.title ?? this.humanize(segment));
      node = deref(property);
    }
    if (parts.length === 0) {
      return "a setting";
    }
    const leaf = parts[parts.length - 1];
    const rest = parts.slice(0, -1);
    return rest.length ? `${leaf} of ${rest.join(", ")}` : leaf;
  }

  /**
   * A setting's value said the way its own form says it: a filter row reads
   * "amount > 100", not the JSON the patch happens to store.
   */
  private describe(value: unknown): string {
    if (value === null || value === undefined) {
      return "nothing";
    }
    if (value === "") {
      return "empty";
    }
    if (Array.isArray(value)) {
      return value.length === 0 ? "nothing" : value.map(entry => this.describeEntry(entry)).join("; ");
    }
    if (typeof value === "object") {
      return this.describeEntry(value);
    }
    return String(value);
  }

  /** One row of a setting that holds a list, e.g. one filter condition. */
  private describeEntry(value: unknown): string {
    if (value === null || value === undefined) {
      return "nothing";
    }
    if (typeof value !== "object") {
      return String(value);
    }
    const row = value as Record<string, unknown>;
    const has = (...keys: string[]) => keys.every(key => row[key] !== undefined && row[key] !== "");

    // A filter condition, as the operator's own row reads: amount > 100.
    if (has("attribute", "condition")) {
      return `${row["attribute"]} ${row["condition"]} ${row["value"] ?? ""}`.trim();
    }
    // An aggregation, named after the column it produces.
    if (has("aggFunction", "attribute")) {
      const named = row["result attribute"] ?? row["resultAttribute"];
      return `${row["aggFunction"]} of ${row["attribute"]}${named ? `, as ${named}` : ""}`;
    }
    // A sort key, said as a direction rather than as DESC.
    if (has("attribute", "sortPreference")) {
      const descending = String(row["sortPreference"]).toUpperCase().startsWith("DESC");
      return `${row["attribute"]}, ${descending ? "highest first" : "lowest first"}`;
    }
    // Anything else: its own fields, labelled the way the form labels them.
    const parts = Object.entries(row)
      .filter(([, field]) => field !== "" && field !== null && field !== undefined)
      .map(([key, field]) => `${this.humanize(key).toLowerCase()} ${this.clip(field, 40)}`);
    return parts.length ? parts.join(", ") : "nothing";
  }

  /** A setting's value for the screen: described first, then cut to size. */
  public said(value: unknown, max = 60): string {
    return this.clip(this.describe(value), max);
  }

  /** Neither value is visible anywhere, so the save changed nothing on screen. */
  private invisible(text: string): boolean {
    return text === "nothing" || text === "empty";
  }

  /** A raw value as a reader would expect to see it. */
  private readable(value: string | undefined): string {
    if (value === undefined || value === "null") {
      return "nothing";
    }
    if (value === '""' || value === "") {
      return "empty";
    }
    return value;
  }

  /** One saved change, written the way the person who made it would say it. */
  public tell(change: SpyChange): Told {
    const raw = change.text;
    switch (change.kind) {
      case "operator_added":
        return {
          lead: `You added ${this.friendlyType(change.operatorType)}${
            change.name && change.name !== change.operatorType ? `, called "${change.name}"` : ""
          }.`,
          detail: raw,
        };
      case "operator_deleted":
        return {
          lead: `You deleted ${this.friendlyType(change.operatorType)}${
            change.name && change.name !== change.operatorType ? `, called "${change.name}"` : ""
          }.`,
          detail: raw,
        };
      case "link_added":
        return {
          lead: `You connected ${this.nameOf(change.from)} into ${this.inputOf(change.to, change.port)}.`,
          detail: raw,
        };
      case "link_removed":
        return {
          lead: `You disconnected ${this.nameOf(change.from)} from ${this.inputOf(change.to, change.port)}.`,
          detail: raw,
        };
      case "renamed":
        return {
          lead: "You renamed",
          before: this.readable(change.before),
          join: "to",
          after: this.readable(change.after),
          detail: raw,
        };
      case "property": {
        const where = this.nameOf(change.operatorId);
        const setting = this.labelFor(change.operatorType, change.path);
        // The value itself when it came through, the clipped text otherwise.
        const was = "beforeValue" in change ? this.describe(change.beforeValue) : this.readable(change.before);
        const now = "afterValue" in change ? this.describe(change.afterValue) : this.readable(change.after);
        if (this.invisible(was) && this.invisible(now)) {
          // Empty rewritten as absent, or the other way round. Texera saved it,
          // but nobody would see a difference on the canvas.
          return { lead: `In ${where}, ${setting} was written again, with nothing to show either way.`, detail: raw };
        }
        if (this.invisible(was)) {
          return { lead: `In ${where}, you set ${setting} to`, after: now, detail: raw };
        }
        if (this.invisible(now)) {
          return { lead: `In ${where}, you cleared ${setting}, which was`, before: was, detail: raw };
        }
        return { lead: `In ${where}, ${setting} went from`, before: was, join: "to", after: now, detail: raw };
      }
      case "moved":
        return { lead: `You moved ${this.nameOf(change.operatorId)} on the canvas.`, detail: raw };
      default:
        return { lead: raw.charAt(0).toUpperCase() + raw.slice(1) + ".", detail: raw };
    }
  }

  /** The label a setting has in the operator's own form. */
  public settingLabel(edit: SpyEdit): string {
    return this.labelFor(this.typeOf(edit.operatorId), edit.path);
  }

  /** When the readable part of the record stops, for the warning sentence. */
  public get brokenTime(): string {
    const vid = this.history?.broken?.vid;
    return vid === undefined ? "" : this.timeOfVersion(vid);
  }

  /** When a given version was saved, for sentences that talk about time. */
  private timeOfVersion(vid: number): string {
    return this.history?.frames.find(f => f.vid === vid)?.time ?? "";
  }

  /** The operator type behind an id, needed to read its form labels. */
  private typeOf(id: string): string | undefined {
    const frames = this.history?.frames ?? [];
    for (let i = frames.length - 1; i >= 0; i--) {
      const found = frames[i].operators.find(o => o.id === id);
      if (found) {
        return found.type;
      }
    }
    return undefined;
  }

  /** The canvas name of an operator as it stood in the run being compared. */
  public opName(id: string): string {
    const vid = this.autopsy?.b.vid;
    const frames = this.history?.frames ?? [];
    const frame = frames.find(f => f.vid === vid) ?? frames[frames.length - 1];
    return frame?.operators.find(o => o.id === id)?.name ?? id;
  }

  /** Column headers for a sample of rows, in the order the engine returned. */
  public columns(rows: object[]): string[] {
    const seen: string[] = [];
    for (const row of rows) {
      for (const key of Object.keys(row ?? {})) {
        if (!seen.includes(key)) {
          seen.push(key);
        }
      }
    }
    return seen;
  }

  public cell(row: object, key: string): string {
    const value = (row as Record<string, unknown>)[key];
    if (value === null || value === undefined) {
      return "";
    }
    return typeof value === "object" ? JSON.stringify(value) : String(value);
  }

  // ---------- the walk-through: how this thing works ----------

  /**
   * The tour is one call to the model, asked for as soon as the facts are in.
   * It is a sentence per step and nothing else: what each step does is derived
   * from its settings, and the sentence only says it in words a newcomer reads.
   */
  private walkThrough(): void {
    this.tourPending = true;
    this.http
      .get<Tour>(`${SPY_API}/tour?wid=${this.wid}`)
      .pipe(untilDestroyed(this))
      .subscribe({
        next: t => {
          this.tour = { ...t, steps: t.steps ?? {} };
          this.tourPending = false;
        },
        // No key, no server, no sentences. The walkthrough stands without them.
        error: () => (this.tourPending = false),
      });
  }

  /** Moving to a tab that costs a fetch only pays for it when it is opened. */
  public show(tab: SpyTab): void {
    this.tab = tab;
    this.stop();
    if (tab === "experiments" && !this.experiments && !this.loadingExperiments) {
      this.loadExperiments();
    }
  }

  public get steps(): BriefStep[] {
    return this.brief?.steps ?? [];
  }

  // The flow drawn as what it is: pipes carrying rows. The thickness of a pipe
  // is how much data runs through it, so where the flow narrows is seen before
  // a single number is read. Texera's own canvas positions are not used: they
  // are wherever somebody dropped the boxes, and a diagram has to be laid out.
  public flowNodes: FlowNode[] = [];
  public flowPipes: FlowPipe[] = [];
  public flowViewBox = "0 0 900 320";
  public readonly nodeWidth = NODE_WIDTH;
  public readonly nodeHeight = NODE_HEIGHT;

  /** The step being looked at. The flow is walked one step at a time. */
  public picked: string | null = null;

  private layOut(): void {
    const steps = this.steps;
    if (steps.length === 0) {
      this.flowNodes = [];
      this.flowPipes = [];
      return;
    }
    // A step sits one column to the right of the furthest step feeding it, so
    // the data always flows left to right however the canvas was arranged.
    const column = new Map<string, number>();
    for (const step of steps) {
      const feeders = step.fedBy.map(f => column.get(f.id) ?? 0);
      column.set(step.id, feeders.length ? Math.max(...feeders) + 1 : 0);
    }
    const columns = new Map<number, BriefStep[]>();
    for (const step of steps) {
      const at = column.get(step.id) ?? 0;
      columns.set(at, [...(columns.get(at) ?? []), step]);
    }
    const tallest = Math.max(...[...columns.values()].map(list => list.length));
    const top = Math.max(1, ...steps.map(step => step.rowsOut ?? 0));

    this.flowNodes = [];
    for (const [at, list] of columns) {
      const offset = ((tallest - list.length) * (NODE_HEIGHT + NODE_GAP_Y)) / 2;
      list.forEach((step, i) => {
        const kind = this.kindOf(step);
        const rowsIn = step.rowsIn ?? 0;
        const rowsOut = step.rowsOut ?? 0;
        this.flowNodes.push({
          step,
          x: at * (NODE_WIDTH + NODE_GAP_X),
          y: offset + i * (NODE_HEIGHT + NODE_GAP_Y),
          kind,
          glyph: this.glyphForKind(kind),
          rows: step.rowsOut,
          // Only where throwing rows away is the whole job. An aggregate turns
          // 668 rows into 3 groups and nothing was dropped, so marking that in
          // red would be a lie told in colour.
          drop: DISCARDS.has(kind) && rowsIn > 0 && rowsOut < rowsIn * 0.9 ? rowsIn - rowsOut : 0,
        });
      });
    }

    const placed = new Map(this.flowNodes.map(node => [node.step.id, node]));
    this.flowPipes = [];
    for (const step of steps) {
      for (const feeder of step.fedBy) {
        const from = placed.get(feeder.id);
        const to = placed.get(step.id);
        if (!from || !to) {
          continue;
        }
        const x1 = from.x + NODE_WIDTH;
        const y1 = from.y + NODE_HEIGHT / 2;
        const x2 = to.x;
        const y2 = to.y + NODE_HEIGHT / 2;
        const bend = Math.max(24, (x2 - x1) / 2);
        const rows = from.step.rowsOut ?? 0;
        this.flowPipes.push({
          path: `M ${x1} ${y1} C ${x1 + bend} ${y1}, ${x2 - bend} ${y2}, ${x2} ${y2}`,
          // Square root, not a straight share: a pipe ten times thicker than
          // another is unreadable, and the eye reads area anyway.
          width: rows <= 0 ? 1.5 : 1.5 + 13 * Math.sqrt(rows / top),
          rows,
          midX: (x1 + x2) / 2,
          midY: (y1 + y2) / 2 - 7,
        });
      }
    }
    const width = (Math.max(...columns.keys()) + 1) * (NODE_WIDTH + NODE_GAP_X) - NODE_GAP_X;
    const height = tallest * (NODE_HEIGHT + NODE_GAP_Y) - NODE_GAP_Y;
    this.flowViewBox = `-10 -14 ${width + 20} ${height + 28}`;
    if (!this.picked || !placed.has(this.picked)) {
      this.picked = steps[0].id;
    }
  }

  /**
   * What a step is, as a picture rather than a class name. Texera has 166
   * operator types and a newcomer knows none of them, but everyone knows a
   * funnel keeps some things and drops others.
   */
  private kindOf(step: BriefStep): string {
    const type = (step.type || "").toLowerCase();
    if (step.role === "source" || type.includes("source") || type.includes("scan")) {
      return "source";
    }
    if (type.includes("filter")) {
      return "filter";
    }
    if (type.includes("join")) {
      return "join";
    }
    if (type.includes("aggregate") || type.includes("count") || type.includes("sum")) {
      return "aggregate";
    }
    if (type.includes("sort")) {
      return "sort";
    }
    if (type.includes("limit")) {
      return "limit";
    }
    if (type.includes("projection") || type.includes("select")) {
      return "projection";
    }
    if (type.includes("udf") || type.includes("python") || type.includes("java")) {
      return "code";
    }
    return step.role === "output" ? "output" : "step";
  }

  public glyphForKind(kind: string): string {
    switch (kind) {
      case "source":
        return "M12 4c4 0 7 1 7 2.2S16 8.4 12 8.4 5 7.4 5 6.2 8 4 12 4zM5 9.2c1.5.9 4.2 1.4 7 1.4s5.5-.5 7-1.4v3.4c0 1.2-3 2.2-7 2.2s-7-1-7-2.2zm0 6.6c1.5.9 4.2 1.4 7 1.4s5.5-.5 7-1.4v2c0 1.2-3 2.2-7 2.2s-7-1-7-2.2z";
      case "filter":
        return "M3.5 5h17l-6.8 8v6.2l-3.4 1.8V13z";
      case "join":
        return "M3 5.6h4.6l3.6 5.2h9.6v2.4h-9.6l-3.6 5.2H3v-2.4h3.4l3-4L6.4 8H3z";
      case "aggregate":
        return "M4 19h3.4v-7H4zm6.3 0h3.4V5h-3.4zM16.6 19H20v-4.4h-3.4z";
      case "sort":
        return "M7 3.6l3.4 4.4H8.2v12H5.8V8H3.6zM17 20.4 13.6 16h2.2V4h2.4v12h2.2z";
      case "limit":
        return "M4 5.6h16V8H4zm0 5.2h11v2.4H4zm0 5.2h6v2.4H4z";
      case "projection":
        return "M4 5h4.4v14H4zm6.8 0h4.4v14h-4.4zm6.8 0H20v14h-2.4z";
      case "code":
        return "M9.4 16.6 4.8 12l4.6-4.6L8 6l-6 6 6 6zm5.2 0 4.6-4.6-4.6-4.6L16 6l6 6-6 6z";
      case "output":
        return "M5 19.6h14V22H5zM12 2v11.2l3.8-3.8 1.7 1.7-6.5 6.5-6.5-6.5 1.7-1.7L10 13.2V2z";
      default:
        return "M5 6h14v12H5z";
    }
  }

  /** The same reading of a step's kind, for the template. */
  public kindOfStep(step: BriefStep): string {
    return this.kindOf(step);
  }

  /** Whether a step has an input worth drawing: a source does not. */
  public hasInput(step: BriefStep): boolean {
    return step.role !== "source" && !!step.rowsIn;
  }

  /** The two bars of one step, drawn against the busiest step in the flow. */
  public inBar(step: BriefStep): number {
    return this.share(step.rowsIn ?? 0);
  }

  public outBar(step: BriefStep): number {
    return this.share(step.rowsOut ?? 0);
  }

  private share(rows: number): number {
    const top = Math.max(1, ...this.steps.flatMap(step => [step.rowsIn ?? 0, step.rowsOut ?? 0]));
    return rows <= 0 ? 0 : Math.max(1.2, (rows / top) * 100);
  }

  public pickStep(id: string): void {
    this.picked = id;
    this.opened = null;
  }

  public get pickedStep(): BriefStep | undefined {
    return this.steps.find(step => step.id === this.picked);
  }

  public get pickedAt(): number {
    return this.steps.findIndex(step => step.id === this.picked);
  }

  /** Walking the flow one step at a time, which is how it is meant to be read. */
  public stepAlong(by: number): void {
    const at = this.pickedAt;
    const next = this.steps[Math.max(0, Math.min(this.steps.length - 1, at + by))];
    if (next) {
      this.pickStep(next.id);
    }
  }

  /**
   * How much of what reached a step came out of it again, for the one line
   * that says what a filter or a join actually did to the data.
   */
  public keepsOf(step: BriefStep | undefined): string {
    if (!step || !step.rowsIn || step.rowsOut === null || step.rowsOut === undefined) {
      return "";
    }
    if (step.rowsOut === step.rowsIn) {
      return "every row that reached it came out again";
    }
    if (step.rowsOut < step.rowsIn) {
      const share = Math.round((step.rowsOut / step.rowsIn) * 100);
      return `${this.count(step.rowsIn - step.rowsOut)} of the ${this.count(step.rowsIn)} rows that reached it stopped here, leaving ${share} in every 100`;
    }
    return `it gave back more rows than it was given: ${this.count(step.rowsOut)} out of ${this.count(step.rowsIn)} in`;
  }

  /** Prose is folded by default: the drawing answers first, words come second. */
  public unfolded = new Set<string>();

  public unfold(key: string): void {
    if (this.unfolded.has(key)) {
      this.unfolded.delete(key);
    } else {
      this.unfolded.add(key);
    }
  }

  public isUnfolded(key: string): boolean {
    return this.unfolded.has(key);
  }

  /** The sentence written for one step, if one came back. */
  public stepTold(id: string): string {
    return this.tour.steps[id] ?? "";
  }

  public openStep(id: string): void {
    this.opened = this.opened === id ? null : id;
  }

  /**
   * What a step is set to do, in the words of its own form. A setting that
   * holds a whole program is not a phrase and never reads as one, so it is
   * counted in lines here and printed in full only when the step is opened.
   */
  public settingsOf(step: BriefStep): { label: string; text: string; big: boolean; raw?: string }[] {
    return step.settings.map(setting => {
      const label = this.labelFor(step.type, setting.path);
      if (!("value" in setting)) {
        return { label, text: this.lineCount(setting.lines ?? 0), big: true };
      }
      const written = this.describe(setting.value);
      const program = typeof setting.value === "string" && (written.includes("⏎") || written.length > 90);
      if (program) {
        const raw = setting.value as string;
        return { label, text: this.lineCount(raw.split("\n").length), big: true, raw };
      }
      return { label, text: written, big: false };
    });
  }

  private lineCount(lines: number): string {
    return `${this.count(lines)} ${lines === 1 ? "line" : "lines"} of it`;
  }

  /** The settings of a step that are worth printing in full when it is opened. */
  public programsOf(step: BriefStep): { label: string; raw: string }[] {
    return this.settingsOf(step)
      .filter((setting): setting is { label: string; text: string; big: boolean; raw: string } => !!setting.raw)
      .map(setting => ({ label: setting.label, raw: setting.raw }));
  }

  /** Where a step sits in the flow, said rather than coloured. */
  public roleWord(step: BriefStep): string {
    if (step.role === "source") {
      return "reads data in";
    }
    return step.role === "output" ? "ends the flow" : "passes data on";
  }

  /** How wide to draw the volume of a step, against the widest step there is. */
  public rowsBar(step: BriefStep): number {
    const top = Math.max(1, ...this.steps.map(x => x.rowsOut ?? 0));
    const rows = step.rowsOut ?? 0;
    return rows <= 0 ? 0 : Math.max(1.2, (rows / top) * 100);
  }

  /** The names of the steps feeding this one, for the line under its title. */
  public feedersOf(step: BriefStep): string {
    const names = step.fedBy.map(f => this.briefName(f.id));
    if (names.length === 0) {
      return "";
    }
    return `fed by ${names.join(" and ")}`;
  }

  public briefName(id: string): string {
    return this.steps.find(x => x.id === id)?.name ?? id;
  }

  /** The label a knob carries in its own form, e.g. "Value of Predicate 1". */
  public knobLabel(knob: BriefKnob | KnobDef): string {
    return this.labelFor(knob.type, knob.path);
  }

  public knobValue(knob: BriefKnob): string {
    return "value" in knob ? this.said(knob.value, 40) : "";
  }

  /** Who can open this workflow, in one line. */
  public get whoLine(): string {
    const people = this.brief?.people ?? [];
    if (people.length === 0) {
      return "";
    }
    const owner = people.find(p => p.owner);
    const others = people.filter(p => !p.owner).map(p => p.name);
    const rest = others.length
      ? `, shared with ${others.length === 1 ? others[0] : `${others.length} other people`}`
      : ", not shared with anyone";
    return `${owner ? `${owner.name} owns it` : "Nobody owns it"}${rest}.`;
  }

  /** The one line that says how alive this workflow is. */
  public get pulseLine(): string {
    const record = this.brief?.record;
    if (!record) {
      return "";
    }
    const built = record.started ? `Started ${this.when(record.started)}` : "";
    const last = record.lastEdited ? `, last changed ${this.when(record.lastEdited)}` : "";
    const ran = record.runs === 0 ? ". Never run." : `. Run ${this.count(record.runs)} times`;
    const lastRun = record.lastRun ? `, the last one ${this.when(record.lastRun)}.` : ".";
    return `${built}${last}${ran}${lastRun}`;
  }

  /** The three numbers of the walkthrough, read before any sentence. */
  public get briefFigures(): { value: string; label: string; tone: string }[] {
    const b = this.brief;
    if (!b) {
      return [];
    }
    const measured = b.steps.filter(x => x.rowsOut !== null && x.rowsOut !== undefined);
    const read = b.steps.filter(x => x.role === "source").reduce((n, x) => n + (x.rowsOut ?? 0), 0);
    const out = b.steps.filter(x => x.role === "output").reduce((n, x) => n + (x.rowsOut ?? 0), 0);
    const figures = [
      { value: this.count(b.steps.length), label: "steps in the flow", tone: "flat" },
      {
        value: this.count(b.record.runs),
        label: b.record.runs === 1 ? "run on record" : "runs on record",
        tone: "flat",
      },
    ];
    if (measured.length) {
      figures.unshift({ value: `${this.count(read)} → ${this.count(out)}`, label: "rows in, rows out", tone: "flat" });
    }
    return figures;
  }

  /** True while there is nothing at all to show in the walkthrough. */
  public get briefEmpty(): boolean {
    return !this.loadingBrief && this.steps.length === 0;
  }

  // ---------- what has been tried ----------

  private loadExperiments(): void {
    this.loadingExperiments = true;
    this.http
      .get<SpyExperiments>(`${SPY_API}/experiments?wid=${this.wid}`)
      .pipe(untilDestroyed(this))
      .subscribe({
        next: e => {
          this.experiments = e;
          this.loadingExperiments = false;
          this.trends = this.linesOverRuns();
        },
        error: (err: unknown) => {
          this.loadingExperiments = false;
          this.error = this.explain(err);
        },
      });
  }

  /**
   * Every run as a row: what the dials were set to, and what came out. Two runs
   * with the same dials and the same output are the same experiment run twice,
   * and saying so is half of what a newcomer needs from this table.
   */
  public get experimentRows(): ExperimentRow[] {
    const e = this.experiments;
    if (!e) {
      return [];
    }
    const top = Math.max(1, ...e.runs.map(r => r.produced));
    const seen = new Map<string, number>();
    return e.runs.map((run, i) => {
      const before = e.runs[i - 1];
      const values = e.knobs.map(knob => ({
        knob,
        text: knob.id in run.settings ? this.said(run.settings[knob.id], 40) : "—",
        changed:
          !!before &&
          JSON.stringify(before.settings[knob.id] ?? null) !== JSON.stringify(run.settings[knob.id] ?? null),
      }));
      // The fingerprint of an experiment is its dials plus what came out of it.
      const print = JSON.stringify([values.map(v => v.text), run.produced]);
      const first = seen.get(print);
      if (first === undefined) {
        seen.set(print, run.eid);
      }
      return {
        run,
        values,
        bar: run.produced <= 0 ? 0 : Math.max(1.2, (run.produced / top) * 100),
        move: e.moves.find(m => m.to === run.eid),
        repeatOf: first,
      };
    });
  }

  /** How many of the runs were genuinely different experiments. */
  public get distinctRuns(): number {
    return this.trials.length;
  }

  /**
   * The experiments themselves, which is not the same list as the runs: the
   * same settings run five times is one experiment, and saying so is most of
   * what someone wants from this table. Runs that produced a different result
   * from the same settings are kept apart, because that is worth seeing.
   */
  public get trials(): { values: { knob: KnobDef; text: string }[]; runs: ExperimentRun[]; produced: number }[] {
    const out: { key: string; values: { knob: KnobDef; text: string }[]; runs: ExperimentRun[]; produced: number }[] =
      [];
    for (const row of this.experimentRows) {
      const values = row.values.map(v => ({ knob: v.knob, text: v.text }));
      const key = JSON.stringify([values.map(v => v.text), row.run.produced]);
      const found = out.find(t => t.key === key);
      if (found) {
        found.runs.push(row.run);
      } else {
        out.push({ key, values, runs: [row.run], produced: row.run.produced });
      }
    }
    return out.map(({ values, runs, produced }) => ({ values, runs, produced }));
  }

  /**
   * One dial at a time: every value it was ever run at, and what came out.
   * This is the question behind the whole tab, and it is a picture of two or
   * three bars rather than a table anyone has to read across.
   */
  public get dials(): {
    knob: KnobDef;
    values: {
      text: string;
      runs: number;
      empty: number;
      low: number;
      high: number;
      bar: number;
      floor: number;
      mixed: boolean;
    }[];
    verdict: string;
  }[] {
    const rows = this.experimentRows;
    if (rows.length === 0) {
      return [];
    }
    const top = Math.max(1, ...rows.map(r => r.run.produced));
    return this.knobs.map(knob => {
      const byValue = new Map<string, number[]>();
      for (const row of rows) {
        const value = row.values.find(v => v.knob.id === knob.id)?.text ?? "—";
        byValue.set(value, [...(byValue.get(value) ?? []), row.run.produced]);
      }
      const values = [...byValue.entries()].map(([text, all]) => {
        // A run that produced nothing says nothing about the dial: it says the
        // run failed. Counted and named, but kept out of the bars.
        const produced = all.filter(rows => rows > 0);
        const empty = all.length - produced.length;
        const low = produced.length ? Math.min(...produced) : 0;
        const high = produced.length ? Math.max(...produced) : 0;
        const bar = (n: number) => (n <= 0 ? 0 : Math.max(1.2, (n / top) * 100));
        return {
          text,
          runs: all.length,
          empty,
          low,
          high,
          bar: bar(high),
          // The solid part is what every run at this value produced at least;
          // the pale part is how far apart those runs were.
          floor: bar(low),
          // The same value coming out differently means something else moved
          // in those runs, and the bar alone would be telling half a story.
          mixed: low !== high,
        };
      });
      const results = new Set(values.map(v => `${v.low}-${v.high}`));
      const verdict = values.some(v => v.mixed)
        ? "runs at the same value came out differently, so something else was moving too"
        : values.length < 2
          ? "only ever run at one value"
          : results.size === 1
            ? "the same number of rows came out whichever value was used"
            : "how many rows came out depended on this";
      return { knob, values, verdict };
    });
  }

  /** Whether the whole run log is unfolded under the experiments. */
  public showEveryRun = false;

  /** One trial's bar, against the biggest result any trial produced. */
  public trialBar(produced: number): number {
    const top = Math.max(1, ...this.trials.map(t => t.produced));
    return produced <= 0 ? 0 : Math.max(1.2, (produced / top) * 100);
  }

  /** When a trial was run, said once however many times it was repeated. */
  public trialWhen(runs: ExperimentRun[]): string {
    if (runs.length === 1) {
      return this.when(runs[0].started);
    }
    return `${runs.length} times, last ${this.when(runs[runs.length - 1].started)}`;
  }

  public get knobs(): KnobDef[] {
    return this.experiments?.knobs ?? [];
  }

  /** What changed between one run and the one before it, said in one line. */
  public moveText(move: RunMove | undefined): string {
    if (!move) {
      return "";
    }
    const said = move.edits.map(e => `${e.step}: ${this.said(e.before, 24)} → ${this.said(e.after, 24)}`);
    for (const change of move.structure) {
      if (change.kind === "added") {
        said.push(`${change.step} added`);
      } else if (change.kind === "removed") {
        said.push(`${change.step} removed`);
      } else if (change.kind === "renamed") {
        said.push(`${change.before || "a step"} renamed to ${change.step}`);
      } else {
        said.push(`${change.step} switched ${change.kind === "disabled" ? "off" : "on"}`);
      }
    }
    return said.join(" · ");
  }

  // ---------- the report ----------

  /**
   * The one thing here that is written rather than derived, and the one thing
   * that costs real money, so it is never asked for on its own: somebody has to
   * press the button. Everything in it comes from the same derived facts the
   * other tabs draw; the model only writes them up as a handover document.
   */
  public writeReport(refresh = false): void {
    if (this.reportPending) {
      return;
    }
    this.reportPending = true;
    this.reportFailed = false;
    this.http
      .get<SpyReport>(`${SPY_API}/report?wid=${this.wid}${refresh ? "&refresh=1" : ""}`)
      .pipe(untilDestroyed(this))
      .subscribe({
        next: r => {
          this.report = r;
          this.reportPending = false;
          this.reportFailed = !r.written;
        },
        error: () => {
          this.reportPending = false;
          this.reportFailed = true;
        },
      });
  }

  public copyReport(): void {
    const text = this.report?.markdown;
    if (!text) {
      return;
    }
    navigator.clipboard?.writeText(text).then(
      () => {
        this.copied = true;
        setTimeout(() => (this.copied = false), 2000);
      },
      () => (this.copied = false)
    );
  }

  /** The report as a file, for whoever wants it outside this panel. */
  public downloadReport(): void {
    const text = this.report?.markdown;
    if (!text) {
      return;
    }
    const blob = new Blob([text], { type: "text/markdown;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `workflow-${this.wid}-report.md`;
    link.click();
    URL.revokeObjectURL(url);
  }

  // ---------- shared ----------

  /** For the signed row difference drawn inside a step. */
  public abs(n: number): number {
    return Math.abs(n);
  }

  /** Thousands separators, for counts shown inside a sentence. */
  public count(n: number): string {
    return n.toLocaleString("en-US");
  }

  /** Properties can carry a whole program; the screen gets a slice. */
  public clip(value: unknown, max = 120): string {
    // An empty string would print as nothing and the change would look
    // half-written, so it is shown quoted.
    if (value === "") {
      return '""';
    }
    const raw = typeof value === "string" ? value : JSON.stringify(value);
    const flat = (raw ?? "").replace(/\n/g, " ⏎ ");
    return flat.length <= max ? flat : flat.slice(0, max) + "…";
  }

  public runLabel(run: SpyRun): string {
    return `${run.name ?? "unnamed run"} · ${this.when(run.started)} · ${this.count(run.rows)} rows`;
  }

  public stamp(value: string): string {
    return value ? value.substring(0, 19) : "";
  }

  public timeOnly(value: string): string {
    return value ? value.substring(11, 19) : "";
  }

  // ---------- the window's own furniture ----------

  /**
   * The sidebar. Every destination on it is a view that exists: there are no
   * placeholders here, so nothing in the rail leads to an empty room.
   */
  public readonly rail: { group: string; items: { id: SpyTab; label: string; icon: string[] }[] }[] = [
    {
      group: "Observe",
      items: [
        // Three boxes chained: the flow as the first view draws it.
        {
          id: "brief",
          label: "Workflow",
          icon: ["M2.5 7.5h4v5h-4zM13.5 4h4v5h-4zM13.5 11h4v5h-4z", "M6.5 10h3.5V6.5h3.5M10 10v3.5h3.5"],
        },
        // A timeline with its marks.
        { id: "footage", label: "Timeline", icon: ["M2.5 10h15", "M5.5 7v6M10 6v8M14.5 7.5v5"] },
      ],
    },
    {
      group: "Compare",
      items: [
        // A line that rises and falls: how the runs came out, one after another.
        { id: "experiments", label: "Runs", icon: ["M2.5 13.5l4-5 3.5 3 3-5.5 4.5 3"] },
        // Bars of different lengths, which is exactly what the comparison draws.
        { id: "autopsy", label: "Results", icon: ["M3 5.5h10M3 10h6M3 14.5h13"] },
      ],
    },
    {
      // A written page.
      group: "Explain",
      items: [{ id: "report", label: "Report", icon: ["M4.5 2.5h8l3.5 3.5v11h-11.5z", "M7 9h6M7 12.5h4"] }],
    },
  ];

  /**
   * Whether the rail is open. Folded it keeps only the icons, so the drawings
   * get the width back without anybody losing their way around.
   */
  public railOpen = true;

  public toggleRail(): void {
    this.railOpen = !this.railOpen;
  }

  /**
   * The question each view answers. The rail carries a short noun so it stays
   * quiet; the question itself is the page's title, which is where it reads.
   */
  public readonly titles: Record<SpyTab, string> = {
    brief: "How does this work?",
    footage: "What did I change?",
    experiments: "What has been tried?",
    autopsy: "Why did my results change?",
    report: "Report",
  };

  /** The most recent run, which is what the title bar reports on. */
  public get lastRun(): SpyRun | undefined {
    return this.runs.length ? this.runs[this.runs.length - 1] : undefined;
  }

  /** One word for where the workflow stands, taken from its last run. */
  public get state(): string {
    if (this.keeping) {
      return "Running";
    }
    const run = this.lastRun;
    if (!run) {
      return "Never run";
    }
    return run.status.charAt(0).toUpperCase() + run.status.slice(1);
  }

  /** Which of the four meanings that state carries. */
  public get stateKind(): string {
    if (this.keeping) {
      return "live";
    }
    const status = this.lastRun?.status ?? "";
    if (status === "running" || status === "paused") {
      return "live";
    }
    if (status === "failed" || status === "killed") {
      return "bad";
    }
    if (status === "completed") {
      return "good";
    }
    return "idle";
  }

  /** How long the last run took, wall clock, from the record. */
  public get lastRunTook(): string {
    const run = this.lastRun;
    if (!run?.started || !run?.ended) {
      return "—";
    }
    const from = new Date(run.started.replace(" ", "T")).getTime();
    const to = new Date(run.ended.replace(" ", "T")).getTime();
    if (isNaN(from) || isNaN(to) || to < from) {
      return "—";
    }
    return this.lasted((to - from) / 1000);
  }

  /** A duration in the shortest form that is still exact enough to trust. */
  public lasted(seconds: number): string {
    if (seconds < 10) {
      return `${seconds.toFixed(1)}s`;
    }
    if (seconds < 60) {
      return `${Math.round(seconds)}s`;
    }
    const minutes = Math.floor(seconds / 60);
    return `${minutes}m ${Math.round(seconds - minutes * 60)}s`;
  }

  // ---------- how the runs moved, run after run ----------

  public readonly trendW = 292;
  public readonly trendH = 68;

  /** Worked out once, when the runs arrive: a getter here would redraw forever. */
  public trends: Trend[] = [];

  private linesOverRuns(): Trend[] {
    const out: Trend[] = [];
    const time = this.trendOf("seconds", "Time to run");
    const rows = this.trendOf("produced", "Rows produced");
    if (time) {
      out.push(time);
    }
    if (rows) {
      out.push(rows);
    }
    return out;
  }

  /**
   * One metric across every run that measured it. Two rules keep this honest:
   * a run that produced nothing breaks the line instead of dipping it, and
   * only time gets called better or worse, because more rows is neither.
   */
  private trendOf(metric: "seconds" | "produced", label: string): Trend | null {
    const seen = (this.experiments?.runs ?? []).filter(run =>
      metric === "seconds" ? run.seconds != null && run.seconds > 0 : run.measured
    );
    if (seen.length < 2) {
      return null;
    }
    const valueOf = (run: ExperimentRun) => (metric === "seconds" ? run.seconds ?? 0 : run.produced);
    const values = seen.map(valueOf);
    const top = Math.max(...values);
    const floor = Math.min(...values);
    const span = top - floor;
    const pad = 11;
    const width = this.trendW - pad * 2;
    const height = this.trendH - pad * 2;
    const stepX = seen.length > 1 ? width / (seen.length - 1) : 0;

    const dots: TrendDot[] = seen.map((run, i) => {
      const value = valueOf(run);
      const empty = metric === "produced" && run.produced === 0;
      // With every run the same, a line at the floor would read as the worst
      // possible result. Flat means flat, so it sits in the middle.
      const lift = span === 0 ? 0.5 : (value - floor) / span;
      return {
        x: Math.round((pad + i * stepX) * 10) / 10,
        y: empty ? this.trendH - pad : Math.round((pad + height - lift * height) * 10) / 10,
        eid: run.eid,
        label: run.name || `run ${run.eid}`,
        value: metric === "seconds" ? this.lasted(value) : this.count(value),
        empty,
      };
    });

    const segments: string[] = [];
    let stretch: TrendDot[] = [];
    for (const dot of dots) {
      if (dot.empty) {
        if (stretch.length > 1) {
          segments.push(stretch.map(d => `${d.x},${d.y}`).join(" "));
        }
        stretch = [];
      } else {
        stretch.push(dot);
      }
    }
    if (stretch.length > 1) {
      segments.push(stretch.map(d => `${d.x},${d.y}`).join(" "));
    }

    const last = values[values.length - 1];
    const before = values[values.length - 2];
    const moved = last - before;
    const written = (n: number) =>
      metric === "seconds"
        ? `${n > 0 ? "+" : "−"}${this.lasted(Math.abs(n))}`
        : `${n > 0 ? "+" : "−"}${this.count(Math.abs(n))}`;
    return {
      key: metric,
      label,
      segments,
      dots,
      high: metric === "seconds" ? this.lasted(top) : this.count(top),
      low: metric === "seconds" ? this.lasted(floor) : this.count(floor),
      latest: metric === "seconds" ? this.lasted(last) : this.count(last),
      change: moved === 0 ? "no change" : written(moved),
      verdict: metric !== "seconds" ? "" : moved === 0 ? "same" : moved < 0 ? "better" : "worse",
    };
  }
}
