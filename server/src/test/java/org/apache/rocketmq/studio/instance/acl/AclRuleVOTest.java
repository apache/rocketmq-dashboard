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

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AclRuleVOTest {

    @Test
    void builderDefaultsDescribeEmptyRule() {
        AclRuleVO vo = AclRuleVO.builder().build();

        assertNull(vo.getId());
        assertNull(vo.getPrincipal());
        assertNull(vo.getResource());
        assertNull(vo.getResourceType());
        assertNull(vo.getActions());
        assertNull(vo.getDecision());
        assertNull(vo.getScope());
        assertNull(vo.getAclVersion());
        assertNull(vo.getGmtCreate());
    }

    @Test
    void allArgsCarryRuleState() {
        LocalDateTime created = LocalDateTime.parse("2026-09-01T08:00:00");

        AclRuleVO vo = AclRuleVO.builder()
            .id(1L)
            .principal("AK-1")
            .resource("order-*")
            .resourceType("TOPIC")
            .resourcePattern("order-*")
            .actions(List.of("PUB", "SUB"))
            .decision("Allow")
            .scope("DefaultCluster")
            .aclVersion("2.0")
            .gmtCreate(created)
            .build();

        assertEquals(1L, vo.getId());
        assertEquals("AK-1", vo.getPrincipal());
        assertEquals(List.of("PUB", "SUB"), vo.getActions());
        assertEquals("Allow", vo.getDecision());
        assertEquals("2.0", vo.getAclVersion());
        assertEquals(created, vo.getGmtCreate());
    }
}
