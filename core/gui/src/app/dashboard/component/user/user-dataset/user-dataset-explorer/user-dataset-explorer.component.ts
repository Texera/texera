import { Component, OnInit } from "@angular/core";
import { ActivatedRoute, NavigationEnd, Router } from "@angular/router";
import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { DatasetService } from "../../../../service/user/dataset/dataset.service";
import { NzResizeEvent } from "ng-zorro-antd/resizable";
import { DatasetFileNode, getFullPathFromDatasetFileNode } from "../../../../../common/type/datasetVersionFileTree";
import { DatasetVersion } from "../../../../../common/type/dataset";
import { switchMap } from "rxjs/operators";
import { NotificationService } from "../../../../../common/service/notification/notification.service";

@UntilDestroy()
@Component({
  templateUrl: "./user-dataset-explorer.component.html",
  styleUrls: ["./user-dataset-explorer.component.scss"],
})
export class UserDatasetExplorerComponent implements OnInit {
  public did: number | undefined;
  public datasetName: string = "";
  public datasetDescription: string = "";
  public datasetCreationTime: string = "";
  public datasetIsPublic: boolean = false;
  public userDatasetAccessLevel: "READ" | "WRITE" | "NONE" = "NONE";

  public currentDisplayedFileName: string = "";

  public isRightBarCollapsed = false;
  public isMaximized = false;

  public versions: ReadonlyArray<DatasetVersion> = [];
  public selectedVersion: DatasetVersion | undefined;
  public fileTreeNodeList: DatasetFileNode[] = [];

  public isCreatingVersion: boolean = false;
  public isCreatingDataset: boolean = false;
  public versionCreatorBaseVersion: DatasetVersion | undefined;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private datasetService: DatasetService,
    private notificationService: NotificationService
  ) {}

  // item for control the resizeable sider
  MAX_SIDER_WIDTH = 600;
  MIN_SIDER_WIDTH = 150;
  siderWidth = 200;
  id = -1;
  onSideResize({ width }: NzResizeEvent): void {
    cancelAnimationFrame(this.id);
    this.id = requestAnimationFrame(() => {
      this.siderWidth = width!;
    });
  }

  ngOnInit(): void {
    this.route.params
      .pipe(
        switchMap(params => {
          const param = params["did"];
          if (param !== "create") {
            this.did = param;
            this.renderDatasetViewSider();
            this.retrieveDatasetInfo();
            this.retrieveDatasetVersionList();
          } else {
            this.renderDatasetCreatorSider();
          }
          return this.route.data; // or some other observable
        }),
        untilDestroyed(this)
      )
      .subscribe();
  }

  renderDatasetViewSider() {
    this.isCreatingVersion = false;
    this.isCreatingDataset = false;
  }
  renderDatasetCreatorSider() {
    this.isCreatingVersion = false;
    this.isCreatingDataset = true;
    this.siderWidth = this.MAX_SIDER_WIDTH;
  }

  renderVersionCreatorSider() {
    if (this.did) {
      this.datasetService
        .retrieveDatasetLatestVersion(this.did)
        .pipe(untilDestroyed(this))
        .subscribe(latestVersion => {
          this.versionCreatorBaseVersion = latestVersion;
          this.isCreatingDataset = false;
          this.isCreatingVersion = true;
          this.siderWidth = this.MAX_SIDER_WIDTH;
        });
    }
  }

  public onCreationFinished(creationID: number) {
    if (creationID != 0) {
      // creation succeed
      if (this.isCreatingVersion) {
        this.retrieveDatasetVersionList();
        this.renderDatasetViewSider();
      } else {
        this.router.navigate([`/dashboard/dataset/${creationID}`]);
      }
    } else {
      // creation failed
      if (this.isCreatingVersion) {
        this.isCreatingVersion = false;
        this.isCreatingDataset = false;
        this.retrieveDatasetVersionList();
      } else {
        this.router.navigate(["/dashboard/dataset"]);
      }
    }
  }

  public onClickOpenVersionCreator() {
    this.renderVersionCreatorSider();
  }

  onPublicStatusChange(checked: boolean): void {
    // Handle the change in dataset public status
    if (this.did) {
      this.datasetService
        .updateDatasetPublicity(this.did)
        .pipe(untilDestroyed(this))
        .subscribe({
          next: (res: Response) => {
            this.datasetIsPublic = checked;
            let state = "public";
            if (!this.datasetIsPublic) {
              state = "private";
            }
            this.notificationService.success(`Dataset ${this.datasetName} is now ${state}`);
          },
          error: (err: unknown) => {
            this.notificationService.error("Fail to change the dataset publicity");
          },
        });
    }
  }

  retrieveDatasetInfo() {
    if (this.did) {
      this.datasetService
        .getDataset(this.did)
        .pipe(untilDestroyed(this))
        .subscribe(dashboardDataset => {
          const dataset = dashboardDataset.dataset;
          this.datasetName = dataset.name;
          this.datasetDescription = dataset.description;
          this.userDatasetAccessLevel = dashboardDataset.accessPrivilege;
          this.datasetIsPublic = dataset.isPublic === 1;
          if (typeof dataset.creationTime === "number") {
            this.datasetCreationTime = new Date(dataset.creationTime).toString();
          }
        });
    }
  }

  retrieveDatasetVersionList() {
    if (this.did) {
      this.datasetService
        .retrieveDatasetVersionList(this.did)
        .pipe(untilDestroyed(this))
        .subscribe(versionNames => {
          this.versions = versionNames;
          // by default, the selected version is the 1st element in the retrieved list
          // which is guaranteed(by the backend) to be the latest created version.
          this.selectedVersion = this.versions[0];
          this.onVersionSelected(this.selectedVersion);
        });
    }
  }

  loadFileContent(node: DatasetFileNode) {
    this.currentDisplayedFileName = getFullPathFromDatasetFileNode(node);
  }

  onClickDownloadCurrentFile() {
    if (this.did && this.selectedVersion && this.selectedVersion.dvid) {
      this.datasetService
        .retrieveDatasetVersionSingleFile(this.currentDisplayedFileName)
        .pipe(untilDestroyed(this))
        .subscribe({
          next: blob => {
            // download this blob, the filename is the direct name of this file(e.g., /a/b/c.txt, name would be c.txt)
            const url = URL.createObjectURL(blob);

            // Create a temporary link element
            const a = document.createElement("a");
            a.href = url;
            a.download = this.currentDisplayedFileName.split("/").pop() || "download"; // Extract the file name

            // Append the link to the body
            document.body.appendChild(a);
            // Trigger the download
            a.click();
            // Remove the link after download
            document.body.removeChild(a);
            // Release the blob URL
            URL.revokeObjectURL(url);
            this.notificationService.info(`File ${this.currentDisplayedFileName} is downloading`);
          },
          error: (error: unknown) => {
            this.notificationService.error(`Error downloading file '${this.currentDisplayedFileName}'`);
          },
        });
    }
  }

  extractVersionPath(currentDisplayedFileName: string): string {
    const pathParts = currentDisplayedFileName.split("/");

    return `/${pathParts[1]}/${pathParts[2]}/${pathParts[3]}`;
  }

  onClickDownloadVersionAsZip() {
    if (this.did && this.selectedVersion && this.selectedVersion.dvid) {
      const versionPath = this.extractVersionPath(this.currentDisplayedFileName);
      this.datasetService
        .retrieveDatasetVersionZip(versionPath)
        .pipe(untilDestroyed(this))
        .subscribe({
          next: blob => {
            // Create URL for the ZIP blob
            const url = URL.createObjectURL(blob);

            // Create a temporary link element
            const a = document.createElement("a");
            a.href = url;
            a.download = `${this.datasetName}-${this.selectedVersion?.name}.zip`; // Filename for ZIP file

            // Append the link to the body
            document.body.appendChild(a);
            // Trigger the download
            a.click();
            // Remove the link after download
            document.body.removeChild(a);
            // Release the blob URL
            URL.revokeObjectURL(url);

            this.notificationService.info(`Version ${this.selectedVersion?.name} is downloading as ZIP`);
          },
          error: (error: unknown) => {
            this.notificationService.error(`Error downloading version '${this.selectedVersion?.name}' as ZIP`);
          },
        });
    }
  }

  onClickScaleTheView() {
    this.isMaximized = !this.isMaximized;
  }

  onClickHideRightBar() {
    this.isRightBarCollapsed = !this.isRightBarCollapsed;
  }

  onVersionSelected(version: DatasetVersion): void {
    this.selectedVersion = version;
    if (this.did && this.selectedVersion.dvid)
      this.datasetService
        .retrieveDatasetVersionFileTree(this.did, this.selectedVersion.dvid)
        .pipe(untilDestroyed(this))
        .subscribe(dataNodeList => {
          this.fileTreeNodeList = dataNodeList;
          let currentNode = this.fileTreeNodeList[0];
          while (currentNode.type === "directory" && currentNode.children) {
            currentNode = currentNode.children[0];
          }
          this.loadFileContent(currentNode);
        });
  }

  onVersionFileTreeNodeSelected(node: DatasetFileNode) {
    this.loadFileContent(node);
  }

  isDisplayingDataset(): boolean {
    return !this.isCreatingDataset && !this.isCreatingVersion;
  }

  userHasWriteAccess(): boolean {
    return this.userDatasetAccessLevel == "WRITE";
  }
}
