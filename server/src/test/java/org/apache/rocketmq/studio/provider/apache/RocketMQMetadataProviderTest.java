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

import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.persistence.entity.RmqGroup;
import org.apache.rocketmq.studio.persistence.mapper.RmqGroupMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTopicMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocketMQMetadataProviderTest {

    @Mock
    private RmqTopicMapper topicMapper;

    @Mock
    private RmqGroupMapper groupMapper;

    /**
     * Builds a provider without a configured NameServer, mirroring the former absent admin bean:
     * DB-backed listings work while live enrichment is skipped.
     */
    private RocketMQMetadataProvider newProvider() {
        return new RocketMQMetadataProvider(mock(MqAdminExtFactory.class), new RocketMQProperties(),
                topicMapper, groupMapper);
    }

    @Test
    void listConsumerGroupsReadsConsumeTypeColumn() {
        RmqGroup entity = new RmqGroup();
        entity.setName("group-broadcast");
        entity.setClusterId("cluster-1");
        entity.setConsumeType("BROADCASTING");
        entity.setMessageModel("Push");
        when(groupMapper.selectList(any())).thenReturn(List.of(entity));

        RocketMQMetadataProvider provider = newProvider();

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

        RocketMQMetadataProvider provider = newProvider();

        List<ConsumerGroupVO> groups = provider.listConsumerGroups("cluster-1", null);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getConsumeType()).isEqualTo(ConsumeType.CLUSTERING);
    }
}
