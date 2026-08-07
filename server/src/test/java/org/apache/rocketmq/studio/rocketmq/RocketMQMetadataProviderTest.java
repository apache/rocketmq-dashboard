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
package org.apache.rocketmq.studio.rocketmq;

import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerVO;
import org.apache.rocketmq.studio.persistence.entity.RmqGroup;
import org.apache.rocketmq.studio.persistence.mapper.RmqGroupMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTopicMapper;
import org.apache.rocketmq.remoting.protocol.body.GroupList;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocketMQMetadataProviderTest {

    @Mock
    private RmqTopicMapper topicMapper;

    @Mock
    private RmqGroupMapper groupMapper;

    @Mock
    private DefaultMQAdminExt adminExt;

    @Test
    void listConsumerGroupsReadsConsumeTypeColumn() {
        RmqGroup entity = new RmqGroup();
        entity.setName("group-broadcast");
        entity.setClusterId("cluster-1");
        entity.setConsumeType("BROADCASTING");
        entity.setMessageModel("Push");
        when(groupMapper.selectList(any())).thenReturn(List.of(entity));

        RocketMQMetadataProvider provider =
                new RocketMQMetadataProvider(null, topicMapper, groupMapper);

        List<ConsumerGroupVO> groups = provider.listConsumerGroups("cluster-1", null);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getConsumeType()).isEqualTo(ConsumeType.BROADCASTING);
    }

    @Test
    void listConsumerGroupsFallsBackToClusteringWhenConsumeTypeIsBlank() {
        RmqGroup entity = new RmqGroup();
        entity.setName("group-legacy");
        entity.setClusterId("cluster-1");
        entity.setConsumeType(null);
        entity.setMessageModel("Push");
        when(groupMapper.selectList(any())).thenReturn(List.of(entity));

        RocketMQMetadataProvider provider =
                new RocketMQMetadataProvider(null, topicMapper, groupMapper);

        List<ConsumerGroupVO> groups = provider.listConsumerGroups("cluster-1", null);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getConsumeType()).isEqualTo(ConsumeType.CLUSTERING);
    }

    @Test
    void topicConsumersShouldMarkMetricsUnavailableWhenStatsCannotBeRead() throws Exception {
        GroupList groups = new GroupList();
        groups.setGroupList(new HashSet<>(List.of("order-consumer")));
        when(adminExt.queryTopicConsumeByWho("orders")).thenReturn(groups);
        when(adminExt.examineConsumeStats("order-consumer", "orders"))
                .thenThrow(new RuntimeException("broker unavailable"));

        RocketMQMetadataProvider provider =
                new RocketMQMetadataProvider(adminExt, topicMapper, groupMapper);

        List<TopicConsumerVO> consumers = provider.getTopicConsumers("orders");

        assertThat(consumers).singleElement().satisfies(consumer -> {
            assertThat(consumer.getDiffTotal()).isZero();
            assertThat(consumer.getConsumeTps()).isZero();
            assertThat(consumer.isMetricsAvailable()).isFalse();
        });
    }
}
