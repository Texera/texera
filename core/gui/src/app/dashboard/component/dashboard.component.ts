import { ChangeDetectorRef, Component, OnInit } from "@angular/core";
import { WorkflowPersistService } from "../../common/service/workflow-persist/workflow-persist.service";
import { UserService } from "../../common/service/user/user.service";
import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { FlarumService } from "../service/user/flarum/flarum.service";
import { HttpErrorResponse } from "@angular/common/http";
import { HubComponent } from "../../hub/component/hub.component";

@Component({
  selector: "texera-dashboard",
  templateUrl: "dashboard.component.html",
  styleUrls: ["dashboard.component.scss"],
  providers: [WorkflowPersistService],
})
@UntilDestroy()
export class DashboardComponent implements OnInit {
  isAdmin = this.userService.isAdmin();
  isLogin = this.userService.isLogin();
  displayForum = true;
  currentComponent = HubComponent;

  constructor(
    private userService: UserService,
    private flarumService: FlarumService,
    private cdr: ChangeDetectorRef
  ) {
    this.userService
      .userChanged()
      .pipe(untilDestroyed(this))
      .subscribe(() => {
        this.isLogin = this.userService.isLogin();
        this.isAdmin = this.userService.isAdmin();
        this.cdr.detectChanges();
      });
  }

  ngOnInit(): void {
    if (!document.cookie.includes("flarum_remember")) {
      this.flarumService
        .auth()
        .pipe(untilDestroyed(this))
        .subscribe({
          next: (response: any) => {
            document.cookie = `flarum_remember=${response.token};path=/`;
          },
          error: (err: unknown) => {
            if ((err as HttpErrorResponse).status == 404) {
              this.displayForum = false;
            } else {
              this.flarumService
                .register()
                .pipe(untilDestroyed(this))
                .subscribe(() => this.ngOnInit());
            }
          },
        });
    }
  }
}
