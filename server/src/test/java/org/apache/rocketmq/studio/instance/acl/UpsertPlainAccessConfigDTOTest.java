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
package org.apache.rocketmq.studio.instance.acl;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpsertPlainAccessConfigDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private UpsertPlainAccessConfigDTO sample() {
        UpsertPlainAccessConfigDTO dto = new UpsertPlainAccessConfigDTO();
        dto.setAccessKey("  AK-1  ");
        dto.setSecretKey("sk-1");
        dto.setWhiteRemoteAddress("10.0.0.0/8");
        dto.setAdmin(true);
        dto.setDefaultTopicPerm("PUB");
        dto.setDefaultGroupPerm("SUB");
        dto.setTopicPerms(List.of("order-*=PUB"));
        dto.setGroupPerms(List.of("cg-order-*=SUB"));
        return dto;
    }

    @Test
    void acceptsCompletePayload() {
        Set<ConstraintViolation<UpsertPlainAccessConfigDTO>> violations = validator.validate(sample());
        assertTrue(violations.isEmpty());
    }

    @Test
    void rejectsMissingAccessKey() {
        UpsertPlainAccessConfigDTO dto = sample();
        dto.setAccessKey(null);

        Set<ConstraintViolation<UpsertPlainAccessConfigDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("accessKey is required", violations.iterator().next().getMessage());
    }

    @Test
    void rejectsBlankAccessKey() {
        UpsertPlainAccessConfigDTO dto = sample();
        dto.setAccessKey("   ");

        Set<ConstraintViolation<UpsertPlainAccessConfigDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("accessKey is required", violations.iterator().next().getMessage());
    }

    @Test
    void trimsAccessKeyWhenMappingToVo() {
        PlainAccessConfigVO vo = sample().toPlainAccessConfigVO();

        assertEquals("AK-1", vo.getAccessKey());
    }

    @Test
    void carriesAllPermissionFieldsToVo() {
        PlainAccessConfigVO vo = sample().toPlainAccessConfigVO();

        assertEquals("sk-1", vo.getSecretKey());
        assertEquals("10.0.0.0/8", vo.getWhiteRemoteAddress());
        assertTrue(vo.isAdmin());
        assertEquals("PUB", vo.getDefaultTopicPerm());
        assertEquals("SUB", vo.getDefaultGroupPerm());
        assertEquals(List.of("order-*=PUB"), vo.getTopicPerms());
        assertEquals(List.of("cg-order-*=SUB"), vo.getGroupPerms());
    }

    @Test
    void mapsBlankAccessKeyToNullVo() {
        UpsertPlainAccessConfigDTO dto = sample();
        dto.setAccessKey(null);

        PlainAccessConfigVO vo = dto.toPlainAccessConfigVO();

        assertNull(vo.getAccessKey());
    }

    @Test
    void secretKeyIsExcludedFromToString() {
        String rendered = sample().toString();

        assertFalse(rendered.contains("sk-1"));
        assertFalse(rendered.contains("AK-1"));
    }
}
