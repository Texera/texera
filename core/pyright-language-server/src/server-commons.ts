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

import { ServerOptions, WebSocketServer } from "ws";
import { IncomingMessage, Server } from "node:http";
import { fileURLToPath, URL } from "node:url";
import { Socket } from "node:net";
import { dirname } from "node:path";
import { IWebSocket, WebSocketMessageReader, WebSocketMessageWriter } from "vscode-ws-jsonrpc";
import { createConnection, createServerProcess, forward } from "vscode-ws-jsonrpc/server";
import { InitializeParams, InitializeRequest, Message } from "vscode-languageserver";
import * as cp from "child_process";

export enum LanguageName {
  /** https://nodejs.org/api/cli.html  */
  node = "node",
  /** https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html */
  java = "java"
}

export interface LanguageServerRunConfig {
  serverName: string;
  pathName: string;
  serverPort: number;
  runCommand: LanguageName | string;
  runCommandArgs: string[];
  wsServerOptions: ServerOptions,
  spawnOptions?: cp.SpawnOptions;
}

/**
 * start the language server inside the current process
 */
export const launchLanguageServer = (runconfig: LanguageServerRunConfig, socket: IWebSocket) => {
  const { serverName, runCommand, runCommandArgs, spawnOptions } = runconfig;
  // start the language server as an external process
  const reader = new WebSocketMessageReader(socket);
  const writer = new WebSocketMessageWriter(socket);
  const socketConnection = createConnection(reader, writer, () => socket.dispose());
  const serverConnection = createServerProcess(serverName, runCommand, runCommandArgs, spawnOptions);
  if (serverConnection) {
    forward(socketConnection, serverConnection, message => {
      if (Message.isRequest(message)) {
        console.log(`${serverName} Server received:`);
        console.log(message);
        if (message.method === InitializeRequest.type.method) {
          const initializeParams = message.params as InitializeParams;
          initializeParams.processId = process.pid;
        }
      }
      if (Message.isResponse(message)) {
        console.log(`${serverName} Server sent:`);
        console.log(message);
      }
      return message;
    });
  }
};

export const upgradeWsServer = (runconfig: LanguageServerRunConfig,
                                config: {
                                  server: Server,
                                  wss: WebSocketServer
                                }) => {
  config.server.on("upgrade", (request: IncomingMessage, socket: Socket, head: Buffer) => {
    const baseURL = `http://${request.headers.host}/`;
    const pathName = request.url !== undefined ? new URL(request.url, baseURL).pathname : undefined;
    if (pathName === runconfig.pathName) {
      config.wss.handleUpgrade(request, socket, head, webSocket => {
        const socket: IWebSocket = {
          send: content => webSocket.send(content, error => {
            if (error) {
              throw error;
            }
          }),
          onMessage: cb => webSocket.on("message", (data) => {
            console.log(data.toString());
            cb(data);
          }),
          onError: cb => webSocket.on("error", cb),
          onClose: cb => webSocket.on("close", cb),
          dispose: () => webSocket.close(),
        };
        // launch the server when the web socket is opened
        if (webSocket.readyState === webSocket.OPEN) {
          launchLanguageServer(runconfig, socket);
        } else {
          webSocket.on("open", () => {
            launchLanguageServer(runconfig, socket);
          });
        }
      });
    }
  });
};

/**
 * Solves: __dirname is not defined in ES module scope
 */
export const getLocalDirectory = (referenceUrl: string | URL) => {
  const __filename = fileURLToPath(referenceUrl);
  return dirname(__filename);
};