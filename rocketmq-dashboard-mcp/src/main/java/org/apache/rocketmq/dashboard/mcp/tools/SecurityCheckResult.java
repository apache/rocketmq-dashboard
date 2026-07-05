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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of a security check performed by {@link SecurityGate}.
 * Determines whether a tool call is ALLOWed, DRY_RUN (preview only), or BLOCKed.
 */
public class SecurityCheckResult {

    /** The action to take for this tool call. */
    public enum Action {
        ALLOW,
        DRY_RUN,
        BLOCK
    }

    private final Action action;
    private final String message;
    private final Map<String, Object> dryRunData;

    private SecurityCheckResult(Action action, String message, Map<String, Object> dryRunData) {
        this.action = action;
        this.message = message;
        this.dryRunData = dryRunData;
    }

    /** Creates an ALLOW result with no restrictions. */
    public static SecurityCheckResult allow() {
        return new SecurityCheckResult(Action.ALLOW, "Operation allowed", null);
    }

    /** Creates a DRY_RUN result with the given preview message. */
    public static SecurityCheckResult dryRun(String message) {
        Map<String, Object> dryRunData = new LinkedHashMap<>();
        dryRunData.put("mode", "dry_run");
        dryRunData.put("note", "This is a preview only. Use --confirm to apply changes.");
        return new SecurityCheckResult(Action.DRY_RUN, message, dryRunData);
    }

    /** Creates a BLOCK result with the given reason message. */
    public static SecurityCheckResult block(String message) {
        return new SecurityCheckResult(Action.BLOCK, message, null);
    }

    public Action getAction() {
        return action;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getDryRunData() {
        return dryRunData;
    }

    public boolean isAllowed() {
        return action == Action.ALLOW;
    }

    public boolean isDryRun() {
        return action == Action.DRY_RUN;
    }

    public boolean isBlocked() {
        return action == Action.BLOCK;
    }

    @Override
    public String toString() {
        return "SecurityCheckResult{action=" + action + ", message='" + message + "'}";
    }
}
