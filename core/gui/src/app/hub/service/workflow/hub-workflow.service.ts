import { HttpClient, HttpParams } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { AppSettings } from "../../../common/app-setting";
import { HubWorkflow } from "../../component/type/hub-workflow.interface";
import { User } from "src/app/common/type/user";

export const WORKFLOW_BASE_URL = `${AppSettings.getApiEndpoint()}/workflow`;

type PartialUserInfo = Pick<User, "name" | "color" | "googleAvatar">;

@Injectable({
  providedIn: "root",
})
export class HubWorkflowService {
  readonly BASE_URL: string = `${AppSettings.getApiEndpoint()}/hub/workflow`;

  constructor(private http: HttpClient) {}

  public getWorkflowCount(): Observable<number> {
    return this.http.get<number>(`${this.BASE_URL}/count`);
  }

  public getWorkflowList(): Observable<HubWorkflow[]> {
    return this.http.get<HubWorkflow[]>(`${this.BASE_URL}/list`);
  }

  public cloneWorkflow(wid: number): Observable<number> {
    return this.http.post<number>(`${WORKFLOW_BASE_URL}/clone/${wid}`, null);
  }

  public getOwnerUser(wid: number): Observable<User> {
    const params = new HttpParams().set("wid", wid);
    return this.http.get<User>(`${this.BASE_URL}/owner_user/`, { params });
  }

  public getUserInfo(wids: number[]): Observable<{ [key: number]: PartialUserInfo }> {
    let params = new HttpParams();
    wids.forEach(wid => {
      params = params.append("wids", wid.toString());
    });
    return this.http.get<{ [key: number]: PartialUserInfo }>(`${this.BASE_URL}/user_info`, { params });
  }

  public checkUserClonedWorkflow(wid: number, uid: number): Observable<boolean> {
    const params = new HttpParams().set("wid", wid.toString()).set("uid", uid.toString());
    return this.http.get<boolean>(`${WORKFLOW_BASE_URL}/is_cloned`, { params });
  }

  public getPopularWorkflows(): Observable<HubWorkflow[]> {
    return this.http.get<HubWorkflow[]>(`${this.BASE_URL}/popular_workflows`);
  }

  public getRecentWorkflows(): Observable<HubWorkflow[]> {
    return this.http.get<HubWorkflow[]>(`${this.BASE_URL}/recent_workflows`);
  }
}
