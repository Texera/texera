import { Component, Input, OnInit } from "@angular/core";
import { HubWorkflow } from "../../type/hub-workflow.interface";
import { WorkflowContent } from "../../../../common/type/workflow";

export interface HubWorkflowWithUserInfo extends HubWorkflow {
  name: string;
  description: string | undefined;
  wid: number | undefined;
  creationTime: number | undefined;
  lastModifiedTime: number | undefined;
  userName?: string;
  userGoogleAvatar?: string;
  color?: string;
}

@Component({
  selector: "texera-hub-result-card",
  templateUrl: "./hub-result-card.component.html",
  styleUrls: ["./hub-result-card.component.scss"],
})
export class HubResultCardComponent implements OnInit {
  @Input() workflow!: HubWorkflowWithUserInfo;
  truncatedTitle: string = "";

  ngOnInit() {
    if (!this.workflow.color) {
      this.workflow.color = "#66CCFF";
      this.truncatedTitle = this.getTruncatedTitle(this.workflow.name);
    }
  }

  getTruncatedTitle(title: string): string {
    const maxLength = 80;
    if (title.length > maxLength) {
      return title.substring(0, maxLength) + "...";
    }
    return title;
  }
}
