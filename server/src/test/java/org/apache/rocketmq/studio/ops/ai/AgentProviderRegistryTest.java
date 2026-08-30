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
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentProviderRegistryTest {

    @Test
    void shouldResolveEngineIndependentlyOfDefaultLocale() {
        AgentProvider provider = mock(AgentProvider.class);
        when(provider.engine()).thenReturn("cli");
        AgentProviderRegistry registry = new AgentProviderRegistry(List.of(provider));
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertThat(registry.forEngine(" CLI ")).isSameAs(provider);
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void shouldNormalizeProviderEngineDuringRegistration() {
        AgentProvider provider = mock(AgentProvider.class);
        when(provider.engine()).thenReturn(" CLI ");

        AgentProviderRegistry registry = new AgentProviderRegistry(List.of(provider));

        assertThat(registry.forEngine("cli")).isSameAs(provider);
    }

    @Test
    void shouldRejectDuplicateNormalizedProviderEngines() {
        AgentProvider first = mock(AgentProvider.class);
        AgentProvider second = mock(AgentProvider.class);
        when(first.engine()).thenReturn("CLI");
        when(second.engine()).thenReturn(" cli ");

        assertThatThrownBy(() -> new AgentProviderRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate agent provider engine: cli");
    }
}
