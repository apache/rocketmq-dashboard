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
 * What the server can actually run right now, probed at request time. Mirrors the frozen TS
 * interface {@code AiAgentCapabilitiesVO} in {@code web/src/api/aiEvents.ts}: all five fields are
 * required booleans, never null, so the UI can grey out engines and tool tiers that are absent
 * instead of guessing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAgentCapabilitiesVO {

    /** The {@code rmqctl} binary is on PATH and executable. */
    private boolean rmqctlAvailable;

    /** The {@code claude} CLI is on PATH and executable. */
    private boolean claudeAvailable;

    /** The {@code qoder} CLI is on PATH and executable. */
    private boolean qoderAvailable;

    /** The Studio MCP server is enabled, so agent tools work at all. */
    private boolean mcpEnabled;

    /** Destructive (L3) tools are allowed by configuration. */
    private boolean l3ToolsAllowed;
}
