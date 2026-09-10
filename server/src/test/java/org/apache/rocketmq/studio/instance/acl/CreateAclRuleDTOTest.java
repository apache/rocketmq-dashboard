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

class CreateAclRuleDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void principalAndResourceShouldBeRequired() {
        CreateAclRuleDTO request = new CreateAclRuleDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("principal is required", "resource is required");
    }

    @Test
    void toAclRuleVoShouldCarryTheRuleConfiguration() {
        CreateAclRuleDTO request = new CreateAclRuleDTO();
        request.setPrincipal("user-order-service");
        request.setResource("order-*");
        request.setResourceType("Topic");
        request.setResourcePattern("PREFIX");
        request.setActions(List.of("PUB", "SUB"));
        request.setDecision("ALLOW");
        request.setScope("cluster");
        request.setAclVersion("2.0");

        AclRuleVO vo = request.toAclRuleVO();

        assertThat(vo.getPrincipal()).isEqualTo("user-order-service");
        assertThat(vo.getResource()).isEqualTo("order-*");
        assertThat(vo.getResourceType()).isEqualTo("Topic");
        assertThat(vo.getResourcePattern()).isEqualTo("PREFIX");
        assertThat(vo.getActions()).containsExactly("PUB", "SUB");
        assertThat(vo.getDecision()).isEqualTo("ALLOW");
        assertThat(vo.getScope()).isEqualTo("cluster");
        assertThat(vo.getAclVersion()).isEqualTo("2.0");
    }

    @Test
    void toAclRuleVoShouldCarryOptionalFields() {
        CreateAclRuleDTO request = new CreateAclRuleDTO();
        request.setPrincipal("user-monitor");
        request.setResource("system-log");
        request.setResourceType("Topic");
        request.setResourcePattern("LITERAL");
        request.setDecision("ALLOW");
        request.setScope("namespace");
        request.setAclVersion("1.0");
        request.setInstanceId("instance-a");

        AclRuleVO vo = request.toAclRuleVO();

        assertThat(vo.getResourceType()).isEqualTo("Topic");
        assertThat(vo.getScope()).isEqualTo("namespace");
        assertThat(vo.getAclVersion()).isEqualTo("1.0");
    }
}
