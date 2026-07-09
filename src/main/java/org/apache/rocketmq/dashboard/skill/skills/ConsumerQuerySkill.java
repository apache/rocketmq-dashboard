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
import org.apache.rocketmq.dashboard.service.ConsumerService;
import org.apache.rocketmq.dashboard.skill.AbstractSkill;
import org.apache.rocketmq.dashboard.skill.Skill;
import org.apache.rocketmq.dashboard.skill.SkillParameter;
import org.apache.rocketmq.dashboard.skill.SkillResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Skill for querying consumer group information from RocketMQ.
 * Supports listing consumer groups and querying group details.
 */
@Slf4j
@Component
public class ConsumerQuerySkill extends AbstractSkill {

    @Autowired
    private ConsumerService consumerService;

    @Override
    public String getId() {
        return "consumer.query";
    }

    @Override
    public String getName() {
        return "Consumer Query";
    }

    @Override
    public String getDescription() {
        return "Query consumer group information from RocketMQ cluster. "
                + "Use action='list' to list all consumer groups, or action='detail' with group parameter to get group details.";
    }

    @Override
    public String getResourceType() {
        return "group";
    }

    @Override
    public String getVerb() {
        return "query";
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.L1;
    }

    @Override
    public List<SkillParameter> getParameters() {
        return Arrays.asList(
                SkillParameter.builder()
                        .name("action")
                        .type("ENUM")
                        .required(true)
                        .description("The query action: 'list' to list all groups, 'detail' to get group details")
                        .allowedValues(Arrays.asList("list", "detail"))
                        .build(),
                SkillParameter.builder()
                        .name("group")
                        .type("STRING")
                        .required(false)
                        .description("Consumer group name (required when action is 'detail')")
                        .build()
        );
    }

    @Override
    public SkillResult execute(Map<String, Object> parameters) {
        try {
            String action = getRequiredParameter(parameters, "action", String.class);

            switch (action.toLowerCase()) {
                case "list":
                    return executeList();
                case "detail":
                    String group = getRequiredParameter(parameters, "group", String.class);
                    return executeDetail(group);
                default:
                    return SkillResult.failure("Unknown action: " + action + ". Supported: list, detail");
            }
        } catch (IllegalArgumentException e) {
            return SkillResult.failure(e.getMessage());
        } catch (Exception e) {
            log.error("Error executing ConsumerQuerySkill", e);
            return SkillResult.failure("Failed to query consumer groups: " + e.getMessage());
        }
    }

    private SkillResult executeList() {
        List<?> groups = consumerService.listConsumerGroups();
        return SkillResult.successList(groups);
    }

    private SkillResult executeDetail(String group) {
        Object groupInfo = consumerService.getConsumerGroup(group);
        if (groupInfo == null) {
            return SkillResult.failure("Consumer group not found: " + group);
        }
        return SkillResult.successObject(groupInfo);
    }
}
