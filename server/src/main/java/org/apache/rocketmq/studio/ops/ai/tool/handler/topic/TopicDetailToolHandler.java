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
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.instance.topic.BrokerRouteVO;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.CapabilityResolver;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicDetailInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicDetailOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicQueueStatsItem;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Aggregated topic detail (decision 10): configuration, consumer groups, broker routes and
 * per-queue offset stats. queueStats degrades to null — never an error — when the instance
 * lacks the REMOTING capability or stats collection fails.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TopicDetailToolHandler
        implements ToolHandler<TopicDetailInput, TopicDetailOutput> {

    private static final String REMOTING_CAPABILITY = "REMOTING";

    private final MetadataService metadataService;
    private final CapabilityResolver capabilityResolver;

    @Override
    public String name() {
        return "rmq.topic.detail";
    }

    @Override
    public Class<TopicDetailInput> inputType() {
        return TopicDetailInput.class;
    }

    @Override
    public TopicDetailOutput execute(
            TopicDetailInput input, ToolExecutionContext context) {
        String instanceId = context.instanceId();
        String topicName = input.topicName();
        TopicVO topic = metadataService.getTopic(instanceId, null, topicName);
        List<TopicConsumerVO> consumers =
                metadataService.getTopicConsumers(instanceId, topicName);
        List<BrokerRouteVO> routes =
                metadataService.getTopicRoutes(instanceId, topicName);
        return TopicDetailOutput.from(topic, instanceId, consumers, routes,
                resolveQueueStats(instanceId, topicName));
    }

    private List<TopicQueueStatsItem> resolveQueueStats(String instanceId, String topicName) {
        try {
            if (!capabilityResolver.resolve(instanceId).contains(REMOTING_CAPABILITY)) {
                return null;
            }
            return metadataService.getTopicStats(instanceId, topicName).stream()
                    .map(TopicQueueStatsItem::from)
                    .toList();
        } catch (Exception e) {
            log.debug("Queue stats unavailable for topic {} on instance {}: {}",
                    topicName, instanceId, e.getMessage());
            return null;
        }
    }
}
