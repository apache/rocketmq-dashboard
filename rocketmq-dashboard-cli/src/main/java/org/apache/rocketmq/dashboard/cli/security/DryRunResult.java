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
package org.apache.rocketmq.dashboard.cli.security;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DryRunResult {

    private String operation;
    private boolean willExecute;
    private List<String> affectedResources;
    private Map<String, Object> changeDetails;
    private String estimatedDuration;
    private List<String> warnings;

    public String toDisplay() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════\n");
        sb.append("  DRY RUN PREVIEW\n");
        sb.append("═══════════════════════════════════════════\n\n");

        sb.append("Operation: ").append(operation != null ? operation : "unknown").append('\n');
        sb.append("Will Execute: ").append(willExecute ? "YES" : "NO").append('\n');

        if (estimatedDuration != null) {
            sb.append("Estimated Duration: ").append(estimatedDuration).append('\n');
        }

        if (affectedResources != null && !affectedResources.isEmpty()) {
            sb.append("\nAffected Resources:\n");
            for (int i = 0; i < affectedResources.size(); i++) {
                sb.append(String.format("  %d. %s%n", i + 1, affectedResources.get(i)));
            }
        }

        if (changeDetails != null && !changeDetails.isEmpty()) {
            sb.append("\nChange Details:\n");
            for (Map.Entry<String, Object> entry : changeDetails.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ")
                        .append(entry.getValue()).append('\n');
            }
        }

        if (warnings != null && !warnings.isEmpty()) {
            sb.append("\nWARNINGS:\n");
            for (String warning : warnings) {
                sb.append("  ! ").append(warning).append('\n');
            }
        }

        sb.append("\n═══════════════════════════════════════════\n");
        return sb.toString();
    }
}
