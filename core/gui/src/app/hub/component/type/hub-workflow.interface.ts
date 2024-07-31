import { WorkflowContent } from "../../../common/type/workflow";

export interface OwnerInfo {
  uid: number | undefined;
  name: string | undefined;
  googleAvatar: string | undefined;
}

export interface HubWorkflow {
  name: string;
  description: string | undefined;
  wid: number | undefined;
  creationTime: number | undefined;
  lastModifiedTime: number | undefined;
  owner: OwnerInfo | undefined;
}
