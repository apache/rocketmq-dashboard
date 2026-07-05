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
package org.apache.rocketmq.dashboard.mcp.tools;

import org.apache.rocketmq.dashboard.cli.schema.RiskLevel;
import org.apache.rocketmq.dashboard.cli.schema.ToolDefinition;

/**
 * Security gate that controls tool execution based on risk level.
 * <p>
 * Default configuration:
 * <ul>
 *   <li>L1 (Read-only): allowed silently</li>
 *   <li>L2 (Controlled mutation): dry-run with preview</li>
 *   <li>L3 (Dangerous operations): blocked</li>
 * </ul>
 * L3 operations require explicit opt-in via {@code --enable-dangerous-ops}.
 */
public class SecurityGate {

    private boolean allowL1 = true;
    private boolean allowL2 = true;
    private boolean allowL3 = false;

    /**
     * Default constructor: L1 and L2 allowed, L3 blocked.
     */
    public SecurityGate() {
    }

    /**
     * Constructor that optionally enables L3 (dangerous) operations.
     *
     * @param allowL3 if true, L3 operations are allowed (with dry-run)
     */
    public SecurityGate(boolean allowL3) {
        this.allowL3 = allowL3;
    }

    /**
     * Check whether a tool call should be allowed, dry-run, or blocked.
     *
     * @param tool the tool definition to check
     * @return a SecurityCheckResult indicating the action to take
     */
    public SecurityCheckResult check(ToolDefinition tool) {
        if (tool == null || tool.getRiskLevel() == null) {
            return SecurityCheckResult.block("Unknown risk level");
        }
        RiskLevel level = tool.getRiskLevel();
        switch (level) {
            case L1:
                return allowL1
                        ? SecurityCheckResult.allow()
                        : SecurityCheckResult.block("L1 disabled");
            case L2:
                return allowL2
                        ? SecurityCheckResult.dryRun("L2 operation requires confirmation. "
                                + "Use --confirm to apply changes.")
                        : SecurityCheckResult.block("L2 disabled");
            case L3:
                return allowL3
                        ? SecurityCheckResult.dryRun("L3 operation requires explicit confirmation. "
                                + "This is a dangerous operation.")
                        : SecurityCheckResult.block("L3 blocked. Enable with --enable-dangerous-ops");
            default:
                return SecurityCheckResult.block("Unknown risk level: " + level);
        }
    }

    // ---- Configuration setters ------------------------------------------------

    public void setAllowL1(boolean allowL1) {
        this.allowL1 = allowL1;
    }

    public void setAllowL2(boolean allowL2) {
        this.allowL2 = allowL2;
    }

    public void setAllowL3(boolean allowL3) {
        this.allowL3 = allowL3;
    }

    public boolean isAllowL1() {
        return allowL1;
    }

    public boolean isAllowL2() {
        return allowL2;
    }

    public boolean isAllowL3() {
        return allowL3;
    }
}
