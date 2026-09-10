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
package org.apache.rocketmq.studio.cluster.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateConfigDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private UpdateConfigDTO validPayload() {
        return UpdateConfigDTO.builder()
            .id("cluster-1")
            .instanceId("inst-1")
            .flushDiskType("ASYNC_FLUSH")
            .autoCreateTopicEnable(true)
            .autoCreateSubscriptionGroup(false)
            .maxMessageSize(4 * 1024 * 1024)
            .fileReservedTime(72)
            .writeQueueNums(8)
            .readQueueNums(8)
            .brokerPermission(6)
            .build();
    }

    private Set<String> messagesOf(UpdateConfigDTO dto) {
        return validator.validate(dto).stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.toSet());
    }

    @Test
    void acceptsCompletePayload() {
        assertTrue(validator.validate(validPayload()).isEmpty());
    }

    @Test
    void rejectsMissingId() {
        UpdateConfigDTO dto = validPayload();
        dto.setId(null);

        Set<String> messages = messagesOf(dto);

        assertEquals(1, messages.size());
        assertTrue(messages.contains("id is required"));
    }

    @Test
    void rejectsBlankId() {
        UpdateConfigDTO dto = validPayload();
        dto.setId("  ");

        Set<String> messages = messagesOf(dto);

        assertTrue(messages.contains("id is required"));
    }

    @Test
    void rejectsOutOfRangeMaxMessageSize() {
        UpdateConfigDTO dto = validPayload();
        dto.setMaxMessageSize(16 * 1024 * 1024 * 16);

        Set<String> messages = messagesOf(dto);

        assertTrue(messages.contains("maxMessageSize must be between 1048576 and 134217728"));
    }

    @Test
    void acceptsBoundaryMaxMessageSize() {
        UpdateConfigDTO dto = validPayload();
        dto.setMaxMessageSize(1_048_576);

        assertTrue(validator.validate(dto).isEmpty());

        dto.setMaxMessageSize(134_217_728);
        assertTrue(validator.validate(dto).isEmpty());
    }

    @Test
    void rejectsOutOfRangeFileReservedTime() {
        UpdateConfigDTO dto = validPayload();
        dto.setFileReservedTime(721);

        Set<String> messages = messagesOf(dto);

        assertTrue(messages.contains("fileReservedTime must be between 1 and 720"));
    }

    @Test
    void rejectsZeroFileReservedTime() {
        UpdateConfigDTO dto = validPayload();
        dto.setFileReservedTime(0);

        Set<String> messages = messagesOf(dto);

        assertTrue(messages.contains("fileReservedTime must be between 1 and 720"));
    }

    @Test
    void rejectsOutOfRangeQueueCounts() {
        UpdateConfigDTO dto = validPayload();
        dto.setWriteQueueNums(300);
        dto.setReadQueueNums(0);

        Set<String> messages = messagesOf(dto);

        assertTrue(messages.contains("writeQueueNums must be between 1 and 256"));
        assertTrue(messages.contains("readQueueNums must be between 1 and 256"));
    }

    @Test
    void rejectsOutOfRangeBrokerPermission() {
        UpdateConfigDTO dto = validPayload();
        dto.setBrokerPermission(8);

        Set<String> messages = messagesOf(dto);

        assertTrue(messages.contains("brokerPermission must be between 0 and 7"));
    }

    @Test
    void optionalNumericFieldsMayBeAbsent() {
        UpdateConfigDTO dto = UpdateConfigDTO.builder()
            .id("cluster-2")
            .flushDiskType("SYNC_FLUSH")
            .build();

        assertTrue(validator.validate(dto).isEmpty());
    }
}
