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
 * Pins the update contract of {@link UpdateAclRuleDTO}: a numeric rule id round-trips as a Long
 * (trimmed, non-numeric or blank maps to null), every field maps into {@code toAclRuleVO()}, and
 * the identity fields are required.
 */
class UpdateAclRuleDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(UpdateAclRuleDTO request) {
        Set<ConstraintViolation<UpdateAclRuleDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    private UpdateAclRuleDTO validRequest() {
        UpdateAclRuleDTO request = new UpdateAclRuleDTO();
        request.setId("42");
        request.setPrincipal("account-1");
        request.setResource("topic-1");
        return request;
    }

    @Test
    void aNumericIdRoundTripsAsALong() {
        UpdateAclRuleDTO request = validRequest();
        request.setId("42");
        assertThat(request.toAclRuleVO().getId()).isEqualTo(42L);

        request.setId(" 42 ");
        assertThat(request.toAclRuleVO().getId()).isEqualTo(42L);
    }

    @Test
    void aNonNumericOrBlankIdMapsToNull() {
        UpdateAclRuleDTO request = validRequest();
        request.setId("not-a-number");
        assertThat(request.toAclRuleVO().getId()).isNull();

        request.setId("   ");
        assertThat(request.toAclRuleVO().getId()).isNull();

        request.setId(null);
        assertThat(request.toAclRuleVO().getId()).isNull();
    }

    @Test
    void toAclRuleVOCarriesEveryField() {
        UpdateAclRuleDTO request = validRequest();
        request.setResourceType("TOPIC");
        request.setResourcePattern("LITERAL");
        request.setActions(List.of("PUB", "SUB"));
        request.setDecision("ALLOW");
        request.setScope("instance");
        request.setAclVersion("v2");

        AclRuleVO rule = request.toAclRuleVO();

        assertThat(rule.getId()).isEqualTo(42L);
        assertThat(rule.getPrincipal()).isEqualTo("account-1");
        assertThat(rule.getResource()).isEqualTo("topic-1");
        assertThat(rule.getResourceType()).isEqualTo("TOPIC");
        assertThat(rule.getResourcePattern()).isEqualTo("LITERAL");
        assertThat(rule.getActions()).containsExactly("PUB", "SUB");
        assertThat(rule.getDecision()).isEqualTo("ALLOW");
        assertThat(rule.getScope()).isEqualTo("instance");
        assertThat(rule.getAclVersion()).isEqualTo("v2");
    }

    @Test
    void theIdentityFieldsAreRequired() {
        UpdateAclRuleDTO request = new UpdateAclRuleDTO();
        request.setPrincipal("account-1");
        request.setResource("topic-1");
        assertThat(violationsOf(request)).containsExactly("id is required");

        request = new UpdateAclRuleDTO();
        request.setId("42");
        request.setResource("topic-1");
        assertThat(violationsOf(request)).containsExactly("principal is required");

        request = new UpdateAclRuleDTO();
        request.setId("42");
        request.setPrincipal("account-1");
        assertThat(violationsOf(request)).containsExactly("resource is required");
    }
}
