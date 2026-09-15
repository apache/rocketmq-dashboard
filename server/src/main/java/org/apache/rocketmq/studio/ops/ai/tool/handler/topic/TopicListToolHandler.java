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
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicListItem;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TopicListToolHandler
        implements ToolHandler<TopicListInput, ListOutput<TopicListItem>> {

    private final MetadataService metadataService;

    @Override
    public String name() {
        return "rmq.topic.list";
    }

    @Override
    public Class<TopicListInput> inputType() {
        return TopicListInput.class;
    }

    @Override
    public ListOutput<TopicListItem> execute(
            TopicListInput input, ToolExecutionContext context) {
        List<TopicVO> topics = metadataService.listTopics(
                context.cluster(), null, input.type(), input.search());
        return new ListOutput<>(topics.stream()
                .map(TopicListItem::from)
                .toList());
    }
}
