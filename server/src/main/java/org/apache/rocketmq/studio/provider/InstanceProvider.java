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
package org.apache.rocketmq.studio.provider;

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.util.Pagination;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerPageVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;

import java.util.List;
import java.util.Set;

/**
 * Unified instance-scoped operations SPI. Every method takes the Studio instance id as its
 * first argument; implementations resolve the target cluster / cloud instance themselves.
 * Unsupported operations throw {@link UnsupportedOperationException} (mapped to HTTP 501).
 */
public interface InstanceProvider {

    InstanceVendor vendor();

    default Set<InstanceCapability> capabilities() {
        return Set.of();
    }

    int countTopics(String instanceId);

    int countGroups(String instanceId);

    List<TopicVO> listTopics(String instanceId, String type, String search);

    default PageResult<TopicVO> listTopicsPage(String instanceId, String type, String search,
            int page, int pageSize) {
        List<TopicVO> topics = listTopics(instanceId, type, search);
        int total = topics.size();
        long offset = Pagination.pageOffset(page, pageSize);
        int from = (int) Math.min(offset, total);
        int to = from + (int) Math.min(pageSize, total - from);
        return PageResult.of(topics.subList(from, to), total, page, pageSize);
    }

    TopicVO createTopic(String instanceId, TopicVO topic);

    TopicVO updateTopic(String instanceId, TopicVO topic);

    void deleteTopic(String instanceId, String topicName);

    List<TopicConsumerVO> getTopicConsumers(String instanceId, String topicName);

    default TopicConsumerPageVO getTopicConsumersPage(String instanceId, String topicName, int page, int pageSize) {
        List<TopicConsumerVO> consumers = getTopicConsumers(instanceId, topicName);
        int total = consumers.size();
        long offset = Pagination.pageOffset(page, pageSize);
        int from = (int) Math.min(offset, total);
        int to = from + (int) Math.min(pageSize, total - from);
        return TopicConsumerPageVO.builder()
                .items(consumers.subList(from, to))
                .total(total)
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    List<ConsumerGroupVO> listConsumerGroups(String instanceId, String search);

    ConsumerGroupVO createConsumerGroup(String instanceId, ConsumerGroupVO group);

    void deleteConsumerGroup(String instanceId, String groupName);

    List<QueueProgressVO> getGroupProgress(String instanceId, String groupName);

    List<SubscriptionEntryVO> getGroupSubscriptions(String instanceId, String groupName);

    void resetOffset(String instanceId, String groupName, long timestamp, String topic);

    List<MessageRecordVO> queryMessages(String instanceId, String topic, String msgId,
                                        String tag, String key, Long startTime, Long endTime);

    default PageResult<MessageRecordVO> queryMessagesPage(String instanceId, String topic, String msgId,
                                                          String tag, String key, Long startTime, Long endTime,
                                                          int page, int pageSize) {
        throw new BusinessException(501, "Paged message query is not supported by this provider");
    }

    TraceRecordVO getMessageTrace(String instanceId, String msgId, String topic);
}
