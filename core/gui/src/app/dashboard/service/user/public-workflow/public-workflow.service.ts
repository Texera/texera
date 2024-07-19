import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { AppSettings } from "../../../../common/app-setting";

export const USER_BASE_URL = `${AppSettings.getApiEndpoint()}/workflow`;

@Injectable({
  providedIn: "root",
})
export class PublicWorkflowService {
  constructor(private http: HttpClient) {}

  public getWorkflowType(wid: number): Observable<string> {
    return this.http.get(`${USER_BASE_URL}/type/${wid}`, { responseType: "text" });
  }

  public makePublic(wid: number): Observable<void> {
    return this.http.put<void>(`${USER_BASE_URL}/public/${wid}`, null);
  }

  public makePrivate(wid: number): Observable<void> {
    return this.http.put<void>(`${USER_BASE_URL}/private/${wid}`, null);
  }
}
