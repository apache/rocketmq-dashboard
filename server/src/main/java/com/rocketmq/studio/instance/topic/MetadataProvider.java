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
package com.rocketmq.studio.instance.topic;

import com.rocketmq.studio.instance.group.ConsumerGroupVO;
import com.rocketmq.studio.instance.group.QueueProgressVO;
import com.rocketmq.studio.instance.group.SubscriptionEntryVO;

import java.util.List;

public interface MetadataProvider {
    List<NamespaceVO> listNamespaces();
    List<TopicVO> listTopics(String clusterId, String type, String search);
    List<ConsumerGroupVO> listConsumerGroups(String clusterId, String search);
    List<BrokerRouteVO> getTopicRoutes(String name);
    List<TopicConsumerVO> getTopicConsumers(String name);
    List<QueueProgressVO> getGroupProgress(String name);
    List<SubscriptionEntryVO> getGroupSubscriptions(String name);
}
