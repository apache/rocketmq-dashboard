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
package org.apache.rocketmq.studio.instance;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateInstanceDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private UpdateInstanceDTO sample() {
        UpdateInstanceDTO dto = new UpdateInstanceDTO();
        dto.setInstanceId("inst-1");
        dto.setName("qa-rocketmq");
        dto.setType(InstanceType.DIRECT);
        dto.setEndpoint("10.0.0.2:9876");
        dto.setRemark("qa cluster");
        dto.setAdminCredentialRef("cred-qa");
        return dto;
    }

    @Test
    void acceptsCompletePayload() {
        assertTrue(validator.validate(sample()).isEmpty());
    }

    @Test
    void rejectsMissingInstanceId() {
        UpdateInstanceDTO dto = sample();
        dto.setInstanceId(null);

        Set<ConstraintViolation<UpdateInstanceDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("instanceId is required", violations.iterator().next().getMessage());
    }

    @Test
    void rejectsBlankInstanceId() {
        UpdateInstanceDTO dto = sample();
        dto.setInstanceId(" ");

        Set<ConstraintViolation<UpdateInstanceDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("instanceId is required", violations.iterator().next().getMessage());
    }

    @Test
    void mapsEditableFieldsToVo() {
        InstanceVO vo = sample().toInstanceVO();

        assertEquals("qa-rocketmq", vo.getName());
        assertEquals(InstanceType.DIRECT, vo.getType());
        assertEquals("10.0.0.2:9876", vo.getEndpoint());
        assertEquals("qa cluster", vo.getRemark());
        assertEquals("cred-qa", vo.getAdminCredentialRef());
    }

    @Test
    void leavesNonEditableFieldsOutOfVo() {
        InstanceVO vo = sample().toInstanceVO();

        assertNull(vo.getCloudInstanceId());
        assertNull(vo.getVendor());
        assertNull(vo.getCredentialId());
        assertNull(vo.getRegionId());
        assertEquals(0, vo.getTopicCount());
    }
}
