import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import {ReactiveFormsModule} from "@angular/forms";
import {HttpClient, HttpHandler} from "@angular/common/http";
import {WorkflowGrantAccessService} from "../../../../../common/service/user/workflow-access-control/workflow-grant-access.service";
import { NgbActiveModal} from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import { NgbdModalShareAccessComponent} from "./ngbd-modal-share-access.component";
import {StubWorkflowGrantAccessService} from "../../../../../common/service/user/workflow-access-control/stub-workflow-grant-access.service";
import {Workflow, WorkflowContent} from "../../../../../common/type/workflow";
import {jsonCast} from "../../../../../common/util/storage";

describe ("NgbdModalShareAccessComponent", () => {
  let component : NgbdModalShareAccessComponent
  let fixture: ComponentFixture<NgbdModalShareAccessComponent>
  let service: WorkflowGrantAccessService

  const workflow: Workflow = {
    wid: 28,
    name: 'project 1',
    content: jsonCast<WorkflowContent>(" {\"operators\":[],\"operatorPositions\":{},\"links\":[],\"groups\":[],\"breakpoints\":{}}"),
    creationTime: 1,
    lastModifiedTime: 2
  }

  beforeEach(async(async () => {
    TestBed.configureTestingModule({
      imports: [ReactiveFormsModule, FormsModule],
      declarations: [NgbdModalShareAccessComponent],
      providers: [NgbActiveModal, HttpClient, HttpHandler,  { provide: WorkflowGrantAccessService, useClass: StubWorkflowGrantAccessService }]
  });
  }))

  beforeEach(() =>{
    fixture = TestBed.createComponent(NgbdModalShareAccessComponent)
    component = fixture.componentInstance
    service = TestBed.get(WorkflowGrantAccessService)
    fixture.detectChanges()
  })

  it("should create", () => {
    expect(component).toBeTruthy()
  })

  it('form invalid when empty', () => {
    expect(component.shareForm.valid).toBeFalsy();
  });

  it("can get all accesses", () => {
    let mySpy = spyOn(service, "getSharedAccess").and.callThrough()
    console.log(service)
    component.workflow = workflow
    fixture.detectChanges()
    component.onClickGetAllSharedAccess(component.workflow)
    expect(mySpy).toHaveBeenCalled()
    expect(component.sharedUsers.length == 0).toBeTruthy()
  })

  it("can share accesses", () => {
    let mySpy = spyOn(service, "grantWorkflowAccess").and.callThrough()
    console.log(service)
    component.workflow = workflow
    fixture.detectChanges()
    component.onClickShareWorkflow(component.workflow, "Jim", "read")
    expect(mySpy).toHaveBeenCalled()
  })

  it("can remove accesses", () => {
    let mySpy = spyOn(service, "removeAccess").and.callThrough()
    console.log(service)
    console.log(service.getSharedAccess)
    component.onClickRemoveAccess(workflow, "Jim")
    expect(mySpy).toHaveBeenCalled()
  })

  it('submitting a form', () => {
    let mySpy = spyOn(component, "onClickShareWorkflow")
    expect(component.shareForm.valid).toBeFalsy();
    component.shareForm.controls['username'].setValue("testguy");
    component.shareForm.controls['accessType'].setValue("read");
    expect(component.shareForm.valid).toBeTruthy();
    component.onSubmit(workflow);
    expect(mySpy).toHaveBeenCalled()
  });

})
