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
package org.apache.rocketmq.studio.ops.ai.conversation.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The paste-ready MCP configuration for driving this conversation's tools from an external agent
 * (Claude Desktop, Cursor, ...). Mirrors the frozen TS interface {@code AiRmqctlConfigVO} in
 * {@code web/src/api/aiEvents.ts}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRmqctlConfigVO {

    /** The {@code {"mcpServers":{...}}} JSON document, ready to paste into the external agent. */
    private String snippet;

    /** The instance the tools are pinned to ({@code rmqctl --instance-id}). */
    private String instanceId;

    /** The Studio base URL the signed MCP requests must reach. */
    private String server;
}
