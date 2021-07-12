import {Component, Input, OnInit} from '@angular/core';
import {NgbActiveModal} from '@ng-bootstrap/ng-bootstrap';
import {FormBuilder, Validators} from "@angular/forms";
import {WorkflowGrantAccessService} from "../../../../../common/service/user/workflow-access-control/workflow-grant-access.service";
import {Workflow} from "../../../../../common/type/workflow";


@Component({
  selector: 'texera-ngbd-modal-share-access',
  templateUrl: './ngbd-modal-share-access.component.html',
  styleUrls: ['./ngbd-modal-share-access.component.scss']
})
export class NgbdModalShareAccessComponent implements OnInit {

  @Input() workflow!: Workflow

  shareForm = this.formBuilder.group({
    username: '',
    accessType: ['', [Validators.required]]
  });


  accessTypes: any = ["read", "write"]

  currentShare: Map<string, string> = new Map<string, string>()

  sharedUsers: string[] = []

  public defaultWeb: String = 'http://localhost:4200/';

  constructor(
    public activeModal: NgbActiveModal,
    private workflowGrantAccessService: WorkflowGrantAccessService,
    private formBuilder: FormBuilder
  ) {
  }

  ngOnInit(): void {
    console.log(this.workflow)
    this.onClickGetAllSharedAccess(this.workflow)
  }


  /**
   * get all shared access of current workflow
   * @param workflow target/current workflow
   */
  public onClickGetAllSharedAccess(workflow: Workflow): void {
    this.workflowGrantAccessService.getSharedAccess(workflow).subscribe(res => {
      this.currentShare = new Map(Object.entries(res));
      this.sharedUsers = []
      for (let username of this.currentShare.keys()) {
        this.sharedUsers.push(username);
      }
    });
  }
  /**
   * offer a specific type of access to a user
   * @param workflow the given/target workflow
   * @param userToShareWith the target user
   * @param accessType the type of access to be given
   */
  public onClickShareWorkflow(workflow: Workflow, userToShareWith: string, accessType: string): void {
    this.workflowGrantAccessService.grantWorkflowAccess(workflow, userToShareWith, accessType).subscribe(resp => this.onClickGetAllSharedAccess(workflow));
  }

  /**
   * remove any type of access of the target used
   * @param workflow the given/target workflow
   * @param userToRemove the target user
   */
  public onClickRemoveAccess(workflow: Workflow, userToRemove: string): void {
    this.workflowGrantAccessService.removeAccess(workflow, userToRemove).subscribe(res => {
      console.log(res)
      this.onClickGetAllSharedAccess(workflow);
    });
  }

  /**
   * change form information based on user behavior on UI
   * @param e selected value
   */
  changeType(e: any) {
    console.log(e.value)
    this.shareForm.setValue(["accessType"], e.target.value)
  }

  /**
   * triggered by clicking the SUBMIT button, offers access based on the input information
   * @param workflow target/current workflow
   */
  onSubmit(workflow: Workflow): any {
    this.onClickShareWorkflow(workflow, this.shareForm.get("username")?.value, this.shareForm.get("accessType")?.value)
  }


}
