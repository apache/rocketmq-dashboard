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
import org.apache.rocketmq.dashboard.service.MessageService;
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
 * Skill for querying message information from RocketMQ.
 * Supports querying messages by ID, by topic with time range, or by topic and key.
 */
@Slf4j
@Component
public class MessageQuerySkill extends AbstractSkill {

    @Autowired
    private MessageService messageService;

    @Override
    public String getId() {
        return "message.query";
    }

    @Override
    public String getName() {
        return "Message Query";
    }

    @Override
    public String getDescription() {
        return "Query message information from RocketMQ. "
                + "Use action='by-id' to get message by ID, "
                + "action='by-topic' to query messages by topic and time range, "
                + "or action='by-key' to query messages by topic and key.";
    }

    @Override
    public String getResourceType() {
        return "message";
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
                        .description("Query action: 'by-id', 'by-topic', or 'by-key'")
                        .allowedValues(Arrays.asList("by-id", "by-topic", "by-key"))
                        .build(),
                SkillParameter.builder()
                        .name("msgId")
                        .type("STRING")
                        .required(false)
                        .description("Message ID (required for action='by-id')")
                        .build(),
                SkillParameter.builder()
                        .name("topic")
                        .type("STRING")
                        .required(false)
                        .description("Topic name (required for action='by-topic' and 'by-key')")
                        .build(),
                SkillParameter.builder()
                        .name("key")
                        .type("STRING")
                        .required(false)
                        .description("Message key (required for action='by-key')")
                        .build(),
                SkillParameter.builder()
                        .name("beginTime")
                        .type("LONG")
                        .required(false)
                        .description("Begin time in milliseconds (for action='by-topic' and 'by-key')")
                        .build(),
                SkillParameter.builder()
                        .name("endTime")
                        .type("LONG")
                        .required(false)
                        .description("End time in milliseconds (for action='by-topic' and 'by-key')")
                        .build()
        );
    }

    @Override
    public SkillResult execute(Map<String, Object> parameters) {
        try {
            String action = getRequiredParameter(parameters, "action", String.class);

            switch (action.toLowerCase()) {
                case "by-id":
                    String msgId = getRequiredParameter(parameters, "msgId", String.class);
                    return executeById(msgId);
                case "by-topic":
                    String topic = getRequiredParameter(parameters, "topic", String.class);
                    Long beginTime = getParameter(parameters, "beginTime", Long.class);
                    Long endTime = getParameter(parameters, "endTime", Long.class);
                    return executeByTopic(topic, beginTime, endTime);
                case "by-key":
                    String topicForKey = getRequiredParameter(parameters, "topic", String.class);
                    String key = getRequiredParameter(parameters, "key", String.class);
                    Long beginTimeForKey = getParameter(parameters, "beginTime", Long.class);
                    Long endTimeForKey = getParameter(parameters, "endTime", Long.class);
                    return executeByKey(topicForKey, key, beginTimeForKey, endTimeForKey);
                default:
                    return SkillResult.failure("Unknown action: " + action);
            }
        } catch (IllegalArgumentException e) {
            return SkillResult.failure(e.getMessage());
        } catch (Exception e) {
            log.error("Error executing MessageQuerySkill", e);
            return SkillResult.failure("Failed to query messages: " + e.getMessage());
        }
    }

    private SkillResult executeById(String msgId) {
        Object message = messageService.getMessageById(msgId);
        if (message == null) {
            return SkillResult.failure("Message not found: " + msgId);
        }
        return SkillResult.successObject(message);
    }

    private SkillResult executeByTopic(String topic, Long beginTime, Long endTime) {
        if (beginTime == null || endTime == null) {
            return SkillResult.failure("beginTime and endTime are required for action='by-topic'");
        }
        List<?> messages = messageService.queryMessageByTopic(topic, beginTime, endTime);
        return SkillResult.successList(messages);
    }

    private SkillResult executeByKey(String topic, String key, Long beginTime, Long endTime) {
        List<?> messages;
        if (beginTime != null && endTime != null) {
            messages = messageService.queryMessageByTopicAndKey(topic, key, beginTime, endTime);
        } else {
            messages = messageService.queryMessageByTopicAndKey(topic, key);
        }
        return SkillResult.successList(messages);
    }
}
