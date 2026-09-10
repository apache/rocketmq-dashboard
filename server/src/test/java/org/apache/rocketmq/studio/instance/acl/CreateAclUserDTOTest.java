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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateAclUserDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private CreateAclUserDTO validPayload() {
        CreateAclUserDTO dto = new CreateAclUserDTO();
        dto.setUsername("admin-user");
        dto.setAdmin(true);
        dto.setClusters(List.of("cluster-a"));
        dto.setPermRead(true);
        dto.setPermWrite(false);
        dto.setInstanceId("inst-1");
        return dto;
    }

    @Test
    void acceptsCompletePayload() {
        Set<ConstraintViolation<CreateAclUserDTO>> violations = validator.validate(validPayload());
        assertTrue(violations.isEmpty());
    }

    @Test
    void rejectsMissingUsername() {
        CreateAclUserDTO dto = validPayload();
        dto.setUsername(null);

        Set<ConstraintViolation<CreateAclUserDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("username is required", violations.iterator().next().getMessage());
    }

    @Test
    void rejectsBlankUsername() {
        CreateAclUserDTO dto = validPayload();
        dto.setUsername("   ");

        Set<ConstraintViolation<CreateAclUserDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("username is required", violations.iterator().next().getMessage());
    }

    @Test
    void optionalFieldsRemainOptional() {
        CreateAclUserDTO dto = new CreateAclUserDTO();
        dto.setUsername("reader");

        Set<ConstraintViolation<CreateAclUserDTO>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty());
    }

    @Test
    void mapsToAclUserVo() {
        CreateAclUserDTO dto = validPayload();

        AclUserVO vo = dto.toAclUserVO();

        assertEquals("admin-user", vo.getUsername());
        assertTrue(vo.isAdmin());
        assertEquals(List.of("cluster-a"), vo.getClusters());
        assertEquals(Boolean.TRUE, vo.getPermRead());
        assertEquals(Boolean.FALSE, vo.getPermWrite());
        assertNull(vo.getId());
        assertNull(vo.getGmtCreate());
    }
}
