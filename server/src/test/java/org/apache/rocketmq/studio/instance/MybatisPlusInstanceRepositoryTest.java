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

package org.apache.rocketmq.studio.instance;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.persistence.entity.RmqInstance;
import org.apache.rocketmq.studio.persistence.mapper.RmqGroupMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqInstanceMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTopicMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MybatisPlusInstanceRepositoryTest {

    @Mock
    private RmqInstanceMapper instanceMapper;

    @Mock
    private RmqTopicMapper topicMapper;

    @Mock
    private RmqGroupMapper groupMapper;

    @InjectMocks
    private MybatisPlusInstanceRepository repository;

    @Test
    void findAllShouldPopulateCountsGroupedByInstance() {
        when(instanceMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(entity("instance-direct-1", InstanceType.DIRECT),
                        entity("instance-proxy-1", InstanceType.PROXY)));
        when(topicMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of("instance_id", "instance-proxy-1", "total", 3L)));
        when(groupMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of("instance_id", "instance-proxy-1", "total", 2L),
                        Map.of("instance_id", "instance-direct-1", "total", 1L)));

        List<InstanceVO> result = repository.findAll();

        assertThat(result).hasSize(2);
        InstanceVO direct = result.stream()
                .filter(i -> "instance-direct-1".equals(i.getId())).findFirst().orElseThrow();
        InstanceVO proxy = result.stream()
                .filter(i -> "instance-proxy-1".equals(i.getId())).findFirst().orElseThrow();
        assertThat(direct.getTopicCount()).isZero();
        assertThat(direct.getConsumerGroupCount()).isEqualTo(1);
        assertThat(proxy.getTopicCount()).isEqualTo(3);
        assertThat(proxy.getConsumerGroupCount()).isEqualTo(2);
    }

    @Test
    void constructorShouldNotSeedDemoInstances() {
        when(instanceMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        assertThat(repository.findAll()).isEmpty();
        verify(instanceMapper, never()).insert(any(RmqInstance.class));
    }

    @Test
    void findAllShouldReturnEmptyWithoutCountQueriesWhenNoInstances() {
        when(instanceMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        assertThat(repository.findAll()).isEmpty();
        verify(topicMapper, never()).selectMaps(any(QueryWrapper.class));
        verify(groupMapper, never()).selectMaps(any(QueryWrapper.class));
    }

    @Test
    void findByIdShouldReturnEmptyWhenMissing() {
        when(instanceMapper.selectById("missing")).thenReturn(null);

        assertThat(repository.findById("missing")).isEmpty();
    }

    @Test
    void findByIdShouldPopulateCountsForSingleInstance() {
        when(instanceMapper.selectById("instance-proxy-1")).thenReturn(entity("instance-proxy-1", InstanceType.PROXY));
        when(topicMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of("instance_id", "instance-proxy-1", "total", 5L)));
        when(groupMapper.selectMaps(any(QueryWrapper.class))).thenReturn(List.of());

        Optional<InstanceVO> result = repository.findById("instance-proxy-1");

        assertThat(result).isPresent();
        assertThat(result.get().getTopicCount()).isEqualTo(5);
        assertThat(result.get().getConsumerGroupCount()).isZero();
    }

    @Test
    void saveShouldInsertWhenInstanceAbsent() {
        InstanceVO vo = vo("instance-proxy-2", InstanceType.PROXY);
        when(instanceMapper.selectById("instance-proxy-2")).thenReturn(null);

        repository.save(vo);

        verify(instanceMapper).insert(any(RmqInstance.class));
        verify(instanceMapper, never()).updateById(any(RmqInstance.class));
    }

    @Test
    void saveShouldUpdateWhenInstanceExists() {
        InstanceVO vo = vo("instance-proxy-2", InstanceType.PROXY);
        when(instanceMapper.selectById("instance-proxy-2")).thenReturn(entity("instance-proxy-2", InstanceType.PROXY));

        repository.save(vo);

        verify(instanceMapper).updateById(any(RmqInstance.class));
        verify(instanceMapper, never()).insert(any(RmqInstance.class));
    }

    @Test
    void deleteByIdShouldDelegateToMapper() {
        repository.deleteById("instance-direct-1");

        verify(instanceMapper).deleteById("instance-direct-1");
    }

    private RmqInstance entity(String id, InstanceType type) {
        RmqInstance entity = new RmqInstance();
        entity.setId(id);
        entity.setName(id);
        entity.setType(type.name());
        entity.setEndpoint("10.0.0.1:9876");
        entity.setCreatedAt(LocalDateTime.of(2026, 8, 3, 0, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 8, 3, 0, 0));
        return entity;
    }

    private InstanceVO vo(String id, InstanceType type) {
        InstanceVO vo = InstanceVO.builder()
                .name(id)
                .type(type)
                .endpoint("10.0.0.1:9876")
                .build();
        vo.setId(id);
        return vo;
    }
}
