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
package org.apache.rocketmq.studio.ops.ai.tool.handler.proxy;

import org.apache.rocketmq.studio.cluster.proxy.ProxyAddressService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.proxy.ProxyConfigUpdateInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.proxy.ProxyConfigUpdateOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class ProxyConfigUpdateToolHandlerTest {

    @Mock
    private ProxyAddressService proxyAddressService;

    @InjectMocks
    private ProxyConfigUpdateToolHandler handler;

    @Test
    void applyShouldReloadConfig() {
        assertThat(handler.name()).isEqualTo("rmq.proxy.config_update");
        Object result = handler.execute(new ProxyConfigUpdateInput(
                "cluster-1", "127.0.0.1:8081"), context("instance-1"));

        assertThat(result).isEqualTo(new ProxyConfigUpdateOutput(true, "127.0.0.1:8081"));
        verify(proxyAddressService).reloadConfigForInstance("instance-1", "127.0.0.1:8081");
    }
}
