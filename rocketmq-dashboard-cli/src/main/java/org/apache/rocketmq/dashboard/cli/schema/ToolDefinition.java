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
package org.apache.rocketmq.dashboard.cli.schema;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {
    /** Full tool name, e.g. "rmq.topic.create" or "rmq.group.reset-offset". */
    private String name;
    /** Resource domain, e.g. "topic", "group", "cluster". */
    private String resource;
    /** Operation verb, e.g. "create", "list", "reset-offset". */
    private String verb;
    /** Risk classification for this operation. */
    private RiskLevel riskLevel;
    /** Human-readable description of what the tool does. */
    private String description;
    /** Parameter definitions for the tool. */
    private List<ParamSchema> params;
    /** Expected return type, e.g. "LIST", "OBJECT", "VOID". */
    private String returnType;

    /**
     * Returns the MCP-compatible tool name, with hyphens in the verb replaced by underscores.
     * Example: "rmq.group.reset-offset" becomes "rmq.group.reset_offset".
     */
    public String getMcpToolName() {
        return "rmq." + resource + "." + verb.replace("-", "_");
    }

    /**
     * Returns the CLI command representation as "resource verb".
     * Example: "group reset-offset".
     */
    public String getCliCommand() {
        return resource + " " + verb;
    }
}
