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

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the ACL rule view of {@link AclRuleVO}: one access rule - who (principal), over what
 * (resource with type and pattern), with which actions, and the verdict (decision) - round-trips
 * through the builder.
 */
class AclRuleVOTest {

    @Test
    void theRuleRoundTrips() {
        AclRuleVO rule = AclRuleVO.builder()
                .id(42L)
                .principal("account-1")
                .resource("order-*")
                .resourceType("TOPIC")
                .resourcePattern("LITERAL")
                .actions(List.of("PUB", "SUB"))
                .decision("ALLOW")
                .scope("instance")
                .aclVersion("v2")
                .gmtCreate(LocalDateTime.of(2026, 1, 2, 3, 4))
                .build();

        assertThat(rule.getId()).isEqualTo(42L);
        assertThat(rule.getPrincipal()).isEqualTo("account-1");
        assertThat(rule.getResource()).isEqualTo("order-*");
        assertThat(rule.getResourceType()).isEqualTo("TOPIC");
        assertThat(rule.getResourcePattern()).isEqualTo("LITERAL");
        assertThat(rule.getActions()).containsExactly("PUB", "SUB");
        assertThat(rule.getDecision()).isEqualTo("ALLOW");
        assertThat(rule.getScope()).isEqualTo("instance");
        assertThat(rule.getAclVersion()).isEqualTo("v2");
        assertThat(rule.getGmtCreate()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
    }

    @Test
    void aFreshRuleCarriesNoActions() {
        AclRuleVO rule = new AclRuleVO();
        assertThat(rule.getActions()).isNull();
        assertThat(rule.getId()).isNull();
    }
}
