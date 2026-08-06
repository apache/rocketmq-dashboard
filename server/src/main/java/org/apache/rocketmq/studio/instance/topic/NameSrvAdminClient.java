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
package org.apache.rocketmq.studio.instance.topic;

import org.apache.rocketmq.studio.provider.apache.AdminClient;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;

import lombok.extern.slf4j.Slf4j;

/**
 * Stub admin client. Disabled in favor of RocketMQAdminClientImpl.
 */
@Slf4j
public class NameSrvAdminClient implements AdminClient {

    @Override
    public TopicVO getTopic(String name) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ConsumerGroupVO getConsumerGroup(String name) {
        throw consumerGroupAdminUnavailable();
    }

    @Override
    public TopicVO createTopic(TopicVO topic) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public TopicVO updateTopic(TopicVO topic) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void deleteTopic(String name) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public SendMessageVO sendMessage(SendMessageDTO request) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ConsumerGroupVO createConsumerGroup(ConsumerGroupVO group) {
        throw consumerGroupAdminUnavailable();
    }

    @Override
    public void deleteConsumerGroup(String instanceId, String name) {
        throw consumerGroupAdminUnavailable();
    }

    @Override
    public void resetOffset(String instanceId, String name, long timestamp, String topic) {
        throw consumerGroupAdminUnavailable();
    }

    private BusinessException consumerGroupAdminUnavailable() {
        return new BusinessException(501, "Consumer group admin client is not configured");
    }
}
