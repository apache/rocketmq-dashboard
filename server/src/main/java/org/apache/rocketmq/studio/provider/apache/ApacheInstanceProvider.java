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
package org.apache.rocketmq.studio.provider.apache;

import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.apache.rocketmq.studio.instance.message.MessageProvider;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Objects;

/**
 * Open-source Apache RocketMQ implementation: pure delegation to the existing admin-client
 * beans, keeping behavior identical to the pre-provider code path.
 */
@RequiredArgsConstructor
@Component
public class ApacheInstanceProvider implements InstanceProvider {

    private final MetadataProvider metadataProvider;
    private final AdminClient adminClient;
    private final MessageProvider messageProvider;
    private final InstanceRepository instanceRepository;

    @Override
    public InstanceVendor vendor() {
        return InstanceVendor.APACHE;
    }

    @Override
    public int countTopics(String instanceId) {
        return (int) instanceRepository.countTopicsByInstance(instanceId);
    }

    @Override
    public int countGroups(String instanceId) {
        return (int) instanceRepository.countGroupsByInstance(instanceId);
    }

    @Override
    public List<TopicVO> listTopics(String instanceId, String type, String search) {
        return metadataProvider.listTopics(null, type, search).stream()
                .filter(topic -> matchesInstance(topic.getInstanceId(), instanceId))
                .toList();
    }

    @Override
    public TopicVO createTopic(String instanceId, TopicVO topic) {
        return adminClient.createTopic(topic);
    }

    @Override
    public TopicVO updateTopic(String instanceId, TopicVO topic) {
        return adminClient.updateTopic(topic);
    }

    @Override
    public void deleteTopic(String instanceId, String topicName) {
        adminClient.deleteTopic(topicName);
    }

    @Override
    public List<TopicConsumerVO> getTopicConsumers(String instanceId, String topicName) {
        return metadataProvider.getTopicConsumers(instanceId, topicName);
    }

    @Override
    public List<ConsumerGroupVO> listConsumerGroups(String instanceId, String search) {
        return metadataProvider.listConsumerGroups(null, search).stream()
                .filter(group -> matchesInstance(group.getInstanceId(), instanceId))
                .toList();
    }

    @Override
    public ConsumerGroupVO createConsumerGroup(String instanceId, ConsumerGroupVO group) {
        return adminClient.createConsumerGroup(group);
    }

    @Override
    public void deleteConsumerGroup(String instanceId, String groupName) {
        adminClient.deleteConsumerGroup(groupName);
    }

    @Override
    public List<QueueProgressVO> getGroupProgress(String instanceId, String groupName) {
        return metadataProvider.getGroupProgress(instanceId, groupName);
    }

    @Override
    public List<SubscriptionEntryVO> getGroupSubscriptions(String instanceId, String groupName) {
        return metadataProvider.getGroupSubscriptions(instanceId, groupName);
    }

    @Override
    public void resetOffset(String instanceId, String groupName, long timestamp, String topic) {
        adminClient.resetOffset(instanceId, groupName, timestamp, topic);
    }

    @Override
    public List<MessageRecordVO> queryMessages(String instanceId, String topic, String msgId,
                                               String tag, String key, Long startTime, Long endTime) {
        return messageProvider.queryMessages(instanceId, topic, msgId, tag, key, startTime, endTime);
    }

    @Override
    public TraceRecordVO getMessageTrace(String instanceId, String msgId) {
        return messageProvider.getMessageTrace(instanceId, msgId);
    }

    private boolean matchesInstance(String topicInstanceId, String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return true;
        }
        return Objects.equals(topicInstanceId, instanceId);
    }
}
