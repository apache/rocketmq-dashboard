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
package org.apache.rocketmq.studio.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Acl2PolicyContextTest {

    @ParameterizedTest
    @ValueSource(strings = {"USER", "user", " Group ", "topic", "SERVICE_ACCOUNT", "service_account", "*"})
    void validateShouldAcceptTheServiceBoundTypeVocabularyCaseInsensitively(String boundType) {
        Acl2PolicyContext policy = validPolicy();
        policy.setBoundType(boundType);

        assertThatCode(policy::validate).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOPICS", "ACCOUNT", " ", ""})
    void validateShouldRejectBoundTypesOutsideTheServiceVocabulary(String boundType) {
        Acl2PolicyContext policy = validPolicy();
        policy.setBoundType(boundType);

        assertThatThrownBy(policy::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("boundType must be one of TOPIC, GROUP, *, USER, SERVICE_ACCOUNT");
    }

    @Test
    void validateShouldRequireABoundTypeLikeThePolicyService() {
        Acl2PolicyContext policy = validPolicy();
        policy.setBoundType(null);

        assertThatThrownBy(policy::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("boundType must be one of TOPIC, GROUP, *, USER, SERVICE_ACCOUNT");
    }

    private static Acl2PolicyContext validPolicy() {
        Acl2PolicyContext policy = new Acl2PolicyContext();
        policy.setAccessKey("svc-a");
        policy.setPolicyName("orders-policy");
        policy.setBoundType("USER");
        Acl2PolicyContext.AuthorizationRule rule =
                Acl2PolicyContext.AuthorizationRule.defaultAllowRule("orders-*");
        policy.setRules(List.of(rule));
        return policy;
    }
}
