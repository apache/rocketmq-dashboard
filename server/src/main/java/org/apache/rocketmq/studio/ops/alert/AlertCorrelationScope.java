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
package org.apache.rocketmq.studio.ops.alert;

import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;

/** Shared instance and resource-scope matching for cross-domain alert handling. */
final class AlertCorrelationScope {
    private static final Set<String> RESOURCE_LABELS = Set.of(
            "brokerName", "brokerAddr", "clusterName", "clusterId");

    private AlertCorrelationScope() {
    }

    static boolean matches(SystemAlertVO source, SystemAlertVO candidate) {
        if (!StringUtils.hasText(source.getInstanceId())
                || !source.getInstanceId().trim().equals(candidate.getInstanceId())) {
            return false;
        }
        Map<String, String> sourceLabels = source.getLabels() == null ? Map.of() : source.getLabels();
        Map<String, String> candidateLabels = candidate.getLabels() == null ? Map.of() : candidate.getLabels();
        for (String label : RESOURCE_LABELS) {
            String sourceValue = sourceLabels.get(label);
            String candidateValue = candidateLabels.get(label);
            if (StringUtils.hasText(sourceValue) && StringUtils.hasText(candidateValue)
                    && !sourceValue.trim().equals(candidateValue.trim())) {
                return false;
            }
        }
        return true;
    }
}
