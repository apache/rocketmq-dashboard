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
package org.apache.rocketmq.studio.ops.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QoderAgentProviderTest {

    private final QoderAgentProvider provider = new QoderAgentProvider(new CliProcessEnvironment(List.of()));

    @Test
    void buildCommandUsesConfiguredModelWhenRequestDoesNotOverrideIt() {
        LlmConfigVO config = LlmConfigVO.builder().model(" qoder-configured ").build();

        assertThat(provider.buildCommand(config, "prompt", null))
                .containsExactly("qodercli", "-p", "prompt", "-m", "qoder-configured");
    }

    @Test
    void buildCommandPrefersRequestModelOverride() {
        LlmConfigVO config = LlmConfigVO.builder().model("qoder-configured").build();

        assertThat(provider.buildCommand(config, "prompt", " qoder-request "))
                .containsExactly("qodercli", "-p", "prompt", "-m", "qoder-request");
    }

    @Test
    void buildCommandOmitsBlankModel() {
        LlmConfigVO config = LlmConfigVO.builder().model(" ").build();

        assertThat(provider.buildCommand(config, null, null))
                .containsExactly("qodercli", "-p", "");
    }
}
