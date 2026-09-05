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
    void emptyRequestReportsPrincipalAndResourceTest() {
        CreateAclRuleDTO request = new CreateAclRuleDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("principal is required", "resource is required");
    }

    @Test
    void completeRequestPassesValidationTest() {
        CreateAclRuleDTO request = new CreateAclRuleDTO();
        request.setPrincipal("admin");
        request.setResource("TopicA");
        request.setActions(List.of("PUB", "SUB"));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void toAclRuleVOShouldCarryTheRuleFieldsTest() {
        CreateAclRuleDTO request = new CreateAclRuleDTO();
        request.setPrincipal("admin");
        request.setResource("TopicA");
        request.setResourceType("TOPIC");
        request.setResourcePattern("LITERAL");
        request.setActions(List.of("PUB"));
        request.setDecision("ALLOW");
        request.setScope("cluster-a");
        request.setAclVersion("1.0");
        request.setInstanceId("instance-1");

        AclRuleVO vo = request.toAclRuleVO();

        assertThat(vo.getPrincipal()).isEqualTo("admin");
        assertThat(vo.getResource()).isEqualTo("TopicA");
        assertThat(vo.getResourceType()).isEqualTo("TOPIC");
        assertThat(vo.getResourcePattern()).isEqualTo("LITERAL");
        assertThat(vo.getActions()).containsExactly("PUB");
        assertThat(vo.getDecision()).isEqualTo("ALLOW");
        assertThat(vo.getScope()).isEqualTo("cluster-a");
        assertThat(vo.getAclVersion()).isEqualTo("1.0");
    }
}
