import {HttpClient} from '@angular/common/http';
import {Injectable} from '@angular/core';
import {Observable} from 'rxjs/Observable';
import {AppSettings} from '../../../app-setting';
import {Workflow} from '../../../type/workflow';


export const WORKFLOW_ACCESS_URL = 'workflowaccess';

@Injectable({
  providedIn: 'root'
})
export class WorkflowGrantAccessService {
  constructor(private http: HttpClient) {
  }

  /**
   * Assign a new access to/Modify an existing access of another user
   * @param workflow the workflow that is about to be shared
   * @param username the username of target user
   * @param accessType the type of access offered
   * @return hashmap indicating all current accesses, ex: {"Jim": "Write"}
   */
  public grantWorkflowAccess(workflow: Workflow, username: string, accessType: string): Observable<Map<string, string>> {
    return this.http.post<Map<string, string>>(`${AppSettings.getApiEndpoint()}/${WORKFLOW_ACCESS_URL}/share/${workflow.wid}/${username}/${accessType}`, null);
  }

  /**
   * Retrieve all shared accesses of the given workflow
   * @param workflow the current workflow
   * @return message of success
   */
  public getSharedAccess(workflow: Workflow): Observable<Map<string, string>> {
    return this.http.get<Map<string, string>>(`${AppSettings.getApiEndpoint()}/${WORKFLOW_ACCESS_URL}/currentShare/${workflow.wid}`);
  }


  /**
   * Remove an existing access of another user
   * @param workflow the current workflow
   * @param username the username of target user
   * @return message of success
   */
  public removeAccess(workflow: Workflow, username: string): Observable<Map<string, string>> {
    return this.http.post<Map<string, string>>(`${AppSettings.getApiEndpoint()}/${WORKFLOW_ACCESS_URL}/remove/${workflow.wid}/${username}`, null);
  }
}
