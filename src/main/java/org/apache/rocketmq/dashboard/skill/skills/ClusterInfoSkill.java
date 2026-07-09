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
package org.apache.rocketmq.dashboard.skill.skills;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.dashboard.service.ClusterService;
import org.apache.rocketmq.dashboard.skill.AbstractSkill;
import org.apache.rocketmq.dashboard.skill.Skill;
import org.apache.rocketmq.dashboard.skill.SkillParameter;
import org.apache.rocketmq.dashboard.skill.SkillResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Skill for querying cluster information from RocketMQ.
 * Provides cluster overview and broker configuration details.
 */
@Slf4j
@Component
public class ClusterInfoSkill extends AbstractSkill {

    @Autowired
    private ClusterService clusterService;

    @Override
    public String getId() {
        return "cluster.info";
    }

    @Override
    public String getName() {
        return "Cluster Info";
    }

    @Override
    public String getDescription() {
        return "Query RocketMQ cluster information. "
                + "Use action='overview' to get cluster overview, or action='broker-config' with brokerAddr to get broker configuration.";
    }

    @Override
    public String getResourceType() {
        return "cluster";
    }

    @Override
    public String getVerb() {
        return "info";
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.L1;
    }

    @Override
    public List<SkillParameter> getParameters() {
        return Collections.singletonList(
                SkillParameter.builder()
                        .name("action")
                        .type("ENUM")
                        .required(true)
                        .description("The query action: 'overview' for cluster overview, 'broker-config' for broker configuration")
                        .allowedValues(java.util.Arrays.asList("overview", "broker-config"))
                        .build()
        );
    }

    @Override
    public SkillResult execute(Map<String, Object> parameters) {
        try {
            String action = getRequiredParameter(parameters, "action", String.class);

            switch (action.toLowerCase()) {
                case "overview":
                    return executeOverview();
                case "broker-config":
                    String brokerAddr = getRequiredParameter(parameters, "brokerAddr", String.class);
                    return executeBrokerConfig(brokerAddr);
                default:
                    return SkillResult.failure("Unknown action: " + action + ". Supported: overview, broker-config");
            }
        } catch (IllegalArgumentException e) {
            return SkillResult.failure(e.getMessage());
        } catch (Exception e) {
            log.error("Error executing ClusterInfoSkill", e);
            return SkillResult.failure("Failed to query cluster info: " + e.getMessage());
        }
    }

    private SkillResult executeOverview() {
        Map<String, Object> clusterInfo = clusterService.list();
        return SkillResult.successObject(clusterInfo);
    }

    private SkillResult executeBrokerConfig(String brokerAddr) {
        java.util.Properties config = clusterService.getBrokerConfig(brokerAddr);
        if (config == null) {
            return SkillResult.failure("Broker not found or unable to retrieve config: " + brokerAddr);
        }
        return SkillResult.successObject(config);
    }
}
