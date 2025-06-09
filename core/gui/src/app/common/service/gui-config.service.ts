/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { GuiConfig } from "../type/gui-config";
import { Observable, tap, map, catchError } from "rxjs";
import { throwError } from "rxjs";
import { AppSettings } from "../app-setting";

@Injectable({ providedIn: "root" })
export class GuiConfigService {
  private config!: GuiConfig;

  constructor(private http: HttpClient) {}

  load(): Observable<GuiConfig> {
    return this.http.get<GuiConfig>(`${AppSettings.getApiEndpoint()}/user-config/gui`).pipe(
      tap(config => {
        this.config = config;
        console.log("GUI configuration loaded successfully from backend");
      }),
      catchError((error: unknown) => {
        console.error("Failed to load GUI configuration:", error);
        return throwError(() => new Error(`Failed to load GUI configuration from backend: ${error}`));
      })
    );
  }

  get env(): GuiConfig {
    if (!this.config) {
      throw new Error("GUI configuration not loaded yet. Make sure load() is called during app initialization");
    }
    return this.config;
  }
}
