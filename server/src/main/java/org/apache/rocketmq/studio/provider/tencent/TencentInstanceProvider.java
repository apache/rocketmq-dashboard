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
package org.apache.rocketmq.studio.provider.tencent;

import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tencent Cloud TDMQ placeholder: package structure reserved, all operations unsupported.
 */
@Component
public class TencentInstanceProvider implements InstanceProvider {

    private static final String NOT_IMPLEMENTED = "Tencent Cloud provider is not implemented yet";

    @Override
    public InstanceVendor vendor() {
        return InstanceVendor.TENCENT;
    }

    @Override
    public List<TopicVO> listTopics(String instanceId, String type, String search) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public TopicVO createTopic(String instanceId, TopicVO topic) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public TopicVO updateTopic(String instanceId, TopicVO topic) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void deleteTopic(String instanceId, String topicName) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public List<TopicConsumerVO> getTopicConsumers(String instanceId, String topicName) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public List<ConsumerGroupVO> listConsumerGroups(String instanceId, String search) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public ConsumerGroupVO createConsumerGroup(String instanceId, ConsumerGroupVO group) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void deleteConsumerGroup(String instanceId, String groupName) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public List<QueueProgressVO> getGroupProgress(String instanceId, String groupName) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public List<SubscriptionEntryVO> getGroupSubscriptions(String instanceId, String groupName) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void resetOffset(String instanceId, String groupName, long timestamp, String topic) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public List<MessageRecordVO> queryMessages(String instanceId, String topic, String msgId,
                                               String tag, String key, Long startTime, Long endTime) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public TraceRecordVO getMessageTrace(String instanceId, String msgId) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }
}
