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
package com.rocketmq.studio.ops.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class McpServerImpl implements McpServerRegistry {

    @Override
    public List<AiToolVO> listTools() {
        log.debug("Listing available MCP tools (stub)");

        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("type", "object");
        queryParams.put("properties", Collections.singletonMap("query",
                Collections.singletonMap("type", "string")));

        Map<String, Object> brokerParams = new HashMap<>();
        brokerParams.put("type", "object");
        brokerParams.put("properties", Collections.singletonMap("brokerName",
                Collections.singletonMap("type", "string")));

        return Arrays.asList(
                AiToolVO.builder()
                        .name("query_metrics")
                        .description("Query RocketMQ metrics from Prometheus")
                        .parameters(queryParams)
                        .build(),
                AiToolVO.builder()
                        .name("list_brokers")
                        .description("List all RocketMQ brokers in the cluster")
                        .parameters(Collections.emptyMap())
                        .build(),
                AiToolVO.builder()
                        .name("diagnose_broker")
                        .description("Diagnose issues with a specific broker")
                        .parameters(brokerParams)
                        .build()
        );
    }
}
