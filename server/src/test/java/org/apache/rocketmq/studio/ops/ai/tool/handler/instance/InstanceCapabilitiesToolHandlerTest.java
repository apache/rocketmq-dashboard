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

import org.apache.rocketmq.studio.ops.ai.tool.catalog.CapabilityResolver;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.InstanceInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.instance.InstanceCapabilitiesOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolSchemaValidator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class InstanceCapabilitiesToolHandlerTest {

    private final ToolCatalog catalog = new ToolCatalog(new DefaultResourceLoader());
    private final ToolSchemaValidator validator = new ToolSchemaValidator(
            catalog,
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),
            new JsonMapper());

    @Test
    void projectsSortedCapabilitiesFromTheBoundInstanceTest() {
        CapabilityResolver capabilityResolver = mock(CapabilityResolver.class);
        when(capabilityResolver.resolve("instance-a"))
                .thenReturn(new LinkedHashSet<>(List.of("TOPIC_MANAGEMENT", "REMOTING")));
        InstanceCapabilitiesToolHandler handler =
                new InstanceCapabilitiesToolHandler(capabilityResolver);

        assertThat(handler.name()).isEqualTo("rmq.instance.capabilities");
        InstanceCapabilitiesOutput result = handler.execute(
                new InstanceInput("instance-a"),
                ToolExecutionContext.of("instance-a", null, Map.of("instanceId", "instance-a")));

        assertThat(result.instanceId()).isEqualTo("instance-a");
        assertThat(result.capabilities()).containsExactly("REMOTING", "TOPIC_MANAGEMENT");
        assertThatCode(() -> validator.validateOutput(
                catalog.getDefinition("rmq.instance.capabilities"), result)).doesNotThrowAnyException();
        verify(capabilityResolver).resolve("instance-a");
        verifyNoMoreInteractions(capabilityResolver);
    }
}
