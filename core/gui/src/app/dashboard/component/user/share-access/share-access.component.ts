import { Component, inject, Input, OnInit } from "@angular/core";
import { FormBuilder, FormGroup, Validators, FormControl } from "@angular/forms";
import { ShareAccessService } from "../../../service/user/share-access/share-access.service";
import { ShareAccess } from "../../../type/share-access.interface";
import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { UserService } from "../../../../common/service/user/user.service";
import { GmailService } from "../../../../common/service/gmail/gmail.service";
import { NZ_MODAL_DATA, NzModalRef, NzModalService } from "ng-zorro-antd/modal";
import { NotificationService } from "../../../../common/service/notification/notification.service";
import { HttpErrorResponse } from "@angular/common/http";
import { NzMessageService } from "ng-zorro-antd/message";
import { DatasetService } from "../../../service/user/dataset/dataset.service";
import { WorkflowPersistService } from "src/app/common/service/workflow-persist/workflow-persist.service";
import { WorkflowActionService } from "src/app/workspace/service/workflow-graph/model/workflow-action.service";

@UntilDestroy()
@Component({
  selector: "texera-share-access",
  templateUrl: "share-access.component.html",
  styleUrls: ["./share-access.component.scss"],
})
export class ShareAccessComponent implements OnInit {
  readonly nzModalData = inject(NZ_MODAL_DATA);
  readonly writeAccess: boolean = this.nzModalData.writeAccess;
  readonly type: string = this.nzModalData.type;
  readonly id: number = this.nzModalData.id;
  readonly allOwners: string[] = this.nzModalData.allOwners;

  readonly inWorkspace: boolean = this.nzModalData.inWorkspace;
  public validateForm: FormGroup;
  public accessList: ReadonlyArray<ShareAccess> = [];
  public owner: string = "";
  public filteredOwners: Array<string> = [];
  public ownerSearchValue?: string;
  public emailTags: string[] = [];
  currentEmail: string | undefined = "";
  isPublic: boolean | null = null;

  constructor(
    private accessService: ShareAccessService,
    private formBuilder: FormBuilder,
    private userService: UserService,
    private gmailService: GmailService,
    private notificationService: NotificationService,
    private message: NzMessageService,
    private modalService: NzModalService,
    private workflowPersistService: WorkflowPersistService,
    private datasetService: DatasetService,
    private workflowActionService: WorkflowActionService
  ) {
    this.validateForm = this.formBuilder.group({
      email: [null, Validators.email],
      accessLevel: ["READ"],
    });
    this.currentEmail = this.userService.getCurrentUser()?.email;
  }

  ngOnInit(): void {
    this.accessService
      .getAccessList(this.type, this.id)
      .pipe(untilDestroyed(this))
      .subscribe(access => (this.accessList = access));
    this.accessService
      .getOwner(this.type, this.id)
      .pipe(untilDestroyed(this))
      .subscribe(name => {
        this.owner = name;
      });
    if (this.type === "workflow") {
      this.workflowPersistService
        .getWorkflowIsPublished(this.id)
        .pipe(untilDestroyed(this))
        .subscribe(dashboardWorkflow => {
          this.isPublic = dashboardWorkflow === "Public";
        });
    } else if (this.type === "dataset") {
      this.datasetService
        .getDataset(this.id)
        .pipe(untilDestroyed(this))
        .subscribe(dashboardDataset => {
          this.isPublic = dashboardDataset.dataset.isPublic === 1;
        });
    }
  }

  public handleInputConfirm(event?: Event): void {
    if (event) {
      event.preventDefault();
    }
    const emailInput = this.validateForm.get("email")?.value;

    if (emailInput) {
      const emailArray: string[] = emailInput.split(/[\s,;]+/);
      emailArray.forEach(email => {
        if (email) {
          const emailControl = new FormControl(email, Validators.email);
          if (!emailControl.errors && !this.emailTags.includes(email)) {
            this.emailTags.push(email);
          } else if (this.emailTags.includes(email)) {
            this.message.error(`${email} is already in the tags`);
          } else {
            this.message.error(`${email} is not a valid email`);
          }
        }
      });
    }

    this.validateForm.get("email")?.reset();
  }

  public removeEmailTag(email: string): void {
    this.emailTags = this.emailTags.filter(tag => tag !== email);
  }

  public grantAccess(): void {
    this.handleInputConfirm();
    if (this.emailTags.length > 0) {
      this.emailTags.forEach(email => {
        this.accessService
          .grantAccess(this.type, this.id, email, this.validateForm.value.accessLevel)
          .pipe(untilDestroyed(this))
          .subscribe({
            next: () => {
              this.notificationService.success(this.type + " shared with " + email + " successfully.");
              this.gmailService.sendEmail(
                "Texera: " + this.owner + " shared a " + this.type + " with you",
                this.owner +
                  " shared a " +
                  this.type +
                  " with you, access the workflow at " +
                  location.origin +
                  "/workflow/" +
                  this.id,
                email
              );
            },
            error: (error: unknown) => {
              if (error instanceof HttpErrorResponse) {
                this.notificationService.error(error.error.message);
              }
            },
          });
      });
      this.emailTags = [];
      this.ngOnInit();
    }
  }

  public onPaste(event: ClipboardEvent): void {
    event.preventDefault();
    const pasteData = event.clipboardData?.getData("text");
    if (pasteData) {
      const currentEmailValue = this.validateForm.get("email")?.value || "";
      // concaste new emails and old emails
      const newValue = currentEmailValue + pasteData;
      this.validateForm.get("email")?.setValue(newValue);
      this.handleInputConfirm();
    }
  }

  public onChange(value: string): void {
    if (value === null || value === undefined) {
      this.filteredOwners = [];
    } else {
      this.filteredOwners = this.allOwners.filter(owner => owner.toLowerCase().indexOf(value.toLowerCase()) !== -1);
    }
  }

  public revokeAccess(userToRemove: string): void {
    this.accessService
      .revokeAccess(this.type, this.id, userToRemove)
      .pipe(untilDestroyed(this))
      .subscribe(() => this.ngOnInit());
  }
  verifyPublish(): void {
    if (!this.isPublic) {
      const modal: NzModalRef = this.modalService.create({
        nzTitle: "Notice",
        nzContent: `Publishing your ${this.type} would grant all Texera users read access to your  ${this.type} along with the right to clone your work.`,
        nzFooter: [
          {
            label: "Cancel",
            onClick: () => modal.close(),
          },
          {
            label: "Publish",
            type: "primary",
            onClick: () => {
              if (this.type === "workflow") {
                this.publishWorkflow();

                if (this.inWorkspace) {
                  this.workflowActionService.setWorkflowIsPublished(1);
                }
              } else if (this.type === "dataset") {
                this.publishDataset();
              }
              modal.close();
            },
          },
        ],
      });
    }
  }

  verifyUnpublish(): void {
    if (this.isPublic) {
      const modal: NzModalRef = this.modalService.create({
        nzTitle: "Notice",
        nzContent: `All other users would lose access to your ${this.type} if you unpublish it.`,
        nzFooter: [
          {
            label: "Cancel",
            onClick: () => modal.close(),
          },
          {
            label: "Unpublish",
            type: "primary",
            onClick: () => {
              if (this.type === "workflow") {
                this.unpublishWorkflow();
                if (this.inWorkspace) {
                  this.workflowActionService.setWorkflowIsPublished(0);
                }
              } else if (this.type === "dataset") {
                this.unpublishDataset();
              }
              modal.close();
            },
          },
        ],
      });
    }
  }

  public publishWorkflow(): void {
    if (!this.isPublic) {
      console.log("Workflow " + this.id + " is published");
      this.workflowPersistService
        .updateWorkflowIsPublished(this.id, true)
        .pipe(untilDestroyed(this))
        .subscribe(() => (this.isPublic = true));
    } else {
      console.log("Workflow " + this.id + " is already published");
    }
  }

  public unpublishWorkflow(): void {
    if (this.isPublic) {
      console.log("Workflow " + this.id + " is unpublished");
      this.workflowPersistService
        .updateWorkflowIsPublished(this.id, false)
        .pipe(untilDestroyed(this))
        .subscribe(() => (this.isPublic = false));
    } else {
      console.log("Workflow " + this.id + " is already private");
    }
  }

  public publishDataset(): void {
    if (!this.isPublic) {
      this.datasetService
        .updateDatasetPublicity(this.id)
        .pipe(untilDestroyed(this))
        .subscribe({
          next: (res: Response) => {
            this.isPublic = true;
          },
          error: (err: unknown) => {
            this.notificationService.error("Failed to publish the dataset");
          },
        });
    } else {
      console.log("Dataset " + this.id + " is already private");
    }
  }

  public unpublishDataset(): void {
    if (this.isPublic) {
      this.datasetService
        .updateDatasetPublicity(this.id)
        .pipe(untilDestroyed(this))
        .subscribe({
          next: (res: Response) => {
            this.isPublic = false;
          },
          error: (err: unknown) => {
            this.notificationService.error("Failed to unpublish the dataset");
          },
        });
    } else {
      console.log("Dataset " + this.id + " is already private");
    }
  }
}
