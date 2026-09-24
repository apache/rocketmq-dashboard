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

import org.apache.rocketmq.studio.common.config.LegacyJackson2Config;
import org.apache.rocketmq.studio.instance.acl.AclRuleVO;
import org.apache.rocketmq.studio.instance.acl.AclUserVO;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolSchemaValidator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cloud ACL projections: Tencent Cloud roles are not database rows, so the provider leaves the
 * numeric id null and carries the role name in the username/principal (TencentAclService). The
 * published output schemas require a non-null {@code id} for rmq.acl.list, rmq.acl.get,
 * rmq.user.list and rmq.user.get, so the projections must fall back to that role name.
 */
class CloudAclIdentifierProjectionTest {

    private final ToolCatalog catalog = new ToolCatalog(new DefaultResourceLoader());
    private final ToolSchemaValidator validator = new ToolSchemaValidator(
            catalog,
            new LegacyJackson2Config().jackson2ObjectMapper(),
            JsonMapper.builder().build());

    @Test
    void aclRuleWithoutNumericIdKeepsTheSchemasRequiredIdTest() {
        AclRuleVO cloudRule = AclRuleVO.builder()
                .principal("role-orders")
                .resource("*")
                .resourceType("Cluster")
                .resourcePattern("LITERAL")
                .actions(List.of("PUB", "SUB"))
                .decision("ALLOW")
                .scope("ALL")
                .aclVersion("v2")
                .build();

        AclRuleItem item = AclRuleItem.from(cloudRule);

        validator.validateOutput(catalog.getDefinition("rmq.acl.get"), item);
        validator.validateOutput(catalog.getDefinition("rmq.acl.list"),
                new ListOutput<>(List.of(item)));
        assertThat(item.id()).isEqualTo("role-orders");
    }

    @Test
    void aclUserWithoutNumericIdKeepsTheSchemasRequiredIdTest() {
        AclUserVO cloudUser = AclUserVO.builder()
                .username("role-orders")
                .admin(false)
                .clusters(List.of("cloud-instance"))
                .build();

        AclUserItem item = AclUserItem.from(cloudUser);

        validator.validateOutput(catalog.getDefinition("rmq.user.get"), item);
        validator.validateOutput(catalog.getDefinition("rmq.user.list"),
                new ListOutput<>(List.of(item)));
        assertThat(item.id()).isEqualTo("role-orders");
    }

    @Test
    void persistedRuleKeepsItsNumericIdTest() {
        AclRuleVO stored = AclRuleVO.builder()
                .id(7L)
                .principal("alice")
                .resource("orders")
                .build();

        assertThat(AclRuleItem.from(stored).id()).isEqualTo("7");
        assertThat(AclUserItem.from(AclUserVO.builder().id(9L).username("alice").build()).id())
                .isEqualTo("9");
    }
}