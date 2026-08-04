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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CliAgentProviderTest {

    @Test
    void completeShouldDrainStderrWhileProcessIsRunning() {
        CliAgentProvider provider = new TestCliAgentProvider(
                List.of("sh", "-c", "yes error | head -c 131072 >&2; printf done"), 5);

        assertThat(provider.complete(LlmConfigVO.builder().build(), "", null)).isEqualTo("done");
    }

    @Test
    void completeShouldTimeOutBeforeWaitingForOutputReaders() {
        CliAgentProvider provider = new TestCliAgentProvider(List.of("sh", "-c", "sleep 2"), 1);

        assertThatThrownBy(() -> provider.complete(LlmConfigVO.builder().build(), "", null))
                .isInstanceOf(LlmGatewayException.class)
                .satisfies(exception -> assertThat(((LlmGatewayException) exception).getStatusCode()).isEqualTo(504));
    }

    private static class TestCliAgentProvider extends CliAgentProvider {

        private final List<String> command;
        private final long timeoutSeconds;

        private TestCliAgentProvider(List<String> command, long timeoutSeconds) {
            this.command = command;
            this.timeoutSeconds = timeoutSeconds;
        }

        @Override
        public String engine() {
            return "test";
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        protected List<String> buildCommand(LlmConfigVO config, String prompt, String modelOverride) {
            return command;
        }

        @Override
        protected Map<String, String> childEnv(LlmConfigVO config) {
            return Map.of();
        }

        @Override
        protected String binaryName() {
            return "test";
        }

        @Override
        protected long timeoutSeconds() {
            return timeoutSeconds;
        }
    }
}
