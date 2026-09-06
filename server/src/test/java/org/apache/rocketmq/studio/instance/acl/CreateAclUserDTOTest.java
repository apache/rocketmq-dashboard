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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreateAclUserDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void usernameShouldBeRequired() {
        CreateAclUserDTO request = new CreateAclUserDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("username is required");
    }

    @Test
    void toAclUserVoShouldCarryAccountFields() {
        CreateAclUserDTO request = new CreateAclUserDTO();
        request.setUsername("user-order-service");
        request.setAdmin(true);
        request.setClusters(List.of("rmq-prod-01", "rmq-prod-02"));

        AclUserVO user = request.toAclUserVO();

        assertThat(user.getUsername()).isEqualTo("user-order-service");
        assertThat(user.isAdmin()).isTrue();
        assertThat(user.getClusters()).containsExactly("rmq-prod-01", "rmq-prod-02");
    }

    @Test
    void toAclUserVoShouldCarryTencentRolePermissions() {
        CreateAclUserDTO request = new CreateAclUserDTO();
        request.setUsername("role-reader");
        request.setAdmin(false);
        request.setPermRead(true);
        request.setPermWrite(false);

        AclUserVO user = request.toAclUserVO();

        assertThat(user.getUsername()).isEqualTo("role-reader");
        assertThat(user.getPermRead()).isTrue();
        assertThat(user.getPermWrite()).isFalse();
        assertThat(user.isAdmin()).isFalse();
    }
}
