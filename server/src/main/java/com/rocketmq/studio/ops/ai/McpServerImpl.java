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

import com.rocketmq.studio.ops.ai.tool.ToolCatalog;
import com.rocketmq.studio.ops.ai.tool.ToolGatewayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class McpServerImpl implements McpServerRegistry {

    private final ToolGatewayService toolGatewayService;
    private final ToolCatalog toolCatalog;

    @Override
    public List<AiToolVO> listTools() {
        return toolGatewayService.discover(null);
    }

    @Override
    public List<AiToolVO> listTools(String clusterId) {
        return toolGatewayService.discover(clusterId);
    }

    @Override
    public Object execute(String name, Map<String, Object> input) {
        return toolGatewayService.execute(name, input);
    }

    @Override
    public String catalogVersion() {
        return toolCatalog.getVersion();
    }

    @Override
    public String catalogDigest() {
        return toolCatalog.getDigest();
    }

    @Override
    public String minimumClientVersion() {
        return toolCatalog.getMinimumClientVersion();
    }
}
