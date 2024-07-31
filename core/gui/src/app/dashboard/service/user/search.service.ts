import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { SearchResult } from "../../type/search-result";
import { AppSettings } from "../../../common/app-setting";
import { SearchFilterParameters, toQueryStrings } from "../../type/search-filter-parameters";
import { SortMethod } from "../../type/sort-method";

const DASHBOARD_SEARCH_URL = "dashboard/search";
const DASHBOARD_PUBLIC_SEARCH_URL = "dashboard/public-search";

@Injectable({
  providedIn: "root",
})
export class SearchService {
  constructor(private http: HttpClient) {}

  public conditional_search(
    keywords: string[],
    params: SearchFilterParameters,
    start: number,
    count: number,
    type: "workflow" | "project" | "file" | "dataset" | null,
    orderBy: SortMethod,
    location: "private" | "public" = "private"
  ): Observable<SearchResult> {
    if (location === "private") {
      return this.search(keywords, params, start, count, type, orderBy);
    } else {
      return this.public_search(keywords, params, start, count, type, orderBy);
    }
  }

  /**
   * retrieves a workflow from backend database given its id. The user in the session must have access to the workflow.
   * @param wid, the workflow id.
   */
  public search(
    keywords: string[],
    params: SearchFilterParameters,
    start: number,
    count: number,
    type: "workflow" | "project" | "file" | "dataset" | null,
    orderBy: SortMethod
  ): Observable<SearchResult> {
    return this.http.get<SearchResult>(
      `${AppSettings.getApiEndpoint()}/${DASHBOARD_SEARCH_URL}?${toQueryStrings(
        keywords,
        params,
        start,
        count,
        type,
        orderBy
      )}`
    );
  }

  public public_search(
    keywords: string[],
    params: SearchFilterParameters,
    start: number,
    count: number,
    type: "workflow" | "project" | "file" | "dataset" | null,
    orderBy: SortMethod
  ): Observable<SearchResult> {
    return this.http.get<SearchResult>(
      `${AppSettings.getApiEndpoint()}/${DASHBOARD_PUBLIC_SEARCH_URL}?${toQueryStrings(
        keywords,
        params,
        start,
        count,
        type,
        orderBy
      )}`
    );
  }
}
