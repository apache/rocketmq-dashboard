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

import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstanceServiceTest {

    @Mock
    private InstanceRepository instanceRepository;

    @InjectMocks
    private InstanceService instanceService;

    @Test
    void listInstancesShouldReturnAllWhenNoFilters() {
        List<InstanceVO> instances = List.of(
                InstanceVO.builder().name("inst-1").build(),
                InstanceVO.builder().name("inst-2").build()
        );
        when(instanceRepository.findAll()).thenReturn(instances);

        List<InstanceVO> result = instanceService.listInstances(null, null);

        assertThat(result).hasSize(2);
        verify(instanceRepository).findAll();
    }

    @Test
    void listInstancesShouldFilterByType() {
        List<InstanceVO> instances = List.of(
                InstanceVO.builder().name("proxy-1").type(InstanceType.PROXY).build()
        );
        when(instanceRepository.findByType(InstanceType.PROXY)).thenReturn(instances);

        List<InstanceVO> result = instanceService.listInstances(InstanceType.PROXY, null);

        assertThat(result).hasSize(1);
        verify(instanceRepository).findByType(InstanceType.PROXY);
    }

    @Test
    void listInstancesShouldSearchByKeyword() {
        List<InstanceVO> instances = List.of(
                InstanceVO.builder().name("production").build()
        );
        when(instanceRepository.search("prod")).thenReturn(instances);

        List<InstanceVO> result = instanceService.listInstances(null, "prod");

        assertThat(result).hasSize(1);
        verify(instanceRepository).search("prod");
    }

    @Test
    void listInstancesShouldTrimSearchKeyword() {
        List<InstanceVO> instances = List.of(
                InstanceVO.builder().name("production").build()
        );
        when(instanceRepository.search("prod")).thenReturn(instances);

        List<InstanceVO> result = instanceService.listInstances(null, " prod ");

        assertThat(result).hasSize(1);
        verify(instanceRepository).search("prod");
    }

    @Test
    void listInstancesShouldFilterByTypeAndSearch() {
        List<InstanceVO> instances = List.of(
                InstanceVO.builder().name("production-proxy").type(InstanceType.PROXY).build()
        );
        when(instanceRepository.findByTypeAndSearch(InstanceType.PROXY, "prod")).thenReturn(instances);

        List<InstanceVO> result = instanceService.listInstances(InstanceType.PROXY, "prod");

        assertThat(result).hasSize(1);
        verify(instanceRepository).findByTypeAndSearch(InstanceType.PROXY, "prod");
    }

    @Test
    void listInstancesShouldTrimSearchKeywordWhenFilteringByType() {
        List<InstanceVO> instances = List.of(
                InstanceVO.builder().name("production-proxy").type(InstanceType.PROXY).build()
        );
        when(instanceRepository.findByTypeAndSearch(InstanceType.PROXY, "prod")).thenReturn(instances);

        List<InstanceVO> result = instanceService.listInstances(InstanceType.PROXY, " prod ");

        assertThat(result).hasSize(1);
        verify(instanceRepository).findByTypeAndSearch(InstanceType.PROXY, "prod");
    }

    @Test
    void listInstancesShouldIgnoreBlankSearch() {
        List<InstanceVO> instances = List.of(InstanceVO.builder().name("inst").build());
        when(instanceRepository.findAll()).thenReturn(instances);

        List<InstanceVO> result = instanceService.listInstances(null, "   ");

        assertThat(result).hasSize(1);
        verify(instanceRepository).findAll();
    }

    @Test
    void createInstanceShouldSetIdAndTimestamps() {
        InstanceVO input = InstanceVO.builder()
                .name("new-instance")
                .endpoint("10.0.1.1:8080")
                .type(InstanceType.PROXY)
                .build();

        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(inv -> inv.getArgument(0));

        InstanceVO result = instanceService.createInstance(input);

        assertThat(result.getId()).isNotBlank();
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
        assertThat(result.getName()).isEqualTo("new-instance");
        verify(instanceRepository).save(any(InstanceVO.class));
    }

    @Test
    void createInstanceShouldThrowWhenNameIsNull() {
        InstanceVO input = InstanceVO.builder()
                .name(null)
                .endpoint("10.0.1.1:8080")
                .build();

        assertThatThrownBy(() -> instanceService.createInstance(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO name is required");
    }

    @Test
    void createInstanceShouldThrowWhenNameIsBlank() {
        InstanceVO input = InstanceVO.builder()
                .name("  ")
                .endpoint("10.0.1.1:8080")
                .build();

        assertThatThrownBy(() -> instanceService.createInstance(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO name is required");
    }

    @Test
    void createInstanceShouldThrowWhenEndpointIsNull() {
        InstanceVO input = InstanceVO.builder()
                .name("valid-name")
                .endpoint(null)
                .build();

        assertThatThrownBy(() -> instanceService.createInstance(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO endpoint is required");
    }

    @Test
    void createInstanceShouldThrowWhenEndpointIsBlank() {
        InstanceVO input = InstanceVO.builder()
                .name("valid-name")
                .endpoint("  ")
                .build();

        assertThatThrownBy(() -> instanceService.createInstance(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO endpoint is required");
    }

    @Test
    void updateInstanceShouldMergeFieldsOntoExisting() {
        LocalDateTime originalCreatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5);
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2025, 2, 3, 4, 5, 6);
        InstanceVO existing = InstanceVO.builder()
                .name("old-name")
                .endpoint("10.0.1.1:8080")
                .type(InstanceType.PROXY)
                .remark("old remark")
                .topicCount(7)
                .consumerGroupCount(3)
                .build();
        existing.setId("inst-1");
        existing.setCreatedAt(originalCreatedAt);
        existing.setUpdatedAt(originalUpdatedAt);

        InstanceVO update = InstanceVO.builder()
                .name("new-name")
                .remark("new remark")
                .build();
        update.setId("inst-1");

        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(existing));
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(inv -> inv.getArgument(0));

        InstanceVO result = instanceService.updateInstance(update);

        assertThat(result.getName()).isEqualTo("new-name");
        assertThat(result.getEndpoint()).isEqualTo("10.0.1.1:8080");
        assertThat(result.getType()).isEqualTo(InstanceType.PROXY);
        assertThat(result.getRemark()).isEqualTo("new remark");
        assertThat(result.getTopicCount()).isEqualTo(7);
        assertThat(result.getConsumerGroupCount()).isEqualTo(3);
        assertThat(result.getId()).isEqualTo("inst-1");
        assertThat(result.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(result.getUpdatedAt()).isAfter(originalUpdatedAt);
        assertThat(result).isNotSameAs(existing);
        assertThat(existing.getName()).isEqualTo("old-name");
        assertThat(existing.getRemark()).isEqualTo("old remark");
        assertThat(existing.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    void updateInstanceShouldNotMutateStoredInstanceWhenSaveFails() {
        LocalDateTime originalCreatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5);
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2025, 2, 3, 4, 5, 6);
        InstanceVO stored = InstanceVO.builder()
                .name("old-name")
                .remark("old remark")
                .type(InstanceType.PROXY)
                .endpoint("10.0.1.1:8080")
                .topicCount(7)
                .consumerGroupCount(3)
                .build();
        stored.setId("inst-1");
        stored.setCreatedAt(originalCreatedAt);
        stored.setUpdatedAt(originalUpdatedAt);

        InstanceVO update = InstanceVO.builder()
                .name("new-name")
                .remark("new remark")
                .type(InstanceType.DIRECT)
                .endpoint("10.0.2.2:10911")
                .build();
        update.setId("inst-1");

        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(stored));
        when(instanceRepository.save(any(InstanceVO.class)))
                .thenThrow(new IllegalStateException("storage unavailable"));

        assertThatThrownBy(() -> instanceService.updateInstance(update))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("storage unavailable");

        assertThat(stored.getName()).isEqualTo("old-name");
        assertThat(stored.getRemark()).isEqualTo("old remark");
        assertThat(stored.getType()).isEqualTo(InstanceType.PROXY);
        assertThat(stored.getEndpoint()).isEqualTo("10.0.1.1:8080");
        assertThat(stored.getTopicCount()).isEqualTo(7);
        assertThat(stored.getConsumerGroupCount()).isEqualTo(3);
        assertThat(stored.getId()).isEqualTo("inst-1");
        assertThat(stored.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(stored.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    void updateInstanceShouldThrowWhenIdIsNull() {
        InstanceVO input = InstanceVO.builder().name("test").build();
        input.setId(null);

        assertThatThrownBy(() -> instanceService.updateInstance(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO ID is required");
    }

    @Test
    void updateInstanceShouldThrowWhenIdIsBlank() {
        InstanceVO input = InstanceVO.builder().name("test").build();
        input.setId("  ");

        assertThatThrownBy(() -> instanceService.updateInstance(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO ID is required");
    }

    @Test
    void updateInstanceShouldThrowWhenInstanceNotFound() {
        InstanceVO input = InstanceVO.builder().name("test").build();
        input.setId("nonexistent");

        when(instanceRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> instanceService.updateInstance(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO not found: nonexistent");
    }

    @Test
    void updateInstanceShouldRejectBlankName() {
        InstanceVO existing = InstanceVO.builder()
                .name("existing-name")
                .endpoint("10.0.1.1:8080")
                .build();
        existing.setId("inst-1");
        InstanceVO update = InstanceVO.builder().name("   ").build();
        update.setId("inst-1");

        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> instanceService.updateInstance(update))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO name is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThat(existing.getName()).isEqualTo("existing-name");
        verify(instanceRepository, never()).save(any(InstanceVO.class));
    }

    @Test
    void updateInstanceShouldRejectBlankEndpointWithoutMutatingExistingFields() {
        InstanceVO existing = InstanceVO.builder()
                .name("existing-name")
                .endpoint("10.0.1.1:8080")
                .build();
        existing.setId("inst-1");
        InstanceVO update = InstanceVO.builder()
                .name("new-name")
                .endpoint("   ")
                .build();
        update.setId("inst-1");

        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> instanceService.updateInstance(update))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO endpoint is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThat(existing.getName()).isEqualTo("existing-name");
        assertThat(existing.getEndpoint()).isEqualTo("10.0.1.1:8080");
        verify(instanceRepository, never()).save(any(InstanceVO.class));
    }

    @Test
    void deleteInstanceShouldRemoveExistingInstance() {
        InstanceVO existing = InstanceVO.builder().name("to-delete").build();
        existing.setId("inst-1");

        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(existing));

        instanceService.deleteInstance("inst-1");

        verify(instanceRepository).deleteById("inst-1");
    }

    @Test
    void deleteInstanceShouldThrowWhenIdIsNull() {
        assertThatThrownBy(() -> instanceService.deleteInstance(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO ID is required");
    }

    @Test
    void deleteInstanceShouldThrowWhenIdIsBlank() {
        assertThatThrownBy(() -> instanceService.deleteInstance("   "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO ID is required");
    }

    @Test
    void deleteInstanceShouldThrowWhenInstanceNotFound() {
        when(instanceRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> instanceService.deleteInstance("missing"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO not found: missing");
    }
}
