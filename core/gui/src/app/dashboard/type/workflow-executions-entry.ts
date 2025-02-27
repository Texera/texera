export interface WorkflowExecutionsEntry {
  eId: number;
  vId: number;
  sId: number;
  userName: string;
  googleAvatar: string;
  name: string;
  startingTime: number;
  completionTime: number;
  status: number;
  result: string;
  bookmarked: boolean;
  logLocation: string;
}
