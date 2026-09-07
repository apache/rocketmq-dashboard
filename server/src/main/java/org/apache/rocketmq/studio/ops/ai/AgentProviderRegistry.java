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

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Selects the CLI agent provider (claude code / qoder) for the configured engine.
 */
@Component
public class AgentProviderRegistry {

    private final Map<String, AgentProvider> providers;

    public AgentProviderRegistry(List<AgentProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(AgentProvider::engine, Function.identity()));
    }

    public AgentProvider forEngine(String engine) {
        AgentProvider provider = providers.get(engine == null ? "" : engine.trim().toLowerCase(Locale.ROOT));
        if (provider == null) {
            throw new LlmGatewayException(400, "llm.config.unsupported_engine",
                    "Agent engine is not supported: " + engine,
                    "Use one of: claude-code, qoder.");
        }
        return provider;
    }
}
