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
package org.apache.rocketmq.studio.ops.ai.tool.contract.alert;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.rocketmq.studio.ops.alert.AlertRuleVO;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AlertRuleListItem(
        Long id,
        String name,
        String metric,
        String operator,
        double threshold,
        String thresholdUnit,
        String duration,
        List<String> channels,
        boolean enabled,
        String description) {

    public AlertRuleListItem {
        channels = channels == null ? List.of() : channels;
    }

    public static AlertRuleListItem from(AlertRuleVO rule) {
        return new AlertRuleListItem(
                rule.getId(),
                rule.getName(),
                rule.getMetric(),
                rule.getOperator(),
                rule.getThreshold(),
                rule.getThresholdUnit(),
                rule.getDuration(),
                rule.getChannels(),
                rule.isEnabled(),
                rule.getDescription());
    }
}
