import { Injectable } from "@angular/core";
import { interval, Observable, Subject, Subscription, timer } from "rxjs";
import { webSocket, WebSocketSubject } from "rxjs/webSocket";
import {
  RuntimeClusterInfo,
  TexeraWebsocketEvent,
  TexeraWebsocketEventTypeMap,
  TexeraWebsocketEventTypes,
  TexeraWebsocketRequest,
  TexeraWebsocketRequestTypeMap,
  TexeraWebsocketRequestTypes,
} from "../../types/workflow-websocket.interface";
import { delayWhen, filter, map, retryWhen, tap } from "rxjs/operators";
import { environment } from "../../../../environments/environment";
import { AuthService } from "../../../common/service/user/auth.service";
import { getWebsocketUrl } from "src/app/common/util/url";
import { AppSettings } from "../../../common/app-setting";
import { HttpClient, HttpParams } from "@angular/common/http";

export const WS_HEARTBEAT_INTERVAL_MS = 10000;
export const WS_RECONNECT_INTERVAL_MS = 3000;

@Injectable({
  providedIn: "root",
})
export class WorkflowWebsocketService {
  private static readonly TEXERA_WEBSOCKET_ENDPOINT = "wsapi/workflow-websocket";

  public isConnected: boolean = false;
  public numWorkers: number = -1;
  private connectedWid: number = 0;

  private websocket?: WebSocketSubject<TexeraWebsocketEvent | TexeraWebsocketRequest>;
  private wsWithReconnectSubscription?: Subscription;
  private readonly webSocketResponseSubject: Subject<TexeraWebsocketEvent> = new Subject();

  public getRuntime(wid: number, uid: number | undefined): Observable<RuntimeClusterInfo> {
    let BASE_URL = `${AppSettings.getApiEndpoint()}/runtime`;
    const params = new HttpParams().set("wid", wid).set("uid", uid ?? 0);
    return this.http.get<RuntimeClusterInfo>(`${BASE_URL}/get`, { params });
  }

  constructor(private http: HttpClient) {
    // setup heartbeat
    interval(WS_HEARTBEAT_INTERVAL_MS).subscribe(_ => this.send("HeartBeatRequest", {}));
  }

  public websocketEvent(): Observable<TexeraWebsocketEvent> {
    return this.webSocketResponseSubject;
  }

  /**
   * Subscribe to a particular type of workflow websocket event
   */
  public subscribeToEvent<T extends TexeraWebsocketEventTypes>(
    type: T
  ): Observable<{ type: T } & TexeraWebsocketEventTypeMap[T]> {
    return this.websocketEvent().pipe(
      filter(event => event.type === type),
      map(event => event as { type: T } & TexeraWebsocketEventTypeMap[T])
    );
  }

  public send<T extends TexeraWebsocketRequestTypes>(type: T, payload: TexeraWebsocketRequestTypeMap[T]): void {
    const request = {
      type,
      ...payload,
    } as any as TexeraWebsocketRequest;
    this.websocket?.next(request);
  }

  public closeWebsocket() {
    this.wsWithReconnectSubscription?.unsubscribe();
    this.websocket?.complete();
  }

  public openWebsocket(wId: number, uId: number | undefined) {
    this.getRuntime(wId, uId).subscribe(info => {
      const websocketUrl =
        getWebsocketUrl(WorkflowWebsocketService.TEXERA_WEBSOCKET_ENDPOINT, info.port.toString()) +
        "?wid=" +
        wId +
        (environment.userSystemEnabled && AuthService.getAccessToken() !== null
          ? "&access-token=" + AuthService.getAccessToken()
          : "");
      this.websocket = webSocket<TexeraWebsocketEvent | TexeraWebsocketRequest>(websocketUrl);
      // setup reconnection logic
      const wsWithReconnect = this.websocket.pipe(
        retryWhen(errors =>
          errors.pipe(
            tap(_ => (this.isConnected = false)), // update connection status
            tap(_ =>
              console.log(`websocket connection lost, reconnecting in ${WS_RECONNECT_INTERVAL_MS / 1000} seconds`)
            ),
            delayWhen(_ => timer(WS_RECONNECT_INTERVAL_MS)), // reconnect after delay
            tap(_ => {
              this.send("HeartBeatRequest", {}); // try to send heartbeat immediately after reconnect
            })
          )
        )
      );
      // set up event listener on re-connectable websocket observable
      this.wsWithReconnectSubscription = wsWithReconnect.subscribe(event =>
        this.webSocketResponseSubject.next(event as TexeraWebsocketEvent)
      );

    // refresh connection status
    this.websocketEvent().subscribe(evt => {
      if (evt.type === "ClusterStatusUpdateEvent") {
        this.numWorkers = evt.numWorkers;
      }
      this.isConnected = true;
      this.connectedWid = wId;
    });
    });
  }

  public reopenWebsocket(wId: number, uId: number | undefined) {
    if (this.isConnected && this.connectedWid === wId) {
      // prevent reconnections
      return;
    }
    this.closeWebsocket();
    this.openWebsocket(wId, uId);
  }
}
