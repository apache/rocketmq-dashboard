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

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateAclUserDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();


    @Test
    void idShouldBeRequired() {
        UpdateAclUserDTO request = new UpdateAclUserDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("id is required");
    }

    @Test
    void toAclUserVoShouldParseNumericIdsAndDefaultAdmin() {
        UpdateAclUserDTO request = new UpdateAclUserDTO();
        request.setId(" 7 ");
        request.setUsername("user-admin");
        request.setClusters(java.util.List.of("rmq-prod"));

        AclUserVO user = request.toAclUserVO();

        assertThat(user.getId()).isEqualTo(7L);
        assertThat(user.getUsername()).isEqualTo("user-admin");
        assertThat(user.getClusters()).containsExactly("rmq-prod");
        assertThat(user.isAdmin()).isFalse();

        UpdateAclUserDTO nonNumeric = new UpdateAclUserDTO();
        nonNumeric.setId("tenant-role");

        assertThat(nonNumeric.toAclUserVO().getId()).isNull();
    }

    @Test
    void toAclUserVoShouldCarryTencentPermissions() {
        UpdateAclUserDTO request = new UpdateAclUserDTO();
        request.setId("7");
        request.setPermRead(true);
        request.setPermWrite(true);

        AclUserVO user = request.toAclUserVO();

        assertThat(user.getPermRead()).isTrue();
        assertThat(user.getPermWrite()).isTrue();
    }
}
