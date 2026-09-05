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

class UpdateAclUserDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private UpdateAclUserDTO validPayload() {
        UpdateAclUserDTO dto = new UpdateAclUserDTO();
        dto.setId("42");
        dto.setUsername("ops-user");
        dto.setAdmin(true);
        dto.setClusters(List.of("cluster-b"));
        dto.setPermRead(true);
        dto.setPermWrite(true);
        dto.setInstanceId("inst-2");
        return dto;
    }

    @Test
    void acceptsCompletePayload() {
        Set<ConstraintViolation<UpdateAclUserDTO>> violations = validator.validate(validPayload());
        assertTrue(violations.isEmpty());
    }

    @Test
    void rejectsMissingId() {
        UpdateAclUserDTO dto = validPayload();
        dto.setId(null);

        Set<ConstraintViolation<UpdateAclUserDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("id is required", violations.iterator().next().getMessage());
    }

    @Test
    void mapsNumericIdToLong() {
        AclUserVO vo = validPayload().toAclUserVO();

        assertEquals(42L, vo.getId());
        assertEquals("ops-user", vo.getUsername());
        assertTrue(vo.isAdmin());
        assertEquals(List.of("cluster-b"), vo.getClusters());
        assertEquals(Boolean.TRUE, vo.getPermRead());
        assertEquals(Boolean.TRUE, vo.getPermWrite());
    }

    @Test
    void mapsNonNumericIdToNull() {
        UpdateAclUserDTO dto = validPayload();
        dto.setId("acl-abc");

        AclUserVO vo = dto.toAclUserVO();

        assertNull(vo.getId());
    }

    @Test
    void treatsNullAdminAsNotAdmin() {
        UpdateAclUserDTO dto = validPayload();
        dto.setAdmin(null);

        AclUserVO vo = dto.toAclUserVO();

        assertFalse(vo.isAdmin());
    }
}
