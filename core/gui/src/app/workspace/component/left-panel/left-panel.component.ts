import { Component, HostListener, OnDestroy, OnInit, Type } from "@angular/core";
import { UntilDestroy } from "@ngneat/until-destroy";
import { NzResizeEvent } from "ng-zorro-antd/resizable";
import { CdkDragDrop, moveItemInArray } from "@angular/cdk/drag-drop";
import { environment } from "../../../../environments/environment";
import { OperatorMenuComponent } from "./operator-menu/operator-menu.component";
import { VersionsListComponent } from "./versions-list/versions-list.component";
import { WorkflowExecutionHistoryComponent } from "../../../dashboard/component/user/user-workflow/ngbd-modal-workflow-executions/workflow-execution-history.component";
import { TimeTravelComponent } from "./time-travel/time-travel.component";
import { SettingsComponent } from "./settings/settings.component";
import { calculateTotalTranslate3d } from "../../../common/util/panel-dock";
@UntilDestroy()
@Component({
  selector: "texera-left-panel",
  templateUrl: "left-panel.component.html",
  styleUrls: ["left-panel.component.scss"],
})
export class LeftPanelComponent implements OnDestroy, OnInit {
  protected readonly window = window;
  currentComponent: Type<any> | null = null;
  title = "Operators";
  width = 300;
  height = Math.max(300, window.innerHeight * 0.6);
  id = -1;
  currentIndex = 0;
  items = [
    { component: null, title: "", icon: "", enabled: true },
    { component: OperatorMenuComponent, title: "Operators", icon: "appstore", enabled: true },
    { component: VersionsListComponent, title: "Versions", icon: "schedule", enabled: environment.userSystemEnabled },
    {
      component: SettingsComponent,
      title: "Settings",
      icon: "setting",
      enabled: true,
    },
    {
      component: WorkflowExecutionHistoryComponent,
      title: "Execution History",
      icon: "history",
      enabled: environment.workflowExecutionsTrackingEnabled,
    },
    {
      component: TimeTravelComponent,
      title: "Time Travel",
      icon: "clock-circle",
      enabled: environment.userSystemEnabled && environment.timetravelEnabled,
    },
  ];

  order = Array.from({ length: this.items.length - 1 }, (_, index) => index + 1);
  dragPosition = { x: 0, y: 0 };
  returnPosition = { x: 0, y: 0 };
  isDocked = true;

  constructor() {
    const savedOrder = localStorage.getItem("left-panel-order")?.split(",").map(Number);
    this.order = savedOrder && new Set(savedOrder).size === new Set(this.order).size ? savedOrder : this.order;

    const savedIndex = Number(localStorage.getItem("left-panel-index"));
    this.openFrame(savedIndex < this.items.length && this.items[savedIndex].enabled ? savedIndex : 1);

    this.width = Number(localStorage.getItem("left-panel-width")) || this.width;
    this.height = Number(localStorage.getItem("left-panel-height")) || this.height;
  }

  ngOnInit(): void {
    const style = localStorage.getItem("left-panel-style");
    if (style) document.getElementById("left-container")!.style.cssText = style;
    const translates = document.getElementById("left-container")!.style.transform;
    const [xOffset, yOffset, _] = calculateTotalTranslate3d(translates);
    this.returnPosition = { x: -xOffset, y: -yOffset };
    this.isDocked = this.dragPosition.x === this.returnPosition.x && this.dragPosition.y === this.returnPosition.y;
  }

  @HostListener("window:beforeunload")
  ngOnDestroy(): void {
    localStorage.setItem("left-panel-width", String(this.width));
    localStorage.setItem("left-panel-height", String(this.height));
    localStorage.setItem("left-panel-order", String(this.order));
    localStorage.setItem("left-panel-index", String(this.currentIndex));
    localStorage.setItem("left-panel-style", document.getElementById("left-container")!.style.cssText);
  }

  openFrame(i: number) {
    if (!i) {
      this.width = 0;
      this.height = 65;
    } else if (!this.width) {
      this.width = 230;
      this.height = 300;
    }
    this.title = this.items[i].title;
    this.currentComponent = this.items[i].component;
    this.currentIndex = i;
  }
  onDrop(event: CdkDragDrop<string[]>) {
    moveItemInArray(this.order, event.previousIndex, event.currentIndex);
  }
  onResize({ width, height }: NzResizeEvent) {
    cancelAnimationFrame(this.id);
    this.id = requestAnimationFrame(() => {
      this.width = width!;
      this.height = height!;
    });
  }

  resetPanelPosition() {
    this.dragPosition = { x: this.returnPosition.x, y: this.returnPosition.y };
    this.isDocked = true;
  }

  handleDragStart() {
    this.isDocked = false;
  }
}
