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
 * Pins the update contract of {@link UpdateAclUserDTO}: the id is required, a numeric id
 * round-trips as a Long (trimmed; non-numeric or blank maps to null), and {@code toAclUserVO()}
 * carries every field with the nullable admin and permission flags passed through unchanged.
 */
class UpdateAclUserDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(UpdateAclUserDTO request) {
        Set<ConstraintViolation<UpdateAclUserDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    private UpdateAclUserDTO validRequest() {
        UpdateAclUserDTO request = new UpdateAclUserDTO();
        request.setId("42");
        return request;
    }

    @Test
    void theIdIsRequired() {
        UpdateAclUserDTO blank = validRequest();
        blank.setId("   ");
        assertThat(violationsOf(blank)).containsExactly("id is required");

        UpdateAclUserDTO missing = validRequest();
        missing.setId(null);
        assertThat(violationsOf(missing)).containsExactly("id is required");
    }

    @Test
    void aNumericIdRoundTripsAsALongAndAnythingElseMapsToNull() {
        UpdateAclUserDTO request = validRequest();
        request.setId("42");
        assertThat(request.toAclUserVO().getId()).isEqualTo(42L);

        request.setId(" 42 ");
        assertThat(request.toAclUserVO().getId()).isEqualTo(42L);

        request.setId("not-a-number");
        assertThat(request.toAclUserVO().getId()).isNull();
    }

    @Test
    void toAclUserVOCarriesEveryField() {
        UpdateAclUserDTO request = validRequest();
        request.setUsername("alice");
        request.setAdmin(Boolean.TRUE);
        request.setClusters(List.of("cluster-1", "cluster-2"));
        request.setPermRead(Boolean.FALSE);
        request.setPermWrite(Boolean.TRUE);

        AclUserVO user = request.toAclUserVO();

        assertThat(user.getId()).isEqualTo(42L);
        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.isAdmin()).isTrue();
        assertThat(user.getClusters()).containsExactly("cluster-1", "cluster-2");
        assertThat(user.getPermRead()).isFalse();
        assertThat(user.getPermWrite()).isTrue();
    }

    @Test
    void theNullablePermissionFlagsPassThroughUnchanged() {
        UpdateAclUserDTO request = validRequest();
        AclUserVO user = request.toAclUserVO();
        assertThat(user.getPermRead()).isNull();
        assertThat(user.getPermWrite()).isNull();
        // an admin flag that is not part of the partial update reads as false in the view;
        // preserving the stored value is the caller's job before mapping
        assertThat(user.isAdmin()).isFalse();
    }
}
