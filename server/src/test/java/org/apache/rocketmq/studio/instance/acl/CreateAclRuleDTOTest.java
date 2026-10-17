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
 * Pins the create contract of {@link CreateAclRuleDTO}: the principal and the resource are
 * required, and {@code toAclRuleVO()} carries every rule field (a created rule has no id yet).
 */
class CreateAclRuleDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(CreateAclRuleDTO request) {
        Set<ConstraintViolation<CreateAclRuleDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    private CreateAclRuleDTO validRequest() {
        CreateAclRuleDTO request = new CreateAclRuleDTO();
        request.setPrincipal("account-1");
        request.setResource("topic-1");
        return request;
    }

    @Test
    void thePrincipalAndTheResourceAreRequired() {
        CreateAclRuleDTO noPrincipal = validRequest();
        noPrincipal.setPrincipal(" ");
        assertThat(violationsOf(noPrincipal)).containsExactly("principal is required");

        CreateAclRuleDTO noResource = validRequest();
        noResource.setResource("");
        assertThat(violationsOf(noResource)).containsExactly("resource is required");

        assertThat(violationsOf(validRequest())).isEmpty();
    }

    @Test
    void toAclRuleVOCarriesEveryRuleField() {
        CreateAclRuleDTO request = validRequest();
        request.setResourceType("TOPIC");
        request.setResourcePattern("LITERAL");
        request.setActions(List.of("PUB", "SUB"));
        request.setDecision("ALLOW");
        request.setScope("instance");
        request.setAclVersion("v2");

        AclRuleVO rule = request.toAclRuleVO();

        assertThat(rule.getId()).isNull();
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
    void aMinimalCreateStillProducesARule() {
        AclRuleVO rule = validRequest().toAclRuleVO();
        assertThat(rule.getPrincipal()).isEqualTo("account-1");
        assertThat(rule.getResource()).isEqualTo("topic-1");
        assertThat(rule.getActions()).isNull();
    }
}
