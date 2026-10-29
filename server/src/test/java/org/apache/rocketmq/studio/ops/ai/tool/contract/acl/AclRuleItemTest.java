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
package org.apache.rocketmq.studio.ops.ai.tool.contract.acl;

import org.apache.rocketmq.studio.instance.acl.AclRuleVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link AclRuleItem}: the tool-side listing projection of an ACL rule. The id and timestamp
 * translations (Long/LocalDateTime to the tool world's strings) must be exact in both directions,
 * and a rule saved before the aclVersion/gmtCreate columns existed must list with nulls, not crash.
 */
class AclRuleItemTest {

    @Test
    void mapsEveryListingField() {
        AclRuleVO rule = AclRuleVO.builder()
                .id(7L).principal("User:alice").resource("Topic:orders")
                .resourceType("Topic").resourcePattern("LITERAL")
                .actions(List.of("PUB", "SUB")).decision("ALLOW").scope("DEFAULT")
                .aclVersion("v3").gmtCreate(LocalDateTime.of(2026, 10, 9, 12, 0))
                .build();

        AclRuleItem item = AclRuleItem.from(rule);

        assertThat(item.id()).isEqualTo("7");
        assertThat(item.principal()).isEqualTo("User:alice");
        assertThat(item.resource()).isEqualTo("Topic:orders");
        assertThat(item.resourceType()).isEqualTo("Topic");
        assertThat(item.resourcePattern()).isEqualTo("LITERAL");
        assertThat(item.actions()).containsExactly("PUB", "SUB");
        assertThat(item.decision()).isEqualTo("ALLOW");
        assertThat(item.scope()).isEqualTo("DEFAULT");
        assertThat(item.aclVersion()).isEqualTo("v3");
        assertThat(item.gmtCreate()).isEqualTo("2026-10-09T12:00");
    }

    @Test
    void aRuleWithoutOptionalColumnsListsWithNulls() {
        AclRuleVO rule = AclRuleVO.builder().principal("User:bob").build();

        AclRuleItem item = AclRuleItem.from(rule);

        assertThat(item.id()).isNull();
        assertThat(item.aclVersion()).isNull();
        assertThat(item.gmtCreate()).isNull();
        assertThat(item.principal()).isEqualTo("User:bob");
    }
}
