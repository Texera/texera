import { Component, Input } from "@angular/core";
import { HubWorkflow } from "../../type/hub-workflow.interface";
import { UserService } from "../../../../common/service/user/user.service";
import { User } from "../../../../common/type/user";
import { UntilDestroy } from "@ngneat/until-destroy";

@UntilDestroy()
@Component({
  selector: "texera-hub-workflow-browse-section",
  templateUrl: "./hub-workflow-browse-section.component.html",
  styleUrls: ["./hub-workflow-browse-section.component.scss"],
})
export class HubWorkflowBrowseSectionComponent {
  public user: User | undefined;
  public targetLink: string = "";

  constructor(private userService: UserService) {
    this.user = this.userService.getCurrentUser();
  }
  @Input() workflowList: HubWorkflow[] = [];
  @Input() sectionTitle: String = "";
  public defaultBackground: String = "../../../../../assets/card_background.jpg";
}
