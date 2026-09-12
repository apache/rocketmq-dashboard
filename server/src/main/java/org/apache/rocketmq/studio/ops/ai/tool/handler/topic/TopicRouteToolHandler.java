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
package org.apache.rocketmq.studio.ops.ai.tool.handler.topic;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.instance.topic.BrokerRouteVO;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicRouteInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicRouteItem;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TopicRouteToolHandler
        implements ToolHandler<TopicRouteInput, ListOutput<TopicRouteItem>> {

    private final MetadataService metadataService;

    @Override
    public String name() {
        return "rmq.topic.route";
    }

    @Override
    public Class<TopicRouteInput> inputType() {
        return TopicRouteInput.class;
    }

    @Override
    public ListOutput<TopicRouteItem> execute(
            TopicRouteInput input, ToolExecutionContext context) {
        List<BrokerRouteVO> routes =
                metadataService.getTopicRoutes(context.cluster(), input.topic());
        return new ListOutput<>(routes.stream()
                .map(TopicRouteItem::from)
                .toList());
    }
}
