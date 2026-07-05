/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.dashboard.mcp.transport;

/**
 * Functional interface for handling incoming MCP JSON-RPC messages.
 * The implementation processes the JSON request and returns a JSON response.
 */
@FunctionalInterface
public interface McpMessageHandler {

    /**
     * Handle an incoming JSON-RPC message and return the JSON-RPC response.
     *
     * @param jsonMessage the raw JSON-RPC request string
     * @return the raw JSON-RPC response string
     */
    String handleMessage(String jsonMessage);
}
