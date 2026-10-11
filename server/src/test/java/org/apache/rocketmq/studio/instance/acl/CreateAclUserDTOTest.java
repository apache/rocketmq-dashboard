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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the create contract of {@link CreateAclUserDTO}: the username is required, and
 * {@code toAclUserVO()} carries the fields of the registration form (a created user has no id
 * yet; the instance id routes to a cloud backend and stays out of the view).
 */
class CreateAclUserDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(CreateAclUserDTO request) {
        Set<ConstraintViolation<CreateAclUserDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void theUsernameIsRequired() {
        CreateAclUserDTO missing = new CreateAclUserDTO();
        assertThat(violationsOf(missing)).containsExactly("username is required");

        CreateAclUserDTO blank = new CreateAclUserDTO();
        blank.setUsername("   ");
        assertThat(violationsOf(blank)).containsExactly("username is required");
    }

    @Test
    void aMinimalCreateIsValid() {
        CreateAclUserDTO request = new CreateAclUserDTO();
        request.setUsername("alice");
        assertThat(violationsOf(request)).isEmpty();
    }

    @Test
    void toAclUserVOCarriesTheFormFields() {
        CreateAclUserDTO request = new CreateAclUserDTO();
        request.setUsername("alice");
        request.setAdmin(true);
        request.setClusters(List.of("cluster-1"));
        request.setPermRead(false);
        request.setPermWrite(true);
        request.setInstanceId("instance-1");

        AclUserVO user = request.toAclUserVO();

        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.isAdmin()).isTrue();
        assertThat(user.getClusters()).containsExactly("cluster-1");
        assertThat(user.getPermRead()).isFalse();
        assertThat(user.getPermWrite()).isTrue();
        // a created user has no id, and the routing instance never leaks into the view
        assertThat(user.getId()).isNull();
    }
}
