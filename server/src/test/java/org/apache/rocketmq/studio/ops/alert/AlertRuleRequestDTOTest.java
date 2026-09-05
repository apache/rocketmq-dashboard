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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertRuleRequestDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private AlertRuleRequestDTO validPayload() {
        AlertRuleRequestDTO dto = new AlertRuleRequestDTO();
        dto.setId(1L);
        dto.setName("disk-high");
        dto.setMetric("broker.disk.usage_ratio");
        dto.setOperator(">");
        dto.setThreshold(0.9);
        dto.setDuration("5m");
        dto.setAggregation("MAX");
        dto.setWindowSeconds(60);
        dto.setChannels(List.of("email"));
        dto.setEnabled(true);
        dto.setSeverity("warning");
        dto.setInstanceId("inst-1");
        dto.setConsecutiveSamples(2);
        dto.setReminderInterval("10m");
        return dto;
    }

    private Set<String> messagesOf(AlertRuleRequestDTO dto) {
        return validator.validate(dto).stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.toSet());
    }

    @Test
    void acceptsCompletePayload() {
        assertTrue(validator.validate(validPayload()).isEmpty());
    }

    @Test
    void rejectsBlankName() {
        AlertRuleRequestDTO dto = validPayload();
        dto.setName(" ");

        Set<String> messages = messagesOf(dto);

        assertTrue(messages.contains("name is required"));
    }

    @Test
    void rejectsUnknownOperator() {
        AlertRuleRequestDTO dto = validPayload();
        dto.setOperator("~");

        Set<String> messages = messagesOf(dto);

        assertTrue(messages.contains("operator is invalid"));
    }

    @Test
    void acceptsUnavailableOperator() {
        AlertRuleRequestDTO dto = validPayload();
        dto.setOperator("UNAVAILABLE");

        assertTrue(validator.validate(dto).isEmpty());
    }

    @Test
    void rejectsMalformedDuration() {
        AlertRuleRequestDTO dto = validPayload();
        dto.setDuration("5x");

        Set<String> messages = messagesOf(dto);

        assertTrue(messages.contains("duration is invalid"));
    }

    @Test
    void rejectsUnknownAggregation() {
        AlertRuleRequestDTO dto = validPayload();
        dto.setAggregation("MEDIAN");

        Set<String> messages = messagesOf(dto);

        assertTrue(messages.contains("aggregation is invalid"));
    }

    @Test
    void rejectsNegativeWindowSeconds() {
        AlertRuleRequestDTO dto = validPayload();
        dto.setWindowSeconds(-1);

        Set<String> messages = messagesOf(dto);

        assertTrue(messages.contains("windowSeconds must not be negative"));
    }

    @Test
    void rejectsUnknownSeverity() {
        AlertRuleRequestDTO dto = validPayload();
        dto.setSeverity("fatal");

        Set<String> messages = messagesOf(dto);

        assertTrue(messages.contains("severity is invalid"));
    }

    @Test
    void rejectsBlankAndUnknownChannels() {
        AlertRuleRequestDTO dto = validPayload();
        dto.setChannels(List.of("", "slack"));

        Set<String> messages = messagesOf(dto);

        assertTrue(messages.contains("channel must not be blank"));
        assertTrue(messages.contains("channel is unsupported"));
    }

    @Test
    void rejectsZeroConsecutiveSamples() {
        AlertRuleRequestDTO dto = validPayload();
        dto.setConsecutiveSamples(0);

        Set<String> messages = messagesOf(dto);

        assertTrue(messages.contains("consecutiveSamples must be at least 1"));
    }

    @Test
    void rejectsMalformedReminderInterval() {
        AlertRuleRequestDTO dto = validPayload();
        dto.setReminderInterval("5x");

        Set<String> messages = messagesOf(dto);

        assertTrue(messages.contains("reminderInterval is invalid"));
    }

    @Test
    void rejectsOverlongNotificationTemplate() {
        AlertRuleRequestDTO dto = validPayload();
        dto.setNotificationTemplate("x".repeat(4001));

        Set<String> messages = messagesOf(dto);

        assertTrue(messages.contains("notificationTemplate must not exceed 4000 characters"));
    }

    @Test
    void mappingAppliesDefaultsAndNormalization() {
        AlertRuleRequestDTO dto = new AlertRuleRequestDTO();
        dto.setName("lag-high");
        dto.setMetric("  consumer.lag.total  ");
        dto.setOperator(">");
        dto.setThreshold(1000);
        dto.setChannels(List.of("  email ", " email ", "", "dingtalk"));

        AlertRuleVO vo = dto.toAlertRuleVO();

        assertEquals("lag-high", vo.getName());
        assertEquals("consumer.lag.total", vo.getMetric());
        assertEquals("LAST", vo.getAggregation());
        assertEquals(0, vo.getWindowSeconds());
        assertEquals(1, vo.getConsecutiveSamples());
        assertEquals("30m", vo.getReminderInterval());
        assertEquals(List.of("email", "dingtalk"), vo.getChannels());
        assertNull(vo.getDuration());
    }
}
