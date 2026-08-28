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
import org.apache.rocketmq.studio.cluster.broker.MqClientPool;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.util.RegionNames;
import org.apache.rocketmq.studio.provider.CloudCatalogProvider;
import org.apache.rocketmq.studio.provider.CloudInstanceDetailVO;
import org.apache.rocketmq.studio.provider.CloudInstanceOptionVO;
import org.apache.rocketmq.studio.provider.CloudRegionVO;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.settings.DataSourceVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private MqClientPool clientPool;

    @Mock
    private OperationAuditService operationAuditService;

    @Mock
    private SettingsRepository settingsRepository;

    @Mock
    private RegionNames regionNames;

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
    void listInstancesShouldResolveRegionDisplayNamesTest() {
        InstanceVO instance = InstanceVO.builder()
                .name("cloud-1")
                .vendor(InstanceVendor.ALIYUN)
                .regionId("cn-hangzhou")
                .build();
        instance.setId(1L);
        when(instanceRepository.findAll()).thenReturn(List.of(instance));
        when(providerRegistry.forVendor(InstanceVendor.ALIYUN)).thenReturn(instanceProvider);
        when(regionNames.resolve("cn-hangzhou")).thenReturn("Hangzhou (CN)");

        List<InstanceVO> result = instanceService.listInstances(null, null);

        assertThat(result.get(0).getRegionName()).isEqualTo("Hangzhou (CN)");
    }

    @Test
    void listInstancesShouldSortApacheFirstThenCloudVendorsAlphabeticallyTest() {
        List<InstanceVO> instances = List.of(
                InstanceVO.builder().name("z-aliyun").vendor(InstanceVendor.ALIYUN).regionId("cn-hangzhou").build(),
                InstanceVO.builder().name("b-apache").vendor(InstanceVendor.APACHE).build(),
                InstanceVO.builder().name("a-tencent").vendor(InstanceVendor.TENCENT).regionId("ap-chengdu").build(),
                InstanceVO.builder().name("a-apache").build(),
                InstanceVO.builder().name("c-aliyun").vendor(InstanceVendor.ALIYUN).regionId("cn-beijing").build(),
                InstanceVO.builder().name("a-aliyun").vendor(InstanceVendor.ALIYUN).regionId("cn-beijing").build()
        );
        when(instanceRepository.findAll()).thenReturn(instances);
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);

        List<InstanceVO> result = instanceService.listInstances(null, null);

        assertThat(result).extracting(InstanceVO::getName)
                .containsExactly("a-apache", "b-apache",
                        "a-aliyun", "c-aliyun", "z-aliyun", "a-tencent");
    }

    @Test
    void listInstancesMarksCloudCountsUnavailableWhenProviderFails() {
        InstanceVO instance = InstanceVO.builder().vendor(InstanceVendor.ALIYUN).build();
        instance.setId(1L);
        InstanceProvider provider = org.mockito.Mockito.mock(InstanceProvider.class);
        when(instanceRepository.findAll()).thenReturn(List.of(instance));
        when(providerRegistry.forVendor(InstanceVendor.ALIYUN)).thenReturn(provider);
        when(provider.countTopics("1")).thenThrow(new IllegalStateException("access denied"));

        InstanceVO result = instanceService.listInstances(null, null).get(0);

        assertThat(result.isResourceCountsAvailable()).isFalse();
    }

    @Test
    void listInstancesKeepsCloudCountsAvailableWhenProviderReturnsEmptyLists() {
        InstanceVO instance = InstanceVO.builder().vendor(InstanceVendor.ALIYUN).build();
        instance.setId(2L);
        InstanceProvider provider = org.mockito.Mockito.mock(InstanceProvider.class);
        when(instanceRepository.findAll()).thenReturn(List.of(instance));
        when(providerRegistry.forVendor(InstanceVendor.ALIYUN)).thenReturn(provider);
        when(provider.countTopics("2")).thenReturn(0);
        when(provider.countGroups("2")).thenReturn(0);

        InstanceVO result = instanceService.listInstances(null, null).get(0);

        assertThat(result.isResourceCountsAvailable()).isTrue();
        assertThat(result.getTopicCount()).isZero();
        assertThat(result.getConsumerGroupCount()).isZero();
    }

    @Test
    void listInstancesShouldFilterByType() {
        List<InstanceVO> instances = List.of(
                InstanceVO.builder().name("proxy-1").type(InstanceType.CLOUD).build()
        );
        when(instanceRepository.findByType(InstanceType.CLOUD)).thenReturn(instances);
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);

        List<InstanceVO> result = instanceService.listInstances(InstanceType.CLOUD, null);

        assertThat(result).hasSize(1);
        verify(instanceRepository).findByType(InstanceType.CLOUD);
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
                InstanceVO.builder().name("production-proxy").type(InstanceType.CLOUD).build()
        );
        when(instanceRepository.findByTypeAndSearch(InstanceType.CLOUD, "prod")).thenReturn(instances);
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);

        List<InstanceVO> result = instanceService.listInstances(InstanceType.CLOUD, "prod");

        assertThat(result).hasSize(1);
        verify(instanceRepository).findByTypeAndSearch(InstanceType.CLOUD, "prod");
    }

    @Test
    void listInstancesShouldTrimSearchKeywordWhenFilteringByType() {
        List<InstanceVO> instances = List.of(
                InstanceVO.builder().name("production-proxy").type(InstanceType.CLOUD).build()
        );
        when(instanceRepository.findByTypeAndSearch(InstanceType.CLOUD, "prod")).thenReturn(instances);
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);

        List<InstanceVO> result = instanceService.listInstances(InstanceType.CLOUD, " prod ");

        assertThat(result).hasSize(1);
        verify(instanceRepository).findByTypeAndSearch(InstanceType.CLOUD, "prod");
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
        apache.setId(3L);
        InstanceVO aliyun = InstanceVO.builder().name("aliyun").vendor(InstanceVendor.ALIYUN).build();
        aliyun.setId(4L);
        InstanceProvider aliyunProvider = org.mockito.Mockito.mock(InstanceProvider.class);
        when(instanceRepository.findAll()).thenReturn(List.of(apache, aliyun));
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);
        when(providerRegistry.forVendor(InstanceVendor.ALIYUN)).thenReturn(aliyunProvider);
        when(instanceProvider.countTopics("3")).thenReturn(3);
        when(instanceProvider.countGroups("3")).thenReturn(2);
        when(aliyunProvider.countTopics("4")).thenReturn(5);
        when(aliyunProvider.countGroups("4")).thenReturn(4);

        List<InstanceVO> result = instanceService.listInstances(null, null);

        assertThat(result.get(0).getTopicCount()).isEqualTo(3);
        assertThat(result.get(0).getConsumerGroupCount()).isEqualTo(2);
        assertThat(result.get(1).getTopicCount()).isEqualTo(5);
        assertThat(result.get(1).getConsumerGroupCount()).isEqualTo(4);
    }

    @Test
    void listInstancesShouldKeepZeroCountsWhenProviderFailsTest() {
        InstanceVO instance = InstanceVO.builder().name("broken").build();
        instance.setId(5L);
        when(instanceRepository.findAll()).thenReturn(List.of(instance));
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);
        when(instanceProvider.countTopics("5"))
                .thenThrow(new IllegalStateException("admin unavailable"));

        List<InstanceVO> result = instanceService.listInstances(null, null);

        assertThat(result.get(0).getTopicCount()).isZero();
        assertThat(result.get(0).getConsumerGroupCount()).isZero();
    }

    @Test
    void listInstancesShouldApplyASingleDeadlineToSlowCountsTest() throws InterruptedException {
        // Three hung providers: waiting per future would cost 3s each (9s total); a shared
        // deadline must bound the whole batch to roughly one timeout.
        List<InstanceVO> slow = new ArrayList<>();
        for (long id = 20L; id < 23L; id++) {
            InstanceVO vo = InstanceVO.builder().name("slow-" + id).build();
            vo.setId(id);
            slow.add(vo);
        }
        when(instanceRepository.findAll()).thenReturn(slow);
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);
        when(instanceProvider.countTopics(anyString())).thenAnswer(invocation -> {
            Thread.sleep(4_000);
            return 0;
        });

        long start = System.nanoTime();
        List<InstanceVO> result = instanceService.listInstances(null, null);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMillis)
                .as("batch must not wait one timeout per instance")
                .isLessThan(2L * InstanceService.COUNT_TIMEOUT_SECONDS * 1000);
        assertThat(result).allSatisfy(vo -> assertThat(vo.isResourceCountsAvailable()).isFalse());
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
                .type(InstanceType.PROXY_CLUSTER)
                .build();

        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> {
            InstanceVO saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(1L);
            }
            return saved;
        });

        InstanceVO result = instanceService.createInstance(input);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getGmtCreate()).isNotNull();
        assertThat(result.getGmtModified()).isNotNull();
        assertThat(result.getName()).isEqualTo("new-instance");
        assertThat(result.getType()).isEqualTo(InstanceType.PROXY_CLUSTER);
        verify(instanceRepository).save(any(InstanceVO.class));
        verify(operationAuditService).record(eq("CREATE_INSTANCE"), eq("INSTANCE"), eq("1"), eq(null),
                argThat(detail -> detail.equals("name=new-instance, vendor=APACHE, type=PROXY_CLUSTER")
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
        InstanceVO input = InstanceVO.builder().name("valid-name").type(InstanceType.PROXY_CLUSTER)
                .endpoint("  namesrv:9876  ").build();
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(instanceService.createInstance(input).getEndpoint()).isEqualTo("namesrv:9876");
    }

    @Test
    void createInstanceShouldTrimNameBeforeSaving() {
        InstanceVO input = InstanceVO.builder().name("  production  ").type(InstanceType.PROXY_CLUSTER)
                .endpoint("namesrv:9876").build();
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(instanceService.createInstance(input).getName()).isEqualTo("production");
    }

    @Test
    void createApacheInstanceShouldTrimAndPersistOnlyAdminCredentialReference() {
        InstanceVO input = InstanceVO.builder().name("production").type(InstanceType.PROXY_CLUSTER)
                .endpoint("namesrv:9876").adminCredentialRef(" production-admin ").build();
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InstanceVO saved = instanceService.createInstance(input);

        assertThat(saved.getAdminCredentialRef()).isEqualTo("production-admin");
    }

    @Test
    void updateApacheInstanceShouldReleaseCachedClientWhenCredentialReferenceChanges() {
        InstanceVO existing = InstanceVO.builder().name("production").type(InstanceType.PROXY_CLUSTER)
                .endpoint("namesrv:9876").adminCredentialRef("credential-a").build();
        existing.setId(1L);
        InstanceVO update = InstanceVO.builder().adminCredentialRef("credential-b").build();
        update.setId(1L);
        when(instanceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InstanceVO saved = instanceService.updateInstance(update);

        assertThat(saved.getAdminCredentialRef()).isEqualTo("credential-b");
        verify(adminFactory).release("namesrv:9876");
    }

    @Test
    void updateInstanceShouldMergeFieldsOntoExisting() {
        LocalDateTime originalCreatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5);
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2025, 2, 3, 4, 5, 6);
        InstanceVO existing = InstanceVO.builder()
                .name("old-name")
                .endpoint("10.0.1.1:8080")
                .type(InstanceType.PROXY_CLUSTER)
                .remark("old remark")
                .topicCount(7)
                .consumerGroupCount(3)
                .build();
        existing.setId(1L);
        existing.setGmtCreate(originalCreatedAt);
        existing.setGmtModified(originalUpdatedAt);

        InstanceVO update = InstanceVO.builder()
                .name("old-name")
                .remark("new remark")
                .build();
        update.setId(1L);

        when(instanceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(inv -> inv.getArgument(0));

        InstanceVO result = instanceService.updateInstance(update);

        assertThat(result.getName()).isEqualTo("old-name");
        assertThat(result.getEndpoint()).isEqualTo("10.0.1.1:8080");
        assertThat(result.getType()).isEqualTo(InstanceType.PROXY_CLUSTER);
        assertThat(result.getRemark()).isEqualTo("new remark");
        assertThat(result.getTopicCount()).isEqualTo(7);
        assertThat(result.getConsumerGroupCount()).isEqualTo(3);
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getGmtCreate()).isEqualTo(originalCreatedAt);
        assertThat(result.getGmtModified()).isAfter(originalUpdatedAt);
        assertThat(result).isNotSameAs(existing);
        assertThat(existing.getName()).isEqualTo("old-name");
        assertThat(existing.getRemark()).isEqualTo("old remark");
        assertThat(existing.getGmtModified()).isEqualTo(originalUpdatedAt);
        verify(operationAuditService).record(eq("UPDATE_INSTANCE"), eq("INSTANCE"), eq("1"), eq(null),
                eq("name=old-name, vendor=APACHE, type=PROXY_CLUSTER"), eq("SUCCESS"), eq(null));
    }

    @Test
    void updateInstanceShouldRejectInstanceIdChangeTest() {
        InstanceVO existing = InstanceVO.builder().name("old-name").endpoint("namesrv:9876").build();
        existing.setId(1L);
        InstanceVO update = InstanceVO.builder().name("new-name").build();
        update.setId(1L);
        when(instanceRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> instanceService.updateInstance(update))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Instance ID cannot be changed after creation");
        verify(instanceRepository, never()).save(any(InstanceVO.class));
    }

    @Test
    void updateInstanceShouldNotMutateStoredInstanceWhenSaveFails() {
        LocalDateTime originalCreatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5);
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2025, 2, 3, 4, 5, 6);
        InstanceVO stored = InstanceVO.builder()
                .name("old-name")
                .remark("old remark")
                .type(InstanceType.PROXY_CLUSTER)
                .endpoint("10.0.1.1:8080")
                .topicCount(7)
                .consumerGroupCount(3)
                .build();
        stored.setId(1L);
        stored.setGmtCreate(originalCreatedAt);
        stored.setGmtModified(originalUpdatedAt);

        InstanceVO update = InstanceVO.builder()
                .name("old-name")
                .remark("new remark")
                .type(InstanceType.DIRECT)
                .endpoint("10.0.2.2:10911")
                .build();
        update.setId(1L);

        when(instanceRepository.findById(1L)).thenReturn(Optional.of(stored));
        when(instanceRepository.save(any(InstanceVO.class)))
                .thenThrow(new IllegalStateException("storage unavailable"));

        assertThatThrownBy(() -> instanceService.updateInstance(update))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("storage unavailable");

        assertThat(stored.getName()).isEqualTo("old-name");
        assertThat(stored.getRemark()).isEqualTo("old remark");
        assertThat(stored.getType()).isEqualTo(InstanceType.PROXY_CLUSTER);
        assertThat(stored.getEndpoint()).isEqualTo("10.0.1.1:8080");
        assertThat(stored.getTopicCount()).isEqualTo(7);
        assertThat(stored.getConsumerGroupCount()).isEqualTo(3);
        assertThat(stored.getId()).isEqualTo(1L);
        assertThat(stored.getGmtCreate()).isEqualTo(originalCreatedAt);
        assertThat(stored.getGmtModified()).isEqualTo(originalUpdatedAt);
    }

    @Test
    void updateInstanceShouldReleaseUnusedPreviousEndpoint() {
        InstanceVO existing = InstanceVO.builder()
                .name("instance")
                .endpoint("old-namesrv:9876")
                .build();
        existing.setId(1L);
        InstanceVO update = InstanceVO.builder().endpoint("new-namesrv:9876").build();
        update.setId(1L);

        when(instanceRepository.findById(1L)).thenReturn(Optional.of(existing));
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
        existing.setId(1L);
        InstanceVO shared = InstanceVO.builder()
                .name("shared-instance")
                .endpoint(" shared-namesrv:9876 ")
                .build();
        shared.setId(2L);
        InstanceVO update = InstanceVO.builder().endpoint("new-namesrv:9876").build();
        update.setId(1L);

        when(instanceRepository.findById(1L)).thenReturn(Optional.of(existing));
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
        existing.setId(1L);
        InstanceVO shared = InstanceVO.builder()
                .name("shared-instance")
                .endpoint(" namesrv-a:9876, namesrv-b:9876 ")
                .build();
        shared.setId(2L);
        InstanceVO update = InstanceVO.builder().endpoint("new-namesrv:9876").build();
        update.setId(1L);

        when(instanceRepository.findById(1L)).thenReturn(Optional.of(existing));
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
        input.setId(null);

        assertThatThrownBy(() -> instanceService.updateInstance(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO ID is required");
    }

    @Test
    void updateInstanceShouldThrowWhenInstanceNotFound() {
        InstanceVO input = InstanceVO.builder().name("test").build();
        input.setId(999L);

        when(instanceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> instanceService.updateInstance(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO not found: 999");
    }

    @Test
    void updateInstanceShouldRejectBlankName() {
        InstanceVO existing = InstanceVO.builder()
                .name("existing-name")
                .endpoint("10.0.1.1:8080")
                .build();
        existing.setId(1L);
        InstanceVO update = InstanceVO.builder().name("   ").build();
        update.setId(1L);

        when(instanceRepository.findById(1L)).thenReturn(Optional.of(existing));

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
        existing.setId(1L);
        InstanceVO update = InstanceVO.builder()
                .name("existing-name")
                .endpoint("   ")
                .build();
        update.setId(1L);

        when(instanceRepository.findById(1L)).thenReturn(Optional.of(existing));

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
        existing.setId(1L);
        InstanceVO update = InstanceVO.builder().endpoint("; ;").build();
        update.setId(1L);
        when(instanceRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> instanceService.updateInstance(update))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO endpoint must not contain empty addresses");
        verify(instanceRepository, never()).save(any(InstanceVO.class));
    }

    @Test
    void deleteInstanceShouldRemoveExistingInstance() {
        InstanceVO existing = InstanceVO.builder().name("to-delete").build();
        existing.setId(1L);

        when(instanceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);
        when(instanceProvider.countTopics("1")).thenReturn(0);
        when(instanceProvider.countGroups("1")).thenReturn(0);
        when(instanceRepository.deleteById(1L)).thenReturn(true);

        instanceService.deleteInstance(1L);

        verify(instanceRepository).deleteById(1L);
        verify(operationAuditService).record(eq("DELETE_INSTANCE"), eq("INSTANCE"), eq("1"), eq(null),
                eq("name=to-delete, vendor=APACHE, type=null"), eq("SUCCESS"), eq(null));
    }

    @Test
    void deleteInstanceShouldSkipResourceCheckForCloudInstancesTest() {
        InstanceVO existing = InstanceVO.builder().name("cloud-inst").vendor(InstanceVendor.ALIYUN).build();
        existing.setId(2L);
        when(instanceRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(instanceRepository.deleteById(2L)).thenReturn(true);

        instanceService.deleteInstance(2L);

        verify(instanceRepository).deleteById(2L);
        verifyNoInteractions(providerRegistry);
    }

    @Test
    void deleteInstancesShouldDeleteAndCollectFailuresTest() {
        InstanceVO existing = InstanceVO.builder().name("inst-a").build();
        existing.setId(1L);
        when(instanceRepository.findByIdentifier("inst-a")).thenReturn(Optional.of(existing));
        when(instanceRepository.findByIdentifier("missing"))
                .thenReturn(Optional.empty());
        when(instanceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);
        when(instanceProvider.countTopics("1")).thenReturn(0);
        when(instanceProvider.countGroups("1")).thenReturn(0);
        when(instanceRepository.deleteById(1L)).thenReturn(true);

        BatchDeleteResultVO result = instanceService.deleteInstances(List.of("inst-a", "missing"));

        assertThat(result.getDeleted()).isEqualTo(1);
        assertThat(result.getFailed()).containsExactly("missing: Instance not found: missing");
    }

    @Test
    void deleteInstancesShouldContinueAfterProviderRuntimeFailureTest() {
        InstanceVO failedInstance = InstanceVO.builder().name("inst-a").build();
        failedInstance.setId(1L);
        InstanceVO deletedInstance = InstanceVO.builder().name("inst-b").build();
        deletedInstance.setId(2L);
        when(instanceRepository.findByIdentifier("inst-a")).thenReturn(Optional.of(failedInstance));
        when(instanceRepository.findByIdentifier("inst-b")).thenReturn(Optional.of(deletedInstance));
        when(instanceRepository.findById(1L)).thenReturn(Optional.of(failedInstance));
        when(instanceRepository.findById(2L)).thenReturn(Optional.of(deletedInstance));
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);
        when(instanceProvider.countTopics("1")).thenThrow(new IllegalStateException("broker unavailable"));
        when(instanceProvider.countTopics("2")).thenReturn(0);
        when(instanceProvider.countGroups("2")).thenReturn(0);
        when(instanceRepository.deleteById(2L)).thenReturn(true);

        BatchDeleteResultVO result = instanceService.deleteInstances(List.of("inst-a", "inst-b"));

        assertThat(result.getDeleted()).isEqualTo(1);
        assertThat(result.getFailed()).containsExactly("inst-a: broker unavailable");
        verify(instanceProvider).countTopics("1");
        verify(instanceRepository).findByIdentifier("inst-b");
        verify(instanceProvider).countTopics("2");
        verify(instanceProvider).countGroups("2");
        verify(instanceRepository).deleteById(2L);
    }

    @Test
    void deleteInstancesShouldBoundUnexpectedFailureMessagesTest() {
        InstanceVO existing = InstanceVO.builder().name("inst-a").build();
        existing.setId(1L);
        String oversizedMessage = "x".repeat(600);
        when(instanceRepository.findByIdentifier("inst-a")).thenReturn(Optional.of(existing));
        when(instanceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);
        when(instanceProvider.countTopics("1")).thenThrow(new IllegalStateException(oversizedMessage));

        BatchDeleteResultVO result = instanceService.deleteInstances(List.of("inst-a"));

        assertThat(result.getDeleted()).isZero();
        assertThat(result.getFailed()).singleElement()
                .isEqualTo("inst-a: " + "x".repeat(500));
    }

    @Test
    void deleteInstancesShouldRejectEmptySelectionTest() {
        assertThatThrownBy(() -> instanceService.deleteInstances(List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Instance IDs are required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }

    @Test
    void deleteInstancesShouldDeduplicateTrimmedIdentifiersTest() {
        InstanceVO existing = InstanceVO.builder().name("inst-a").build();
        existing.setId(1L);
        when(instanceRepository.findByIdentifier("inst-a")).thenReturn(Optional.of(existing));
        when(instanceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);
        when(instanceProvider.countTopics("1")).thenReturn(0);
        when(instanceProvider.countGroups("1")).thenReturn(0);
        when(instanceRepository.deleteById(1L)).thenReturn(true);

        BatchDeleteResultVO result = instanceService.deleteInstances(List.of("inst-a", " inst-a ", "inst-a"));

        assertThat(result.getDeleted()).isEqualTo(1);
        assertThat(result.getFailed()).isEmpty();
        verify(instanceRepository).deleteById(1L);
    }

    @Test
    void deleteInstancesShouldRejectAllBlankIdentifiersTest() {
        assertThatThrownBy(() -> instanceService.deleteInstances(Arrays.asList(" ", null, "")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Instance IDs are required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }

    @Test
    void deleteInstanceShouldRejectInstanceWithTopics() {
        InstanceVO existing = InstanceVO.builder()
                .name("with-topics")
                .topicCount(0)
                .build();
        existing.setId(1L);
        when(instanceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);
        when(instanceProvider.countTopics("1")).thenReturn(2);
        when(instanceProvider.countGroups("1")).thenReturn(0);

        assertThatThrownBy(() -> instanceService.deleteInstance(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cannot delete instance with managed resources: topics=2, consumerGroups=0")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(instanceRepository, never()).deleteById(1L);
    }

    @Test
    void deleteInstanceShouldRejectInstanceWithConsumerGroups() {
        InstanceVO existing = InstanceVO.builder()
                .name("with-consumer-groups")
                .consumerGroupCount(0)
                .build();
        existing.setId(1L);
        when(instanceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);
        when(instanceProvider.countTopics("1")).thenReturn(0);
        when(instanceProvider.countGroups("1")).thenReturn(3);

        assertThatThrownBy(() -> instanceService.deleteInstance(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cannot delete instance with managed resources: topics=0, consumerGroups=3")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(instanceRepository, never()).deleteById(1L);
    }

    @Test
    void deleteInstanceShouldReleaseUnusedEndpoint() {
        InstanceVO existing = InstanceVO.builder()
                .name("to-delete")
                .endpoint("namesrv:9876")
                .build();
        existing.setId(1L);
        when(instanceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(instanceRepository.findAll()).thenReturn(List.of());
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);
        when(instanceRepository.deleteById(1L)).thenReturn(true);

        instanceService.deleteInstance(1L);

        verify(instanceRepository).deleteById(1L);
        verify(adminFactory).release("namesrv:9876");
    }

    @Test
    void deleteInstanceShouldRemoveMetricsDataSourceBindingTest() {
        InstanceVO existing = InstanceVO.builder().name("to-delete").build();
        existing.setId(1L);
        DataSourceVO dataSource = DataSourceVO.builder().key("prometheus")
                .instanceIds(List.of("to-delete", "inst-2")).build();
        when(instanceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);
        when(instanceProvider.countTopics("1")).thenReturn(0);
        when(instanceProvider.countGroups("1")).thenReturn(0);
        when(instanceRepository.deleteById(1L)).thenReturn(true);
        when(settingsRepository.findAllDataSources()).thenReturn(List.of(dataSource));
        when(settingsRepository.replaceDataSource(dataSource)).thenReturn(true);

        instanceService.deleteInstance(1L);

        assertThat(dataSource.getInstanceIds()).containsExactly("inst-2");
        verify(settingsRepository).replaceDataSource(dataSource);
    }

    @Test
    void deleteInstanceShouldRejectConcurrentRemoval() {
        InstanceVO existing = InstanceVO.builder()
                .name("concurrently-removed")
                .endpoint("namesrv:9876")
                .build();
        existing.setId(1L);
        when(instanceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);
        when(instanceRepository.deleteById(1L)).thenReturn(false);

        assertThatThrownBy(() -> instanceService.deleteInstance(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO not found: 1")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(404));

        verify(adminFactory, never()).release(any());
        verifyNoInteractions(operationAuditService);
    }

    @Test
    void deleteInstanceShouldThrowWhenIdIsNull() {
        assertThatThrownBy(() -> instanceService.deleteInstance(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO ID is required");
    }

    @Test
    void deleteInstanceShouldThrowWhenIdIsBlank() {
        assertThatThrownBy(() -> instanceService.deleteInstance(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO ID is required");
    }

    @Test
    void deleteInstanceShouldThrowWhenInstanceNotFound() {
        when(instanceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> instanceService.deleteInstance(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InstanceVO not found: 999");
    }

    @Test
    void createInstanceShouldDefaultToApacheVendorTest() {
        InstanceVO instance = InstanceVO.builder()
                .name("inst")
                .endpoint("10.0.0.1:8080")
                .type(InstanceType.PROXY_CLUSTER)
                .build();
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InstanceVO created = instanceService.createInstance(instance);

        assertThat(created.getVendor()).isEqualTo(InstanceVendor.APACHE);
        assertThat(created.getType()).isEqualTo(InstanceType.PROXY_CLUSTER);
        verifyNoInteractions(cloudCredentialRepository, providerRegistry);
    }

    @Test
    void createApacheInstanceShouldPreserveExplicitProxyLocalTypeTest() {
        InstanceVO instance = InstanceVO.builder()
                .name("local-proxy")
                .endpoint("broker-proxy:8080")
                .type(InstanceType.PROXY_LOCAL)
                .build();
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InstanceVO created = instanceService.createInstance(instance);

        assertThat(created.getType()).isEqualTo(InstanceType.PROXY_LOCAL);
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
    void createApacheInstanceShouldRejectCloudTypeTest() {
        InstanceVO instance = InstanceVO.builder()
                .name("inst")
                .endpoint("10.0.0.1:8080")
                .type(InstanceType.CLOUD)
                .build();

        assertThatThrownBy(() -> instanceService.createInstance(instance))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLOUD type is reserved for vendor-managed instances")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(instanceRepository, never()).save(any(InstanceVO.class));
    }

    @Test
    void updateApacheInstanceShouldRejectCloudTypeTest() {
        InstanceVO existing = InstanceVO.builder().name("production").type(InstanceType.PROXY_CLUSTER)
                .endpoint("namesrv:9876").build();
        existing.setId(1L);
        InstanceVO update = InstanceVO.builder().type(InstanceType.CLOUD).build();
        update.setId(1L);
        when(instanceRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> instanceService.updateInstance(update))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLOUD type is reserved for vendor-managed instances")
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
    void createInstanceShouldRejectMissingCloudCatalogDetails() {
        InstanceVO instance = InstanceVO.builder()
                .vendor(InstanceVendor.ALIYUN)
                .credentialId(1L)
                .cloudInstanceId("rmq-missing")
                .regionId("cn-hangzhou")
                .build();
        CloudCredentialVO credential = new CloudCredentialVO();
        credential.setVendor(InstanceVendor.ALIYUN);
        when(cloudCredentialRepository.findById(1L)).thenReturn(Optional.of(credential));
        CloudCatalogProvider catalog = org.mockito.Mockito.mock(CloudCatalogProvider.class);
        when(providerRegistry.catalogFor(InstanceVendor.ALIYUN)).thenReturn(catalog);
        when(catalog.getCloudInstance(1L, "cn-hangzhou", "rmq-missing")).thenReturn(null);

        assertThatThrownBy(() -> instanceService.createInstance(instance))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cloud instance details unavailable: rmq-missing")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(502));
        verify(instanceRepository, never()).save(any());
    }

    @Test
    void createInstanceShouldResolveAliyunEndpointFromCatalogTest() {
        InstanceVO instance = InstanceVO.builder()
                .vendor(InstanceVendor.ALIYUN)
                .credentialId(1L)
                .cloudInstanceId("rmq-cn-xxx")
                .regionId("cn-hangzhou")
                .build();
        CloudCredentialVO credential = new CloudCredentialVO();
        credential.setId(1L);
        credential.setVendor(InstanceVendor.ALIYUN);
        when(cloudCredentialRepository.findById(1L)).thenReturn(Optional.of(credential));
        CloudCatalogProvider catalog = org.mockito.Mockito.mock(CloudCatalogProvider.class);
        CloudInstanceDetailVO detail = new CloudInstanceDetailVO();
        detail.setInstanceId("rmq-cn-xxx");
        detail.setInstanceName("prod-mq");
        detail.setEndpoints(List.of(
                new CloudInstanceDetailVO.CloudEndpoint("TCP_INTERNET", "public:8080"),
                new CloudInstanceDetailVO.CloudEndpoint("TCP_VPC", "vpc:8080")));
        when(providerRegistry.catalogFor(InstanceVendor.ALIYUN)).thenReturn(catalog);
        when(catalog.getCloudInstance(1L, "cn-hangzhou", "rmq-cn-xxx")).thenReturn(detail);
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InstanceVO created = instanceService.createInstance(instance);

        assertThat(created.getName()).isEqualTo("rmq-cn-xxx");
        assertThat(created.getEndpoint()).isEqualTo("vpc:8080");
        assertThat(created.getType()).isEqualTo(InstanceType.CLOUD);
    }

    @Test
    void createInstanceShouldAutoFillRemarkFromCloudDetailTest() {
        InstanceVO instance = InstanceVO.builder()
                .vendor(InstanceVendor.ALIYUN)
                .credentialId(1L)
                .cloudInstanceId("rmq-cn-xxx")
                .regionId("cn-hangzhou")
                .build();
        CloudCredentialVO credential = new CloudCredentialVO();
        credential.setId(1L);
        credential.setVendor(InstanceVendor.ALIYUN);
        when(cloudCredentialRepository.findById(1L)).thenReturn(Optional.of(credential));
        CloudCatalogProvider catalog = org.mockito.Mockito.mock(CloudCatalogProvider.class);
        CloudInstanceDetailVO detail = new CloudInstanceDetailVO();
        detail.setInstanceId("rmq-cn-xxx");
        detail.setInstanceName("prod-mq");
        detail.setRemark("prod-link");
        detail.setEndpoints(List.of(new CloudInstanceDetailVO.CloudEndpoint("TCP_VPC", "vpc:8080")));
        when(providerRegistry.catalogFor(InstanceVendor.ALIYUN)).thenReturn(catalog);
        when(catalog.getCloudInstance(1L, "cn-hangzhou", "rmq-cn-xxx")).thenReturn(detail);
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InstanceVO created = instanceService.createInstance(instance);

        assertThat(created.getRemark()).isEqualTo("prod-link");
    }

    @Test
    void createInstanceShouldKeepExplicitRemarkOverCloudDetailTest() {
        InstanceVO instance = InstanceVO.builder()
                .vendor(InstanceVendor.ALIYUN)
                .credentialId(1L)
                .cloudInstanceId("rmq-cn-xxx")
                .regionId("cn-hangzhou")
                .remark("user-remark")
                .build();
        CloudCredentialVO credential = new CloudCredentialVO();
        credential.setId(1L);
        credential.setVendor(InstanceVendor.ALIYUN);
        when(cloudCredentialRepository.findById(1L)).thenReturn(Optional.of(credential));
        CloudCatalogProvider catalog = org.mockito.Mockito.mock(CloudCatalogProvider.class);
        CloudInstanceDetailVO detail = new CloudInstanceDetailVO();
        detail.setInstanceId("rmq-cn-xxx");
        detail.setInstanceName("prod-mq");
        detail.setRemark("cloud-remark");
        detail.setEndpoints(List.of(new CloudInstanceDetailVO.CloudEndpoint("TCP_VPC", "vpc:8080")));
        when(providerRegistry.catalogFor(InstanceVendor.ALIYUN)).thenReturn(catalog);
        when(catalog.getCloudInstance(1L, "cn-hangzhou", "rmq-cn-xxx")).thenReturn(detail);
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InstanceVO created = instanceService.createInstance(instance);

        assertThat(created.getRemark()).isEqualTo("user-remark");
    }

    @Test
    void importCloudInstancesShouldRejectApacheVendorTest() {
        assertThatThrownBy(() -> instanceService.importCloudInstances(InstanceVendor.APACHE, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Import is only supported for cloud vendors")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verifyNoInteractions(instanceRepository);
    }

    @Test
    void importCloudInstancesShouldImportSkipAndCollectFailuresTest() {
        CloudCredentialVO credential = new CloudCredentialVO();
        credential.setId(1L);
        credential.setVendor(InstanceVendor.ALIYUN);
        when(cloudCredentialRepository.findById(1L)).thenReturn(Optional.of(credential));

        CloudCatalogProvider catalog = org.mockito.Mockito.mock(CloudCatalogProvider.class);
        when(providerRegistry.catalogFor(InstanceVendor.ALIYUN)).thenReturn(catalog);

        CloudRegionVO hangzhou = new CloudRegionVO();
        hangzhou.setRegionId("cn-hangzhou");
        CloudRegionVO broken = new CloudRegionVO();
        broken.setRegionId("cn-broken");
        when(catalog.listRegions(1L)).thenReturn(List.of(hangzhou, broken));

        CloudInstanceOptionVO fresh = new CloudInstanceOptionVO();
        fresh.setInstanceId("rmq-fresh");
        CloudInstanceOptionVO duplicate = new CloudInstanceOptionVO();
        duplicate.setInstanceId("rmq-dup");
        when(catalog.listCloudInstances(1L, "cn-hangzhou", null)).thenReturn(List.of(fresh, duplicate));
        when(catalog.listCloudInstances(1L, "cn-broken", null))
                .thenThrow(new BusinessException(502, "catalog unavailable"));

        CloudInstanceDetailVO freshDetail = new CloudInstanceDetailVO();
        freshDetail.setInstanceId("rmq-fresh");
        freshDetail.setInstanceName("inst-fresh");
        freshDetail.setRemark("cloud-remark");
        freshDetail.setEndpoints(List.of(new CloudInstanceDetailVO.CloudEndpoint("TCP_VPC", "vpc-a:8080")));
        CloudInstanceDetailVO duplicateDetail = new CloudInstanceDetailVO();
        duplicateDetail.setInstanceId("rmq-dup");
        duplicateDetail.setInstanceName("inst-dup");
        duplicateDetail.setEndpoints(List.of(new CloudInstanceDetailVO.CloudEndpoint("TCP_VPC", "vpc-b:8080")));
        when(catalog.getCloudInstance(1L, "cn-hangzhou", "rmq-fresh")).thenReturn(freshDetail);
        when(catalog.getCloudInstance(1L, "cn-hangzhou", "rmq-dup")).thenReturn(duplicateDetail);

        when(instanceRepository.findByName("rmq-fresh")).thenReturn(Optional.empty());
        InstanceVO existing = InstanceVO.builder().name("rmq-dup").build();
        existing.setId(9L);
        when(instanceRepository.findByName("rmq-dup")).thenReturn(Optional.of(existing));
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CloudImportResultVO result = instanceService.importCloudInstances(InstanceVendor.ALIYUN, 1L);

        assertThat(result.getDiscovered()).isEqualTo(2);
        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getFailed()).containsExactly("cn-broken: catalog unavailable");

        ArgumentCaptor<InstanceVO> saved = ArgumentCaptor.forClass(InstanceVO.class);
        verify(instanceRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("rmq-fresh");
        assertThat(saved.getValue().getRemark()).isEqualTo("cloud-remark");
        assertThat(saved.getValue().getType()).isEqualTo(InstanceType.CLOUD);
        assertThat(saved.getValue().getRegionId()).isEqualTo("cn-hangzhou");
    }

    @Test
    void importCloudInstancesShouldContinueAfterUnexpectedRegionFailureTest() {
        CloudCatalogProvider catalog = prepareAliyunCatalog();
        CloudRegionVO broken = new CloudRegionVO("cn-broken", "Broken");
        CloudRegionVO working = new CloudRegionVO("  cn-working  ", "Working");
        when(catalog.listRegions(1L)).thenReturn(List.of(broken, working));
        when(catalog.listCloudInstances(1L, "cn-broken", null))
                .thenThrow(new IllegalStateException("regional outage"));

        CloudInstanceOptionVO option = new CloudInstanceOptionVO();
        option.setInstanceId("  rmq-working  ");
        when(catalog.listCloudInstances(1L, "cn-working", null)).thenReturn(List.of(option));
        when(catalog.getCloudInstance(1L, "cn-working", "rmq-working"))
                .thenReturn(cloudDetail("rmq-working", "vpc-working:8080"));
        when(instanceRepository.findByName("rmq-working")).thenReturn(Optional.empty());
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CloudImportResultVO result = instanceService.importCloudInstances(InstanceVendor.ALIYUN, 1L);

        assertThat(result.getDiscovered()).isEqualTo(1);
        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getSkipped()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.isFailureDetailsTruncated()).isFalse();
        assertThat(result.getFailed()).containsExactly("cn-broken: regional outage");
        verify(catalog).listCloudInstances(1L, "cn-working", null);
        verify(catalog).getCloudInstance(1L, "cn-working", "rmq-working");
        verify(operationAuditService).record(eq("IMPORT_CLOUD_INSTANCES"), eq("INSTANCE"), eq("1"), eq(null),
                argThat(detail -> detail.contains("imported=1") && detail.contains("failed=1")),
                eq("SUCCESS"), eq(null));
    }

    @Test
    void importCloudInstancesShouldContinueAfterUnexpectedInstanceFailuresTest() {
        CloudCatalogProvider catalog = prepareAliyunCatalog();
        CloudRegionVO region = new CloudRegionVO("cn-hangzhou", "Hangzhou");
        when(catalog.listRegions(1L)).thenReturn(List.of(region));

        CloudInstanceOptionVO detailFailure = cloudOption("rmq-detail-failure");
        CloudInstanceOptionVO saveFailure = cloudOption("rmq-save-failure");
        CloudInstanceOptionVO successful = cloudOption("rmq-success");
        when(catalog.listCloudInstances(1L, "cn-hangzhou", null))
                .thenReturn(List.of(detailFailure, saveFailure, successful));
        when(catalog.getCloudInstance(1L, "cn-hangzhou", "rmq-detail-failure"))
                .thenThrow(new IllegalStateException("detail lookup failed"));
        when(catalog.getCloudInstance(1L, "cn-hangzhou", "rmq-save-failure"))
                .thenReturn(cloudDetail("rmq-save-failure", "vpc-save:8080"));
        when(catalog.getCloudInstance(1L, "cn-hangzhou", "rmq-success"))
                .thenReturn(cloudDetail("rmq-success", "vpc-success:8080"));
        when(instanceRepository.findByName(anyString())).thenReturn(Optional.empty());
        String longFailure = "persistence failure ".repeat(60);
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> {
            InstanceVO instance = invocation.getArgument(0);
            if ("rmq-save-failure".equals(instance.getName())) {
                throw new IllegalStateException(longFailure);
            }
            return instance;
        });

        CloudImportResultVO result = instanceService.importCloudInstances(InstanceVendor.ALIYUN, 1L);

        assertThat(result.getDiscovered()).isEqualTo(3);
        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getSkipped()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(2);
        assertThat(result.getFailed()).hasSize(2)
                .contains("rmq-detail-failure: detail lookup failed")
                .anySatisfy(failure -> assertThat(failure)
                        .startsWith("rmq-save-failure: ")
                        .endsWith("…")
                        .hasSizeLessThan(550));
        verify(catalog).getCloudInstance(1L, "cn-hangzhou", "rmq-detail-failure");
        verify(catalog).getCloudInstance(1L, "cn-hangzhou", "rmq-save-failure");
        verify(catalog).getCloudInstance(1L, "cn-hangzhou", "rmq-success");
        verify(instanceRepository, times(2)).save(any(InstanceVO.class));
    }

    @Test
    void importCloudInstancesShouldHandleNullCatalogResponsesTest() {
        CloudCatalogProvider catalog = prepareAliyunCatalog();
        when(catalog.listRegions(1L)).thenReturn(List.of(new CloudRegionVO("cn-empty", "Empty")));
        when(catalog.listCloudInstances(1L, "cn-empty", null)).thenReturn(null);

        CloudImportResultVO result = instanceService.importCloudInstances(InstanceVendor.ALIYUN, 1L);

        assertThat(result.getDiscovered()).isZero();
        assertThat(result.getImported()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getFailed()).containsExactly("cn-empty: catalog returned a null instance list");
        assertThat(result.isFailureDetailsTruncated()).isFalse();
        verify(catalog).listCloudInstances(1L, "cn-empty", null);
        verifyNoInteractions(instanceRepository);
    }

    @Test
    void importCloudInstancesShouldBoundMalformedCatalogFailuresTest() {
        CloudCatalogProvider catalog = prepareAliyunCatalog();
        CloudRegionVO blank = new CloudRegionVO("  ", "Blank");
        List<CloudRegionVO> regions = new ArrayList<>();
        regions.add(null);
        regions.add(blank);
        regions.add(new CloudRegionVO("cn-malformed", "Malformed"));
        when(catalog.listRegions(1L)).thenReturn(regions);

        List<CloudInstanceOptionVO> options = new ArrayList<>();
        for (int i = 0; i < 105; i++) {
            options.add(cloudOption(" "));
        }
        when(catalog.listCloudInstances(1L, "cn-malformed", null)).thenReturn(options);

        CloudImportResultVO result = instanceService.importCloudInstances(InstanceVendor.ALIYUN, 1L);

        assertThat(result.getDiscovered()).isZero();
        assertThat(result.getImported()).isZero();
        assertThat(result.getSkipped()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(107);
        assertThat(result.getFailed()).hasSize(InstanceService.MAX_CLOUD_IMPORT_FAILURE_DETAILS);
        assertThat(result.isFailureDetailsTruncated()).isTrue();
        assertThat(result.getFailed()).first().isEqualTo("region: catalog returned an invalid region entry");
        verify(catalog).listCloudInstances(1L, "cn-malformed", null);
        verifyNoInteractions(instanceRepository);
    }

    @Test
    void importCloudInstancesShouldReturnPartialResultWhenRegionDiscoveryFailsTest() {
        CloudCatalogProvider catalog = prepareAliyunCatalog();
        when(catalog.listRegions(1L)).thenThrow(new IllegalStateException("region discovery unavailable"));

        CloudImportResultVO result = instanceService.importCloudInstances(InstanceVendor.ALIYUN, 1L);

        assertThat(result.getDiscovered()).isZero();
        assertThat(result.getImported()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getFailed()).containsExactly("regions: region discovery unavailable");
        verify(catalog, never()).listCloudInstances(any(Long.class), anyString(), any());
        verifyNoInteractions(instanceRepository);
    }

    @Test
    void importCloudInstancesShouldReportMissingCatalogProviderTest() {
        CloudCredentialVO credential = new CloudCredentialVO();
        credential.setId(1L);
        credential.setVendor(InstanceVendor.ALIYUN);
        when(cloudCredentialRepository.findById(1L)).thenReturn(Optional.of(credential));
        when(providerRegistry.catalogFor(InstanceVendor.ALIYUN)).thenReturn(null);

        CloudImportResultVO result = instanceService.importCloudInstances(InstanceVendor.ALIYUN, 1L);

        assertThat(result.getDiscovered()).isZero();
        assertThat(result.getImported()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getFailed()).containsExactly("catalog: provider returned no cloud catalog");
        verifyNoInteractions(instanceRepository);
    }

    @Test
    void importCloudInstancesShouldDeduplicateCatalogRowsTest() {
        CloudCatalogProvider catalog = prepareAliyunCatalog();
        when(catalog.listRegions(1L)).thenReturn(List.of(new CloudRegionVO("cn-hangzhou", "Hangzhou")));
        when(catalog.listCloudInstances(1L, "cn-hangzhou", null)).thenReturn(
                List.of(cloudOption(" rmq-duplicate "), cloudOption("rmq-duplicate")));
        when(catalog.getCloudInstance(1L, "cn-hangzhou", "rmq-duplicate"))
                .thenReturn(cloudDetail("rmq-duplicate", "vpc-duplicate:8080"));
        when(instanceRepository.findByName("rmq-duplicate")).thenReturn(Optional.empty());
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CloudImportResultVO result = instanceService.importCloudInstances(InstanceVendor.ALIYUN, 1L);

        assertThat(result.getDiscovered()).isEqualTo(1);
        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getFailedCount()).isZero();
        verify(catalog, times(1)).getCloudInstance(1L, "cn-hangzhou", "rmq-duplicate");
        verify(instanceRepository, times(1)).save(any(InstanceVO.class));
    }

    @Test
    void createInstanceShouldSkipNullCloudEndpointEntries() {
        InstanceVO instance = InstanceVO.builder()
                .vendor(InstanceVendor.ALIYUN)
                .credentialId(1L)
                .cloudInstanceId("rmq-cn-xxx")
                .regionId("cn-hangzhou")
                .build();
        CloudCredentialVO credential = new CloudCredentialVO();
        credential.setVendor(InstanceVendor.ALIYUN);
        when(cloudCredentialRepository.findById(1L)).thenReturn(Optional.of(credential));
        CloudCatalogProvider catalog = org.mockito.Mockito.mock(CloudCatalogProvider.class);
        CloudInstanceDetailVO detail = new CloudInstanceDetailVO();
        detail.setInstanceId("rmq-cn-xxx");
        detail.setInstanceName("prod-mq");
        detail.setEndpoints(Arrays.asList(null,
                new CloudInstanceDetailVO.CloudEndpoint("TCP_VPC", "vpc:8080")));
        when(providerRegistry.catalogFor(InstanceVendor.ALIYUN)).thenReturn(catalog);
        when(catalog.getCloudInstance(1L, "cn-hangzhou", "rmq-cn-xxx")).thenReturn(detail);
        when(instanceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(instanceService.createInstance(instance).getEndpoint()).isEqualTo("vpc:8080");
    }

    @Test
    void createInstanceShouldPrioritizeEndpointsIndependentlyOfDefaultLocaleTest() {
        InstanceVO instance = InstanceVO.builder()
                .vendor(InstanceVendor.ALIYUN)
                .credentialId(1L)
                .cloudInstanceId("rmq-cn-xxx")
                .regionId("cn-hangzhou")
                .build();
        CloudCredentialVO credential = new CloudCredentialVO();
        credential.setId(1L);
        credential.setVendor(InstanceVendor.ALIYUN);
        when(cloudCredentialRepository.findById(1L)).thenReturn(Optional.of(credential));
        CloudCatalogProvider catalog = org.mockito.Mockito.mock(CloudCatalogProvider.class);
        CloudInstanceDetailVO detail = new CloudInstanceDetailVO();
        detail.setInstanceId("rmq-cn-xxx");
        detail.setInstanceName("prod-mq");
        detail.setEndpoints(List.of(
                new CloudInstanceDetailVO.CloudEndpoint("unknown", "fallback:8080"),
                new CloudInstanceDetailVO.CloudEndpoint("tcp_internet", "public:8080")));
        when(providerRegistry.catalogFor(InstanceVendor.ALIYUN)).thenReturn(catalog);
        when(catalog.getCloudInstance(1L, "cn-hangzhou", "rmq-cn-xxx")).thenReturn(detail);
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Locale originalLocale = Locale.getDefault();

        InstanceVO created;
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            created = instanceService.createInstance(instance);
        } finally {
            Locale.setDefault(originalLocale);
        }

        assertThat(created.getEndpoint()).isEqualTo("public:8080");
    }

    @Test
    void createInstanceShouldResolveTencentEndpointFromCatalogTest() {
        InstanceVO instance = InstanceVO.builder()
                .vendor(InstanceVendor.TENCENT)
                .credentialId(2L)
                .cloudInstanceId("rmq-abc")
                .regionId("ap-chengdu")
                .build();
        CloudCredentialVO credential = new CloudCredentialVO();
        credential.setId(2L);
        credential.setVendor(InstanceVendor.TENCENT);
        when(cloudCredentialRepository.findById(2L)).thenReturn(Optional.of(credential));
        CloudCatalogProvider catalog = org.mockito.Mockito.mock(CloudCatalogProvider.class);
        CloudInstanceDetailVO detail = new CloudInstanceDetailVO();
        detail.setInstanceId("rmq-abc");
        detail.setInstanceName("chengdu-prod");
        detail.setEndpoints(List.of(
                new CloudInstanceDetailVO.CloudEndpoint("TCP_INTERNET", "public.tencent:8080"),
                new CloudInstanceDetailVO.CloudEndpoint("TCP_VPC", "vpc.tencent:8080")));
        when(providerRegistry.catalogFor(InstanceVendor.TENCENT)).thenReturn(catalog);
        when(catalog.getCloudInstance(2L, "ap-chengdu", "rmq-abc")).thenReturn(detail);
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InstanceVO created = instanceService.createInstance(instance);

        assertThat(created.getName()).isEqualTo("rmq-abc");
        assertThat(created.getEndpoint()).isEqualTo("vpc.tencent:8080");
        assertThat(created.getType()).isEqualTo(InstanceType.CLOUD);
        assertThat(created.getVendor()).isEqualTo(InstanceVendor.TENCENT);
    }

    @Test
    void updateInstanceShouldKeepCloudFieldsImmutableTest() {
        InstanceVO existing = InstanceVO.builder()
                .name("aliyun-inst")
                .vendor(InstanceVendor.ALIYUN)
                .cloudInstanceId("rmq-cn-xxx")
                .credentialId(1L)
                .regionId("cn-hangzhou")
                .type(InstanceType.CLOUD)
                .endpoint("vpc:8080")
                .build();
        existing.setId(1L);
        when(instanceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(instanceRepository.save(any(InstanceVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InstanceVO request = InstanceVO.builder().endpoint("hacked:8080").remark("updated").build();
        request.setId(1L);
        InstanceVO updated = instanceService.updateInstance(request);

        assertThat(updated.getEndpoint()).isEqualTo("vpc:8080");
        assertThat(updated.getRemark()).isEqualTo("updated");
        assertThat(updated.getCloudInstanceId()).isEqualTo("rmq-cn-xxx");
    }

    private CloudCatalogProvider prepareAliyunCatalog() {
        CloudCredentialVO credential = new CloudCredentialVO();
        credential.setId(1L);
        credential.setVendor(InstanceVendor.ALIYUN);
        when(cloudCredentialRepository.findById(1L)).thenReturn(Optional.of(credential));
        CloudCatalogProvider catalog = org.mockito.Mockito.mock(CloudCatalogProvider.class);
        when(providerRegistry.catalogFor(InstanceVendor.ALIYUN)).thenReturn(catalog);
        return catalog;
    }

    private CloudInstanceOptionVO cloudOption(String instanceId) {
        CloudInstanceOptionVO option = new CloudInstanceOptionVO();
        option.setInstanceId(instanceId);
        return option;
    }

    private CloudInstanceDetailVO cloudDetail(String instanceId, String endpoint) {
        CloudInstanceDetailVO detail = new CloudInstanceDetailVO();
        detail.setInstanceId(instanceId);
        detail.setInstanceName(instanceId + "-name");
        detail.setEndpoints(List.of(new CloudInstanceDetailVO.CloudEndpoint("TCP_VPC", endpoint)));
        return detail;
    }
}
