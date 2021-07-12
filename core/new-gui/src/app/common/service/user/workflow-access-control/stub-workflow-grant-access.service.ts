import {Injectable} from '@angular/core';
import {Observable} from "rxjs/Observable";
import {of} from "rxjs"
import {WorkflowGrantAccessService} from "./workflow-grant-access.service";
import {Workflow, WorkflowContent} from "../../../type/workflow";
import {jsonCast} from "../../../util/storage";

export const MOCK_WORKFLOW: Workflow = {
  wid: 1,
  name: 'project 1',
  content: jsonCast<WorkflowContent>(" {\"operators\":[],\"operatorPositions\":{},\"links\":[],\"groups\":[],\"breakpoints\":{}}"),
  creationTime: 1,
  lastModifiedTime: 2,
}

type PublicInterfaceOf<Class> = {
  [Member in keyof Class]: Class[Member];
};

@Injectable()
export class StubWorkflowGrantAccessService implements PublicInterfaceOf<WorkflowGrantAccessService> {


  public workflow: Workflow

  public message: string = "This is testing"

  public mapString: Map<string, string> = new Map<string, string>()

  constructor() {
    this.workflow = MOCK_WORKFLOW
  }

  public getSharedAccess(workflow: Workflow): Observable<Map<string, string>> {
    return of(this.mapString)
  }

  public grantWorkflowAccess(workflow: Workflow, username: string, accessType: string): Observable<Map<string, string>> {
    return of(this.mapString)
  }


  public removeAccess(workflow: Workflow, username: string): Observable<Map<string, string>> {
    return of(this.mapString)
  }


}
