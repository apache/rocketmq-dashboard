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
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.persistence.entity.RmqInstance;
import org.apache.rocketmq.studio.persistence.mapper.RmqGroupMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqInstanceMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTopicMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    void findAllShouldMapEntitiesWithoutCountsTest() {
        when(instanceMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(entity(1L, "instance-direct-1", InstanceType.DIRECT),
                        entity(2L, "instance-proxy-1", InstanceType.PROXY_CLUSTER)));

        List<InstanceVO> result = repository.findAll();

        assertThat(result).hasSize(2);
        InstanceVO direct = result.stream()
                .filter(i -> Long.valueOf(1L).equals(i.getId())).findFirst().orElseThrow();
        InstanceVO proxy = result.stream()
                .filter(i -> Long.valueOf(2L).equals(i.getId())).findFirst().orElseThrow();
        assertThat(direct.getTopicCount()).isZero();
        assertThat(direct.getConsumerGroupCount()).isZero();
        assertThat(proxy.getTopicCount()).isZero();
        assertThat(proxy.getConsumerGroupCount()).isZero();
        assertThat(direct.getAdminCredentialRef()).isEqualTo("admin-instance-direct-1");
        verifyNoInteractions(topicMapper, groupMapper);
    }

    @Test
    void findByTypeShouldMatchExactTypeNameTest() {
        when(instanceMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(entity(3L, "cloud-inst", InstanceType.CLOUD)));

        List<InstanceVO> result = repository.findByType(InstanceType.CLOUD);

        assertThat(result).extracting(InstanceVO::getType)
                .containsExactly(InstanceType.CLOUD);
        ArgumentCaptor<QueryWrapper<RmqInstance>> query = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(instanceMapper).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("type =");
        assertThat(query.getValue().getParamNameValuePairs().values())
                .containsExactly(InstanceType.CLOUD.name());
    }

    @Test
    void findByTypeShouldFilterExplicitProxyLocalTypeOnly() {
        when(instanceMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(entity(4L, "local", InstanceType.PROXY_LOCAL)));

        assertThat(repository.findByType(InstanceType.PROXY_LOCAL)).singleElement()
                .extracting(InstanceVO::getType)
                .isEqualTo(InstanceType.PROXY_LOCAL);
        ArgumentCaptor<QueryWrapper<RmqInstance>> query = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(instanceMapper).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("type =");
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
        verifyNoInteractions(topicMapper, groupMapper);
    }

    @Test
    void findByIdShouldReturnEmptyWhenMissing() {
        when(instanceMapper.selectById(999L)).thenReturn(null);

        assertThat(repository.findById(999L)).isEmpty();
    }

    @Test
    void findByIdShouldNotComputeCountsTest() {
        when(instanceMapper.selectById(2L)).thenReturn(entity(2L, "instance-proxy-1", InstanceType.PROXY_CLUSTER));

        Optional<InstanceVO> result = repository.findById(2L);

        assertThat(result).isPresent();
        assertThat(result.get().getTopicCount()).isZero();
        assertThat(result.get().getConsumerGroupCount()).isZero();
        verifyNoInteractions(topicMapper, groupMapper);
    }

    @Test
    void findByIdShouldRejectInvalidPersistedInstanceType() {
        RmqInstance entity = entity(3L, "instance-invalid-type", InstanceType.DIRECT);
        entity.setType("UNKNOWN_TYPE");
        when(instanceMapper.selectById(entity.getId())).thenReturn(entity);

        assertThatThrownBy(() -> repository.findById(entity.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid persisted instance type")
                .hasMessageContaining(String.valueOf(entity.getId()));
    }

    @Test
    void findByIdShouldRejectInvalidPersistedInstanceVendor() {
        RmqInstance entity = entity(4L, "instance-invalid-vendor", InstanceType.DIRECT);
        entity.setVendor("UNKNOWN_VENDOR");
        when(instanceMapper.selectById(entity.getId())).thenReturn(entity);

        assertThatThrownBy(() -> repository.findById(entity.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid persisted instance vendor")
                .hasMessageContaining(String.valueOf(entity.getId()));
    }

    @Test
    void countTopicsByInstanceShouldDelegateToTopicMapperTest() {
        when(topicMapper.selectCount(any(QueryWrapper.class))).thenReturn(5L);

        assertThat(repository.countTopicsByInstance("instance-proxy-1")).isEqualTo(5L);
        verify(topicMapper).selectCount(any(QueryWrapper.class));
    }

    @Test
    void countGroupsByInstanceShouldDelegateToGroupMapperTest() {
        when(groupMapper.selectCount(any(QueryWrapper.class))).thenReturn(2L);

        assertThat(repository.countGroupsByInstance("instance-proxy-1")).isEqualTo(2L);
        verify(groupMapper).selectCount(any(QueryWrapper.class));
    }

    @Test
    void saveShouldInsertWhenInstanceIdIsAbsent() {
        InstanceVO vo = vo(null, "instance-proxy-2", InstanceType.PROXY_CLUSTER);

        repository.save(vo);

        ArgumentCaptor<RmqInstance> entity = ArgumentCaptor.forClass(RmqInstance.class);
        verify(instanceMapper).insert(entity.capture());
        assertThat(entity.getValue().getAdminCredentialRef()).isEqualTo("admin-instance-proxy-2");
        verify(instanceMapper, never()).updateById(any(RmqInstance.class));
    }

    @Test
    void saveShouldUpdateWhenInstanceExists() {
        InstanceVO vo = vo(5L, "instance-proxy-2", InstanceType.PROXY_CLUSTER);
        when(instanceMapper.updateById(any(RmqInstance.class))).thenReturn(1);

        repository.save(vo);

        verify(instanceMapper).updateById(any(RmqInstance.class));
        verify(instanceMapper, never()).insert(any(RmqInstance.class));
    }

    @Test
    void saveShouldNotResurrectAnInstanceDeletedDuringUpdate() {
        // The service read the instance (id 5) and another request deleted it before this
        // write: the update touches zero rows and must fail instead of re-inserting id 5.
        InstanceVO vo = vo(5L, "instance-proxy-2", InstanceType.PROXY_CLUSTER);
        when(instanceMapper.updateById(any(RmqInstance.class))).thenReturn(0);

        assertThatThrownBy(() -> repository.save(vo))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Instance update was not applied: 5")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(409));
        verify(instanceMapper, never()).insert(any(RmqInstance.class));
    }

    @Test
    void deleteByIdShouldDelegateToMapper() {
        repository.deleteById(1L);
        verify(instanceMapper).deleteById(1L);
    }

    @Test
    void deleteByIdShouldReportWhetherARowWasRemoved() {
        when(instanceMapper.deleteById(1L)).thenReturn(1);
        when(instanceMapper.deleteById(2L)).thenReturn(0);

        assertThat(repository.deleteById(1L)).isTrue();
        assertThat(repository.deleteById(2L)).isFalse();
    }

    private RmqInstance entity(Long id, String name, InstanceType type) {
        RmqInstance entity = new RmqInstance();
        entity.setId(id);
        entity.setName(name);
        entity.setType(type.name());
        entity.setEndpoint("10.0.0.1:9876");
        entity.setVendor(InstanceVendor.APACHE.name());
        entity.setAdminCredentialRef("admin-" + name);
        entity.setGmtCreate(LocalDateTime.of(2026, 8, 3, 0, 0));
        entity.setGmtModified(LocalDateTime.of(2026, 8, 3, 0, 0));
        return entity;
    }

    private InstanceVO vo(Long id, String name, InstanceType type) {
        InstanceVO vo = InstanceVO.builder()
                .name(name)
                .type(type)
                .endpoint("10.0.0.1:9876")
                .build();
        vo.setId(id);
        vo.setAdminCredentialRef("admin-" + name);
        return vo;
    }
}
