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

class UpdateAclRuleDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void emptyRequestReportsRequiredFieldsTest() {
        UpdateAclRuleDTO request = new UpdateAclRuleDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("id is required", "principal is required", "resource is required");
    }

    @Test
    void numericIdIsParsedIntoTheVoTest() {
        UpdateAclRuleDTO request = new UpdateAclRuleDTO();
        request.setId(" 42 ");
        request.setPrincipal("admin");
        request.setResource("TopicA");
        request.setDecision("ALLOW");
        request.setAclVersion("1.0");

        AclRuleVO vo = request.toAclRuleVO();

        assertThat(vo.getId()).isEqualTo(42L);
        assertThat(vo.getPrincipal()).isEqualTo("admin");
        assertThat(vo.getDecision()).isEqualTo("ALLOW");
    }

    @Test
    void nonNumericIdLeavesTheVoIdNullTest() {
        UpdateAclRuleDTO request = new UpdateAclRuleDTO();
        request.setId("TopicA-acl");
        request.setPrincipal("admin");
        request.setResource("TopicA");

        assertThat(request.toAclRuleVO().getId()).isNull();
    }
}
