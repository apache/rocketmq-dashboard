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
package org.apache.rocketmq.studio.ops.ai.tool.handler.instance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceService;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.instance.InstanceListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.instance.InstanceListItem;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolSchemaValidator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class InstanceListToolHandlerTest {

    private final ToolCatalog catalog = new ToolCatalog(new DefaultResourceLoader());
    private final ToolSchemaValidator validator = new ToolSchemaValidator(
            catalog, new ObjectMapper().findAndRegisterModules(), new JsonMapper());

    @Test
    void listsFilteredInstancesWithASecretFreeOperationalProjectionTest() throws Exception {
        InstanceService instanceService = mock(InstanceService.class);
        InstanceVO apache = InstanceVO.builder()
                .name("instance-apache")
                .type(InstanceType.DIRECT)
                .vendor(InstanceVendor.APACHE)
                .regionId("cn-beijing")
                .regionName("Beijing (CN)")
                .topicCount(12)
                .consumerGroupCount(4)
                .resourceCountsAvailable(true)
                .credentialId(42L)
                .adminCredentialRef("secret-ref")
                .build();
        InstanceVO tencent = InstanceVO.builder()
                .name("instance-tencent")
                .type(InstanceType.CLOUD)
                .vendor(InstanceVendor.TENCENT)
                .regionId("ap-guangzhou")
                .topicCount(0)
                .consumerGroupCount(0)
                .resourceCountsAvailable(false)
                .credentialId(7L)
                .adminCredentialRef("cloud-secret-ref")
                .build();
        when(instanceService.listInstances(InstanceType.CLOUD, "prod"))
                .thenReturn(List.of(tencent));

        InstanceListToolHandler handler = new InstanceListToolHandler(instanceService);
        ListOutput<InstanceListItem> result = handler.execute(
                new InstanceListInput("cloud", "prod"),
                ToolExecutionContext.of(null, null, Map.of("type", "cloud", "search", "prod")));

        assertThat(handler.name()).isEqualTo("rmq.instance.list");
        assertThat(result.items()).containsExactly(new InstanceListItem(
                "instance-tencent", "TENCENT", "CLOUD", "ap-guangzhou", null,
                0, 0, false));
        assertThatCode(() -> validator.validateOutput(
                catalog.getDefinition("rmq.instance.list"), result)).doesNotThrowAnyException();
        String json = new ObjectMapper().writeValueAsString(result);
        assertThat(json).doesNotContain("credentialId", "adminCredentialRef", "cloud-secret-ref");
        verify(instanceService).listInstances(InstanceType.CLOUD, "prod");
        verifyNoMoreInteractions(instanceService);
        assertThat(apache.getCredentialId()).isEqualTo(42L);
    }
}
