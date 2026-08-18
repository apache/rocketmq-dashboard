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
package org.apache.rocketmq.studio.cluster.nameserver;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.persistence.entity.RmqNameserver;
import org.apache.rocketmq.studio.persistence.mapper.RmqNameserverMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NameserverRegistryServiceTest {

    @Mock
    private RmqNameserverMapper nameserverMapper;

    @InjectMocks
    private NameserverRegistryService service;

    @Test
    void listShouldMapAllEntityFieldsTest() {
        LocalDateTime created = LocalDateTime.of(2026, 8, 17, 19, 15, 37);
        RmqNameserver entity = new RmqNameserver();
        entity.setId(1L);
        entity.setName("rocketmq1");
        entity.setNamesrvAddr("rocketmq1-nameserver:9876");
        entity.setK8sNamespace("rocketmq1");
        entity.setStatus("healthy");
        entity.setDescription("community chart cluster");
        entity.setGmtCreate(created);
        entity.setGmtModified(created);
        when(nameserverMapper.selectList(any())).thenReturn(List.of(entity));

        List<NameserverRegistryVO> result = service.list();

        assertThat(result).hasSize(1);
        NameserverRegistryVO vo = result.get(0);
        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getName()).isEqualTo("rocketmq1");
        assertThat(vo.getNamesrvAddr()).isEqualTo("rocketmq1-nameserver:9876");
        assertThat(vo.getK8sNamespace()).isEqualTo("rocketmq1");
        assertThat(vo.getStatus()).isEqualTo("healthy");
        assertThat(vo.getDescription()).isEqualTo("community chart cluster");
        assertThat(vo.getGmtCreate()).isEqualTo(created);
        assertThat(vo.getGmtModified()).isEqualTo(created);
        verify(nameserverMapper).selectList(any());
    }

    @Test
    void createShouldPersistAndReturnStoredEntryTest() {
        when(nameserverMapper.selectCount(any())).thenReturn(0L);
        when(nameserverMapper.insert(any(RmqNameserver.class))).thenAnswer(invocation -> {
            RmqNameserver entity = invocation.getArgument(0);
            entity.setId(9L);
            return 1;
        });
        RmqNameserver stored = new RmqNameserver();
        stored.setId(9L);
        stored.setName("rocketmq3");
        stored.setNamesrvAddr("rocketmq3-nameserver:9876");
        stored.setK8sNamespace("rocketmq3");
        stored.setStatus("healthy");
        when(nameserverMapper.selectById(9L)).thenReturn(stored);

        NameserverRegistryVO created = service.create(CreateNameserverRegistryDTO.builder()
                .name("rocketmq3")
                .namesrvAddr("rocketmq3-nameserver:9876")
                .k8sNamespace("rocketmq3")
                .build());

        assertThat(created.getId()).isEqualTo(9L);
        assertThat(created.getName()).isEqualTo("rocketmq3");
        assertThat(created.getStatus()).isEqualTo("healthy");
        verify(nameserverMapper).insert(any(RmqNameserver.class));
    }

    @Test
    void createShouldRejectDuplicateNameTest() {
        when(nameserverMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(CreateNameserverRegistryDTO.builder()
                .name("rocketmq1")
                .namesrvAddr("10.0.0.1:9876")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 409)
                .hasMessageContaining("already exists");
        verify(nameserverMapper, never()).insert(any(RmqNameserver.class));
    }

    @Test
    void createShouldReturnConflictWhenNameIsInsertedConcurrentlyTest() {
        when(nameserverMapper.selectCount(any())).thenReturn(0L);
        when(nameserverMapper.insert(any(RmqNameserver.class)))
                .thenThrow(new DuplicateKeyException("uk_nameserver_name"));

        assertThatThrownBy(() -> service.create(CreateNameserverRegistryDTO.builder()
                .name("rocketmq1")
                .namesrvAddr("10.0.0.1:9876")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 409)
                .hasMessage("NameServer registry name already exists: rocketmq1");
    }

    @Test
    void updateShouldPersistAndReturnStoredEntryTest() {
        RmqNameserver existing = new RmqNameserver();
        existing.setId(1L);
        existing.setName("rocketmq1");
        when(nameserverMapper.selectById(1L)).thenReturn(existing).thenReturn(existing);
        when(nameserverMapper.selectCount(any())).thenReturn(0L);

        NameserverRegistryVO updated = service.update(UpdateNameserverRegistryDTO.builder()
                .id(1L)
                .name("rocketmq1")
                .namesrvAddr("rocketmq1-nameserver.svc:9876")
                .k8sNamespace("rocketmq1")
                .build());

        assertThat(updated.getNamesrvAddr()).isEqualTo("rocketmq1-nameserver.svc:9876");
        verify(nameserverMapper).updateById(existing);
        assertThat(existing.getNamesrvAddr()).isEqualTo("rocketmq1-nameserver.svc:9876");
    }

    @Test
    void updateShouldThrowWhenEntryMissingTest() {
        when(nameserverMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(UpdateNameserverRegistryDTO.builder()
                .id(404L)
                .name("x")
                .namesrvAddr("x:9876")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");
        verify(nameserverMapper, never()).updateById(any(RmqNameserver.class));
    }

    @Test
    void updateShouldRejectDuplicateNameOfOtherEntryTest() {
        RmqNameserver existing = new RmqNameserver();
        existing.setId(1L);
        when(nameserverMapper.selectById(1L)).thenReturn(existing);
        when(nameserverMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.update(UpdateNameserverRegistryDTO.builder()
                .id(1L)
                .name("rocketmq2")
                .namesrvAddr("x:9876")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 409)
                .hasMessageContaining("already exists");
        verify(nameserverMapper, never()).updateById(any(RmqNameserver.class));
    }

    @Test
    void updateShouldReturnConflictWhenNameIsClaimedConcurrentlyTest() {
        RmqNameserver existing = new RmqNameserver();
        existing.setId(1L);
        when(nameserverMapper.selectById(1L)).thenReturn(existing);
        when(nameserverMapper.selectCount(any())).thenReturn(0L);
        when(nameserverMapper.updateById(any(RmqNameserver.class)))
                .thenThrow(new DuplicateKeyException("uk_nameserver_name"));

        assertThatThrownBy(() -> service.update(UpdateNameserverRegistryDTO.builder()
                .id(1L)
                .name("rocketmq2")
                .namesrvAddr("10.0.0.2:9876")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 409)
                .hasMessage("NameServer registry name already exists: rocketmq2");
    }

    @Test
    void deleteShouldRemoveExistingEntryTest() {
        RmqNameserver stored = new RmqNameserver();
        stored.setId(1L);
        when(nameserverMapper.selectById(1L)).thenReturn(stored);

        service.delete(1L);

        verify(nameserverMapper).deleteById(1L);
    }

    @Test
    void deleteShouldThrowWhenEntryMissingTest() {
        when(nameserverMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");
        verify(nameserverMapper, never()).deleteById(anyLong());
    }
}
