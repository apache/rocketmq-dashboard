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
package org.apache.rocketmq.studio.ops.ai.tool.handler.client;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.cluster.client.ClientConnectionVO;
import org.apache.rocketmq.studio.cluster.client.ClientService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.client.ClientItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.client.ClientListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ClientListToolHandler implements ToolHandler<ClientListInput, ListOutput<ClientItem>> {

    private final ClientService clientService;

    @Override
    public String name() {
        return "rmq.client.list";
    }

    @Override
    public Class<ClientListInput> inputType() {
        return ClientListInput.class;
    }

    @Override
    public ListOutput<ClientItem> execute(ClientListInput input, ToolExecutionContext context) {
        return new ListOutput<>(clientService
                .listConnections(context.instanceId(), input.clusterId(), input.type()).stream()
                .filter(connection -> matchesSearch(connection, input.search()))
                .map(ClientItem::from)
                .toList());
    }

    private static boolean matchesSearch(ClientConnectionVO connection, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String normalizedSearch = search.trim().toLowerCase(Locale.ROOT);
        return contains(connection.getClientId(), normalizedSearch)
                || contains(connection.getGroupOrTopic(), normalizedSearch)
                || contains(connection.getProducerGroup(), normalizedSearch)
                || contains(connection.getAddress(), normalizedSearch);
    }

    private static boolean contains(String value, String normalizedSearch) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedSearch);
    }
}
