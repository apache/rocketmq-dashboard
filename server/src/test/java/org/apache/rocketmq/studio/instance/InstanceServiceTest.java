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

import org.apache.rocketmq.studio.provider.credential.CloudCredentialRepository;
import org.apache.rocketmq.studio.provider.credential.CloudCredentialVO;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.provider.CloudCatalogProvider;
import org.apache.rocketmq.studio.provider.CloudInstanceDetailVO;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.apache.rocketmq.studio.provider.InstanceProvider;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstanceServiceTest {

    @Mock
    private InstanceRepository instanceRepository;

    @Mock
    private CloudCredentialRepository cloudCredentialRepository;

    @Mock
    private InstanceProviderRegistry providerRegistry;

    @Mock
    private InstanceProvider instanceProvider;

    @Mock
    private MqAdminExtFactory adminFactory;

    @Mock
    private OperationAuditService operationAuditService;

    @InjectMocks
    private InstanceService instanceService;

    @Test
    void listInstancesShouldReturnAllWhenNoFilters() {
        List<InstanceVO> instances = List.of(
                InstanceVO.builder().name("inst-1").build(),
                InstanceVO.builder().name("inst-2").build()
        );
        when(instanceRepository.findAll()).thenReturn(instances);
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);

        List<InstanceVO> result = instanceService.listInstances(null, null);

        assertThat(result).hasSize(2);
        verify(instanceRepository).findAll();
    }

    @Test
    void listInstancesMarksCloudCountsUnavailableWhenProviderFails() {
        InstanceVO instance = InstanceVO.builder().vendor(InstanceVendor.ALIYUN).build();
        instance.setId("cloud-1");
        InstanceProvider provider = org.mockito.Mockito.mock(InstanceProvider.class);
        when(instanceRepository.findAll()).thenReturn(List.of(instance));
        when(providerRegistry.forVendor(InstanceVendor.ALIYUN)).thenReturn(provider);
        when(provider.countTopics("cloud-1")).thenThrow(new IllegalStateException("access denied"));

        InstanceVO result = instanceService.listInstances(null, null).get(0);

        assertThat(result.isResourceCountsAvailable()).isFalse();
    }

    @Test
    void listInstancesKeepsCloudCountsAvailableWhenProviderReturnsEmptyLists() {
        InstanceVO instance = InstanceVO.builder().vendor(InstanceVendor.ALIYUN).build();
        instance.setId("cloud-1");
        InstanceProvider provider = org.mockito.Mockito.mock(InstanceProvider.class);
        when(instanceRepository.findAll()).thenReturn(List.of(instance));
        when(providerRegistry.forVendor(InstanceVendor.ALIYUN)).thenReturn(provider);
        when(provider.countTopics("cloud-1")).thenReturn(0);
        when(provider.countGroups("cloud-1")).thenReturn(0);

        InstanceVO result = instanceService.listInstances(null, null).get(0);

        assertThat(result.isResourceCountsAvailable()).isTrue();
        assertThat(result.getTopicCount()).isZero();
        assertThat(result.getConsumerGroupCount()).isZero();
    }

    @Test
    void listInstancesShouldFilterByType() {
        List<InstanceVO> instances = List.of(
                InstanceVO.builder().name("proxy-1").type(InstanceType.PROXY).build()
        );
        when(instanceRepository.findByType(InstanceType.PROXY)).thenReturn(instances);
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);

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
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);

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
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);

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
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);

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
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);

        List<InstanceVO> result = instanceService.listInstances(InstanceType.PROXY, " prod ");

        assertThat(result).hasSize(1);
        verify(instanceRepository).findByTypeAndSearch(InstanceType.PROXY, "prod");
    }

    @Test
    void listInstancesShouldIgnoreBlankSearch() {
        List<InstanceVO> instances = List.of(InstanceVO.builder().name("inst").build());
        when(instanceRepository.findAll()).thenReturn(instances);
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);

        List<InstanceVO> result = instanceService.listInstances(null, "   ");

        assertThat(result).hasSize(1);
        verify(instanceRepository).findAll();
    }

    @Test
    void listInstancesShouldFillCountsThroughVendorProviderTest() {
        InstanceVO apache = InstanceVO.builder().name("apache").build();
        apache.setId("inst-apache");
        InstanceVO aliyun = InstanceVO.builder().name("aliyun").vendor(InstanceVendor.ALIYUN).build();
        aliyun.setId("inst-aliyun");
        InstanceProvider aliyunProvider = org.mockito.Mockito.mock(InstanceProvider.class);
        when(instanceRepository.findAll()).thenReturn(List.of(apache, aliyun));
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);
        when(providerRegistry.forVendor(InstanceVendor.ALIYUN)).thenReturn(aliyunProvider);
        when(instanceProvider.countTopics("inst-apache")).thenReturn(3);
        when(instanceProvider.countGroups("inst-apache")).thenReturn(2);
        when(aliyunProvider.countTopics("inst-aliyun")).thenReturn(5);
        when(aliyunProvider.countGroups("inst-aliyun")).thenReturn(4);

        List<InstanceVO> result = instanceService.listInstances(null, null);

        assertThat(result.get(0).getTopicCount()).isEqualTo(3);
        assertThat(result.get(0).getConsumerGroupCount()).isEqualTo(2);
        assertThat(result.get(1).getTopicCount()).isEqualTo(5);
        assertThat(result.get(1).getConsumerGroupCount()).isEqualTo(4);
    }

    @Test
    void listInstancesShouldKeepZeroCountsWhenProviderFailsTest() {
        InstanceVO instance = InstanceVO.builder().name("broken").build();
        instance.setId("inst-broken");
        when(instanceRepository.findAll()).thenReturn(List.of(instance));
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);
        when(instanceProvider.countTopics("inst-broken"))
                .thenThrow(new IllegalStateException("admin unavailable"));

        List<InstanceVO> result = instanceService.listInstances(null, null);

        assertThat(result.get(0).getTopicCount()).isZero();
        assertThat(result.get(0).getConsumerGroupCount()).isZero();
    }

    @Test
    void createInstanceShouldThrowWhenRequestIsNull() {
        assertThatThrownBy(() -> instanceService.createInstance(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Instance request is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verifyNoInteractions(instanceRepository);
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
        verify(operationAuditService).record(eq("CREATE_INSTANCE"), eq("INSTANCE"), eq(result.getId()), eq(null),
                argThat(detail -> detail.equals("name=new-instance, vendor=APACHE, type=PROXY")
                        && !detail.contains("10.0.1.1:8080")),
                eq("SUCCESS"), eq(null));
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
    void createInstanceShouldRejectEndpointWithEmptyAddressSegment() {
        InstanceVO input = InstanceVO.builder().name("valid-name").endpoint("namesrv:9876;").build();

        assertThatThrownBy(() -> instanceService.createInstance(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO endpoint must not contain empty addresses");
    }

    @Test
    void createInstanceShouldTrimEndpointBeforeSaving() {
        InstanceVO input = InstanceVO.builder().name("valid-name").type(InstanceType.PROXY)
                .endpoint("  namesrv:9876  ").build();
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(instanceService.createInstance(input).getEndpoint()).isEqualTo("namesrv:9876");
    }

    @Test
    void createInstanceShouldTrimNameBeforeSaving() {
        InstanceVO input = InstanceVO.builder().name("  production  ").type(InstanceType.PROXY)
                .endpoint("namesrv:9876").build();
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(instanceService.createInstance(input).getName()).isEqualTo("production");
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
        verify(operationAuditService).record(eq("UPDATE_INSTANCE"), eq("INSTANCE"), eq("inst-1"), eq(null),
                eq("name=new-name, vendor=APACHE, type=PROXY"), eq("SUCCESS"), eq(null));
    }

    @Test
    void updateInstanceShouldTrimNameBeforeSaving() {
        InstanceVO existing = InstanceVO.builder().name("old-name").endpoint("namesrv:9876").build();
        existing.setId("inst-1");
        InstanceVO update = InstanceVO.builder().name("  production  ").build();
        update.setId("inst-1");
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(existing));
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(instanceService.updateInstance(update).getName()).isEqualTo("production");
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
    void updateInstanceShouldReleaseUnusedPreviousEndpoint() {
        InstanceVO existing = InstanceVO.builder()
                .name("instance")
                .endpoint("old-namesrv:9876")
                .build();
        existing.setId("inst-1");
        InstanceVO update = InstanceVO.builder().endpoint("new-namesrv:9876").build();
        update.setId("inst-1");

        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(existing));
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(instanceRepository.findAll()).thenReturn(List.of());

        instanceService.updateInstance(update);

        verify(adminFactory).release("old-namesrv:9876");
    }

    @Test
    void updateInstanceShouldKeepEndpointClientWhenAnotherInstanceReferencesIt() {
        InstanceVO existing = InstanceVO.builder()
                .name("instance")
                .endpoint("shared-namesrv:9876")
                .build();
        existing.setId("inst-1");
        InstanceVO shared = InstanceVO.builder()
                .name("shared-instance")
                .endpoint(" shared-namesrv:9876 ")
                .build();
        shared.setId("inst-2");
        InstanceVO update = InstanceVO.builder().endpoint("new-namesrv:9876").build();
        update.setId("inst-1");

        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(existing));
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(instanceRepository.findAll()).thenReturn(List.of(shared));

        instanceService.updateInstance(update);

        verify(adminFactory, never()).release("shared-namesrv:9876");
    }

    @Test
    void updateInstanceShouldKeepEndpointClientWhenAnotherInstanceUsesEquivalentAddressList() {
        InstanceVO existing = InstanceVO.builder()
                .name("instance")
                .endpoint("namesrv-b:9876;namesrv-a:9876")
                .build();
        existing.setId("inst-1");
        InstanceVO shared = InstanceVO.builder()
                .name("shared-instance")
                .endpoint(" namesrv-a:9876, namesrv-b:9876 ")
                .build();
        shared.setId("inst-2");
        InstanceVO update = InstanceVO.builder().endpoint("new-namesrv:9876").build();
        update.setId("inst-1");

        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(existing));
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(instanceRepository.findAll()).thenReturn(List.of(shared));

        instanceService.updateInstance(update);

        verifyNoInteractions(adminFactory);
    }

    @Test
    void updateInstanceShouldThrowWhenRequestIsNull() {
        assertThatThrownBy(() -> instanceService.updateInstance(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Instance request is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verifyNoInteractions(instanceRepository);
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
    void updateInstanceShouldRejectEndpointWithEmptyAddressSegment() {
        InstanceVO existing = InstanceVO.builder().name("instance").endpoint("namesrv:9876").build();
        existing.setId("inst-1");
        InstanceVO update = InstanceVO.builder().endpoint("; ;").build();
        update.setId("inst-1");
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> instanceService.updateInstance(update))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO endpoint must not contain empty addresses");
        verify(instanceRepository, never()).save(any(InstanceVO.class));
    }

    @Test
    void deleteInstanceShouldRemoveExistingInstance() {
        InstanceVO existing = InstanceVO.builder().name("to-delete").build();
        existing.setId("inst-1");

        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(existing));
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);
        when(instanceProvider.countTopics("inst-1")).thenReturn(0);
        when(instanceProvider.countGroups("inst-1")).thenReturn(0);

        instanceService.deleteInstance("inst-1");

        verify(instanceRepository).deleteById("inst-1");
        verify(operationAuditService).record(eq("DELETE_INSTANCE"), eq("INSTANCE"), eq("inst-1"), eq(null),
                eq("name=to-delete, vendor=APACHE, type=null"), eq("SUCCESS"), eq(null));
    }

    @Test
    void deleteInstanceShouldRejectInstanceWithTopics() {
        InstanceVO existing = InstanceVO.builder()
                .name("with-topics")
                .topicCount(0)
                .build();
        existing.setId("inst-1");
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(existing));
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);
        when(instanceProvider.countTopics("inst-1")).thenReturn(2);
        when(instanceProvider.countGroups("inst-1")).thenReturn(0);

        assertThatThrownBy(() -> instanceService.deleteInstance("inst-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cannot delete instance with managed resources: topics=2, consumerGroups=0")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(instanceRepository, never()).deleteById("inst-1");
    }

    @Test
    void deleteInstanceShouldRejectInstanceWithConsumerGroups() {
        InstanceVO existing = InstanceVO.builder()
                .name("with-consumer-groups")
                .consumerGroupCount(0)
                .build();
        existing.setId("inst-1");
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(existing));
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);
        when(instanceProvider.countTopics("inst-1")).thenReturn(0);
        when(instanceProvider.countGroups("inst-1")).thenReturn(3);

        assertThatThrownBy(() -> instanceService.deleteInstance("inst-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cannot delete instance with managed resources: topics=0, consumerGroups=3")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(instanceRepository, never()).deleteById("inst-1");
    }

    @Test
    void deleteInstanceShouldRejectWhenProviderCannotCountManagedResources() {
        InstanceVO existing = InstanceVO.builder()
                .name("tencent-instance")
                .vendor(InstanceVendor.TENCENT)
                .build();
        existing.setId("inst-tencent");
        when(instanceRepository.findById("inst-tencent")).thenReturn(Optional.of(existing));
        when(providerRegistry.forVendor(InstanceVendor.TENCENT)).thenReturn(instanceProvider);
        when(instanceProvider.vendor()).thenReturn(InstanceVendor.TENCENT);
        when(instanceProvider.countTopics("inst-tencent")).thenReturn(0);
        when(instanceProvider.countGroups("inst-tencent"))
                .thenThrow(new UnsupportedOperationException("Tencent groups are unavailable"));

        assertThatThrownBy(() -> instanceService.deleteInstance("inst-tencent"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cannot delete instance because managed resource counts are unavailable for TENCENT")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(501));

        verify(instanceRepository, never()).deleteById("inst-tencent");
    }

    @Test
    void deleteInstanceShouldReleaseUnusedEndpoint() {
        InstanceVO existing = InstanceVO.builder()
                .name("to-delete")
                .endpoint("namesrv:9876")
                .build();
        existing.setId("inst-1");
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(existing));
        when(instanceRepository.findAll()).thenReturn(List.of());
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);

        instanceService.deleteInstance("inst-1");

        verify(instanceRepository).deleteById("inst-1");
        verify(adminFactory).release("namesrv:9876");
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

    @Test
    void createInstanceShouldDefaultToApacheVendorTest() {
        InstanceVO instance = InstanceVO.builder()
                .name("inst")
                .endpoint("10.0.0.1:8080")
                .type(InstanceType.PROXY)
                .build();
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InstanceVO created = instanceService.createInstance(instance);

        assertThat(created.getVendor()).isEqualTo(InstanceVendor.APACHE);
        verifyNoInteractions(cloudCredentialRepository, providerRegistry);
    }

    @Test
    void createApacheInstanceShouldRequireTypeTest() {
        InstanceVO instance = InstanceVO.builder()
                .name("inst")
                .endpoint("10.0.0.1:8080")
                .build();

        assertThatThrownBy(() -> instanceService.createInstance(instance))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO type is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(instanceRepository, never()).save(any(InstanceVO.class));
    }

    @Test
    void createInstanceShouldRejectManualEndpointForAliyunTest() {
        InstanceVO instance = InstanceVO.builder()
                .vendor(InstanceVendor.ALIYUN)
                .endpoint("rmq-xxx.cn-hangzhou.rmq.aliyuncs.com:8080")
                .build();

        assertThatThrownBy(() -> instanceService.createInstance(instance))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be set manually");
        verify(instanceRepository, never()).save(any(InstanceVO.class));
    }

    @Test
    void createInstanceShouldRequireCloudFieldsForAliyunTest() {
        InstanceVO instance = InstanceVO.builder().vendor(InstanceVendor.ALIYUN).build();

        assertThatThrownBy(() -> instanceService.createInstance(instance))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("credentialId");
    }

    @Test
    void createInstanceShouldResolveAliyunEndpointFromCatalogTest() {
        InstanceVO instance = InstanceVO.builder()
                .vendor(InstanceVendor.ALIYUN)
                .credentialId("cred-1")
                .cloudInstanceId("rmq-cn-xxx")
                .regionId("cn-hangzhou")
                .build();
        CloudCredentialVO credential = new CloudCredentialVO();
        credential.setId("cred-1");
        credential.setVendor(InstanceVendor.ALIYUN);
        when(cloudCredentialRepository.findById("cred-1")).thenReturn(Optional.of(credential));
        CloudCatalogProvider catalog = org.mockito.Mockito.mock(CloudCatalogProvider.class);
        CloudInstanceDetailVO detail = new CloudInstanceDetailVO();
        detail.setInstanceId("rmq-cn-xxx");
        detail.setInstanceName("prod-mq");
        detail.setEndpoints(List.of(
                new CloudInstanceDetailVO.CloudEndpoint("TCP_INTERNET", "public:8080"),
                new CloudInstanceDetailVO.CloudEndpoint("TCP_VPC", "vpc:8080")));
        when(providerRegistry.catalogFor(InstanceVendor.ALIYUN)).thenReturn(catalog);
        when(catalog.getCloudInstance("cred-1", "cn-hangzhou", "rmq-cn-xxx")).thenReturn(detail);
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InstanceVO created = instanceService.createInstance(instance);

        assertThat(created.getName()).isEqualTo("prod-mq");
        assertThat(created.getEndpoint()).isEqualTo("vpc:8080");
        assertThat(created.getType()).isEqualTo(InstanceType.PROXY);
    }

    @Test
    void createInstanceShouldResolveTencentEndpointFromCatalogTest() {
        InstanceVO instance = InstanceVO.builder()
                .vendor(InstanceVendor.TENCENT)
                .credentialId("cred-tencent")
                .cloudInstanceId("rmq-abc")
                .regionId("ap-chengdu")
                .build();
        CloudCredentialVO credential = new CloudCredentialVO();
        credential.setId("cred-tencent");
        credential.setVendor(InstanceVendor.TENCENT);
        when(cloudCredentialRepository.findById("cred-tencent")).thenReturn(Optional.of(credential));
        CloudCatalogProvider catalog = org.mockito.Mockito.mock(CloudCatalogProvider.class);
        CloudInstanceDetailVO detail = new CloudInstanceDetailVO();
        detail.setInstanceId("rmq-abc");
        detail.setInstanceName("chengdu-prod");
        detail.setEndpoints(List.of(
                new CloudInstanceDetailVO.CloudEndpoint("TCP_INTERNET", "public.tencent:8080"),
                new CloudInstanceDetailVO.CloudEndpoint("TCP_VPC", "vpc.tencent:8080")));
        when(providerRegistry.catalogFor(InstanceVendor.TENCENT)).thenReturn(catalog);
        when(catalog.getCloudInstance("cred-tencent", "ap-chengdu", "rmq-abc")).thenReturn(detail);
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InstanceVO created = instanceService.createInstance(instance);

        assertThat(created.getName()).isEqualTo("chengdu-prod");
        assertThat(created.getEndpoint()).isEqualTo("vpc.tencent:8080");
        assertThat(created.getType()).isEqualTo(InstanceType.PROXY);
        assertThat(created.getVendor()).isEqualTo(InstanceVendor.TENCENT);
    }

    @Test
    void updateInstanceShouldKeepCloudFieldsImmutableTest() {
        InstanceVO existing = InstanceVO.builder()
                .name("aliyun-inst")
                .vendor(InstanceVendor.ALIYUN)
                .cloudInstanceId("rmq-cn-xxx")
                .credentialId("cred-1")
                .regionId("cn-hangzhou")
                .type(InstanceType.PROXY)
                .endpoint("vpc:8080")
                .build();
        existing.setId("inst-1");
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(existing));
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InstanceVO request = InstanceVO.builder().endpoint("hacked:8080").remark("updated").build();
        request.setId("inst-1");
        InstanceVO updated = instanceService.updateInstance(request);

        assertThat(updated.getEndpoint()).isEqualTo("vpc:8080");
        assertThat(updated.getRemark()).isEqualTo("updated");
        assertThat(updated.getCloudInstanceId()).isEqualTo("rmq-cn-xxx");
    }
}
