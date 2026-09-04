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

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.util.Pagination;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerPageVO;
import org.apache.rocketmq.studio.instance.topic.BrokerRouteVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;

import java.util.List;

public interface MetadataProvider {
    List<TopicVO> listTopics(String clusterId, String type, String search);

    default List<TopicVO> listTopics(String instanceId, String clusterId, String type, String search) {
        return listTopics(clusterId, type, search);
    }

    default PageResult<TopicVO> listTopicsPage(String clusterId, String type, String search,
            int page, int pageSize) {
        List<TopicVO> topics = listTopics(clusterId, type, search);
        int total = topics.size();
        long offset = Pagination.pageOffset(page, pageSize);
        int from = (int) Math.min(offset, total);
        int to = from + (int) Math.min(pageSize, total - from);
        return PageResult.of(topics.subList(from, to), total, page, pageSize);
    }

    default PageResult<TopicVO> listTopicsPage(String instanceId, String clusterId, String type,
            String search, int page, int pageSize) {
        return listTopicsPage(clusterId, type, search, page, pageSize);
    }

    List<ConsumerGroupVO> listConsumerGroups(String clusterId, String search);

    default List<ConsumerGroupVO> listConsumerGroups(String instanceId, String clusterId, String search) {
        return listConsumerGroups(clusterId, search);
    }

    default PageResult<ConsumerGroupVO> listConsumerGroupsPage(String clusterId, String search,
            int page, int pageSize) {
        return listConsumerGroupsPage(null, clusterId, search, null, page, pageSize);
    }

    default PageResult<ConsumerGroupVO> listConsumerGroupsPage(String clusterId, String search,
            String subscriptionMode, int page, int pageSize) {
        return listConsumerGroupsPage(null, clusterId, search, subscriptionMode, page, pageSize);
    }

    default PageResult<ConsumerGroupVO> listConsumerGroupsPage(String instanceId, String clusterId,
            String search, String subscriptionMode, int page, int pageSize) {
        List<ConsumerGroupVO> groups = listConsumerGroups(instanceId, clusterId, search);
        if (subscriptionMode != null && !subscriptionMode.isBlank()) {
            String normalizedMode = subscriptionMode.trim();
            groups = groups.stream()
                    .filter(group -> group != null && group.getSubscriptionMode() != null
                            && normalizedMode.equalsIgnoreCase(group.getSubscriptionMode().name()))
                    .toList();
        }
        int total = groups.size();
        long offset = Pagination.pageOffset(page, pageSize);
        int from = (int) Math.min(offset, total);
        int to = from + (int) Math.min(pageSize, total - from);
        return PageResult.of(groups.subList(from, to), total, page, pageSize);
    }

    List<BrokerRouteVO> getTopicRoutes(String instanceId, String name);
    List<TopicConsumerVO> getTopicConsumers(String instanceId, String name);

    default TopicConsumerPageVO getTopicConsumersPage(String instanceId, String name, int page, int pageSize) {
        List<TopicConsumerVO> consumers = getTopicConsumers(instanceId, name);
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
    List<QueueProgressVO> getGroupProgress(String instanceId, String name);
    List<SubscriptionEntryVO> getGroupSubscriptions(String instanceId, String name);
}
