import { Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { map } from "rxjs/operators";
import { NotificationService } from "../../../../common/service/notification/notification.service";
import { Dataset, DatasetVersion } from "../../../../common/type/dataset";
import { AppSettings } from "../../../../common/app-setting";
import { Observable } from "rxjs";
import { DashboardDataset } from "../../../type/dashboard-dataset.interface";
import { FileUploadItem } from "../../../type/dashboard-file.interface";
import { DatasetFileNode } from "../../../../common/type/datasetVersionFileTree";

export const DATASET_BASE_URL = "dataset";
export const DATASET_CREATE_URL = DATASET_BASE_URL + "/create";
export const DATASET_UPDATE_BASE_URL = DATASET_BASE_URL + "/update";
export const DATASET_UPDATE_NAME_URL = DATASET_UPDATE_BASE_URL + "/name";
export const DATASET_UPDATE_DESCRIPTION_URL = DATASET_UPDATE_BASE_URL + "/description";
export const DATASET_UPDATE_PUBLICITY_URL = "update/publicity";
export const DATASET_LIST_URL = DATASET_BASE_URL + "/list";
export const DATASET_SEARCH_URL = DATASET_BASE_URL + "/search";
export const DATASET_DELETE_URL = DATASET_BASE_URL + "/delete";

export const DATASET_VERSION_BASE_URL = "version";
export const DATASET_VERSION_RETRIEVE_LIST_URL = DATASET_VERSION_BASE_URL + "/list";
export const DATASET_VERSION_LATEST_URL = DATASET_VERSION_BASE_URL + "/latest";

@Injectable({
  providedIn: "root",
})
export class DatasetService {
  constructor(
    private http: HttpClient,
    private notificationService: NotificationService
  ) {}

  public createDataset(
    dataset: Dataset,
    initialVersionName: string,
    filesToBeUploaded: FileUploadItem[]
  ): Observable<DashboardDataset> {
    const formData = new FormData();
    formData.append("datasetName", dataset.name);
    formData.append("datasetDescription", dataset.description);
    formData.append("isDatasetPublic", dataset.isPublic.toString());
    formData.append("initialVersionName", initialVersionName);

    filesToBeUploaded.forEach(file => {
      formData.append(`file:upload:${file.name}`, file.file);
    });

    return this.http.post<DashboardDataset>(`${AppSettings.getApiEndpoint()}/${DATASET_CREATE_URL}`, formData);
  }

  public getDataset(did: number): Observable<DashboardDataset> {
    return this.http.get<DashboardDataset>(`${AppSettings.getApiEndpoint()}/${DATASET_BASE_URL}/${did}`);
  }

  public retrieveDatasetVersionSingleFile(path: string): Observable<Blob> {
    const encodedPath = encodeURIComponent(path);
    return this.http.get(`${AppSettings.getApiEndpoint()}/${DATASET_BASE_URL}/file?path=${encodedPath}`, {
      responseType: "blob",
    });
  }

  public retrieveDatasetVersionZip(path: string): Observable<Blob> {
    const encodedPath = encodeURIComponent(path);
    return this.http.get(`${AppSettings.getApiEndpoint()}/${DATASET_BASE_URL}/version-zip?path=${encodedPath}`, {
      responseType: "blob",
    });
  }

  public retrieveAccessibleDatasets(
    includeVersions: boolean = false,
    includeFileNodes: boolean = false,
    filePath: string = ""
  ): Observable<{ datasets: DashboardDataset[]; fileNodes: DatasetFileNode[] }> {
    let params = new HttpParams();
    if (includeVersions) {
      params = params.set("includeVersions", "true");
    }
    if (includeFileNodes) {
      params = params.set("includeFileNodes", "true");
    }
    if (filePath && filePath != "") {
      params = params.set("path", encodeURIComponent(filePath));
    }
    return this.http.get<{ datasets: DashboardDataset[]; fileNodes: DatasetFileNode[] }>(
      `${AppSettings.getApiEndpoint()}/${DATASET_BASE_URL}`,
      { params: params }
    );
  }
  public createDatasetVersion(
    did: number,
    newVersion: string,
    removedFilePaths: string[],
    filesToBeUploaded: FileUploadItem[]
  ): Observable<DatasetVersion> {
    const formData = new FormData();
    formData.append("versionName", newVersion);

    if (removedFilePaths.length > 0) {
      const removedFilesString = JSON.stringify(removedFilePaths);
      formData.append("file:remove", removedFilesString);
    }

    filesToBeUploaded.forEach(file => {
      formData.append(`file:upload:${file.name}`, file.file);
    });

    return this.http
      .post<{
        datasetVersion: DatasetVersion;
        fileNodes: DatasetFileNode[];
      }>(`${AppSettings.getApiEndpoint()}/${DATASET_BASE_URL}/${did}/version/create`, formData)
      .pipe(
        map(response => {
          response.datasetVersion.fileNodes = response.fileNodes;
          return response.datasetVersion;
        })
      );
  }

  /**
   * retrieve a list of versions of a dataset. The list is sorted so that the latest versions are at front.
   * @param did
   */
  public retrieveDatasetVersionList(did: number): Observable<DatasetVersion[]> {
    return this.http
      .get<{
        versions: DatasetVersion[];
      }>(`${AppSettings.getApiEndpoint()}/${DATASET_BASE_URL}/${did}/${DATASET_VERSION_RETRIEVE_LIST_URL}`)
      .pipe(map(response => response.versions));
  }

  /**
   * retrieve the latest version of a dataset.
   * @param did
   */
  public retrieveDatasetLatestVersion(did: number): Observable<DatasetVersion> {
    return this.http
      .get<{
        datasetVersion: DatasetVersion;
        fileNodes: DatasetFileNode[];
      }>(`${AppSettings.getApiEndpoint()}/${DATASET_BASE_URL}/${did}/${DATASET_VERSION_LATEST_URL}`)
      .pipe(
        map(response => {
          response.datasetVersion.fileNodes = response.fileNodes;
          return response.datasetVersion;
        })
      );
  }

  /**
   * retrieve a list of nodes that represent the files in the version
   * @param did
   * @param dvid
   */
  public retrieveDatasetVersionFileTree(did: number, dvid: number): Observable<DatasetFileNode[]> {
    return this.http
      .get<{
        fileNodes: DatasetFileNode[];
      }>(`${AppSettings.getApiEndpoint()}/${DATASET_BASE_URL}/${did}/${DATASET_VERSION_BASE_URL}/${dvid}/rootFileNodes`)
      .pipe(map(response => response.fileNodes));
  }

  public deleteDatasets(dids: number[]): Observable<Response> {
    return this.http.post<Response>(`${AppSettings.getApiEndpoint()}/${DATASET_DELETE_URL}`, {
      dids: dids,
    });
  }

  public updateDatasetName(did: number, name: string): Observable<Response> {
    return this.http.post<Response>(`${AppSettings.getApiEndpoint()}/${DATASET_UPDATE_NAME_URL}`, {
      did: did,
      name: name,
    });
  }

  public updateDatasetDescription(did: number, description: string): Observable<Response> {
    return this.http.post<Response>(`${AppSettings.getApiEndpoint()}/${DATASET_UPDATE_DESCRIPTION_URL}`, {
      did: did,
      description: description,
    });
  }

  public updateDatasetPublicity(did: number): Observable<Response> {
    return this.http.post<Response>(
      `${AppSettings.getApiEndpoint()}/${DATASET_BASE_URL}/${did}/${DATASET_UPDATE_PUBLICITY_URL}`,
      {}
    );
  }
}
