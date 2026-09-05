/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NativeAlertMetricCatalogServiceTest {
    @Test
    void returnsOnlyMetricsBackedByTheSelectedProviderTest() {
        InstanceRepository repository = mock(InstanceRepository.class);
        when(repository.findByIdentifier("apache")).thenReturn(Optional.of(InstanceVO.builder()
                .name("apache").vendor(InstanceVendor.APACHE).build()));
        when(repository.findByIdentifier("aliyun")).thenReturn(Optional.of(InstanceVO.builder()
                .name("aliyun").vendor(InstanceVendor.ALIYUN).build()));
        NativeAlertMetricCatalogService service = new NativeAlertMetricCatalogService(repository);

        assertThat(service.list("apache", AlertDomain.BUSINESS)).extracting(NativeAlertMetricInfo::key)
                .containsExactly("consumer.lag.total", "consumer.lag.max_queue", "consumer.delay.seconds",
                        "topic.backlog.total", "dlq.message.count");
        assertThat(service.list("apache", AlertDomain.CLUSTER)).extracting(NativeAlertMetricInfo::key)
                .contains("broker.jvm.heap.usage_ratio", "broker.send_queue.usage_ratio", "proxy.availability");
        assertThat(service.list("aliyun", AlertDomain.BUSINESS)).extracting(NativeAlertMetricInfo::key)
                .containsExactly("consumer.lag.total", "consumer.lag.max_queue", "topic.backlog.total");
        assertThat(service.list("aliyun", AlertDomain.CLUSTER)).extracting(NativeAlertMetricInfo::key)
                .containsExactly("cloud.instance.availability");
        assertThatThrownBy(() -> service.validate(AlertRuleVO.builder().domain(AlertDomain.CLUSTER)
                .instanceId("aliyun").metric("broker.availability").build()))
                .hasMessageContaining("not supported");
    }

    @Test
    void normalizesMetricKeysBeforeNativeAndCustomValidationTest() {
        InstanceRepository repository = mock(InstanceRepository.class);
        when(repository.findByIdentifier("apache")).thenReturn(Optional.of(InstanceVO.builder()
                .name("apache").vendor(InstanceVendor.APACHE).build()));
        NativeAlertMetricCatalogService service = new NativeAlertMetricCatalogService(repository);
        AlertRuleVO nativeRule = AlertRuleVO.builder().domain(AlertDomain.CLUSTER)
                .instanceId("apache").metric(" broker.disk.usage_ratio ").build();
        AlertRuleVO customRule = AlertRuleVO.builder().domain(AlertDomain.CLUSTER)
                .instanceId("apache").metric(" custom.metric ").build();

        service.validate(nativeRule);
        service.validate(customRule);

        assertThat(nativeRule.getMetric()).isEqualTo("broker.disk.usage_ratio");
        assertThat(customRule.getMetric()).isEqualTo("custom.metric");
    }

    @Test
    void listRejectsBlankAndUnknownInstanceIdsTest() {
        InstanceRepository repository = mock(InstanceRepository.class);
        when(repository.findByIdentifier("ghost")).thenReturn(Optional.empty());
        NativeAlertMetricCatalogService service = new NativeAlertMetricCatalogService(repository);

        assertThatThrownBy(() -> service.list("  ", AlertDomain.BUSINESS))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("instanceId is required");
        assertThatThrownBy(() -> service.list("ghost", AlertDomain.BUSINESS))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Instance not found");
    }

    @Test
    void tencentVendorReturnsTheCloudMetricSetsTest() {
        InstanceRepository repository = mock(InstanceRepository.class);
        when(repository.findByIdentifier("tencent")).thenReturn(Optional.of(InstanceVO.builder()
                .name("tencent").vendor(InstanceVendor.TENCENT).build()));
        NativeAlertMetricCatalogService service = new NativeAlertMetricCatalogService(repository);

        assertThat(service.list("tencent", AlertDomain.BUSINESS)).extracting(NativeAlertMetricInfo::key)
                .containsExactly("consumer.lag.total", "consumer.lag.max_queue", "topic.backlog.total");
        assertThat(service.list("tencent", AlertDomain.CLUSTER)).extracting(NativeAlertMetricInfo::key)
                .containsExactly("cloud.instance.availability");
    }

    @Test
    void missingVendorFallsBackToTheApacheSetsTest() {
        InstanceRepository repository = mock(InstanceRepository.class);
        when(repository.findByIdentifier("local")).thenReturn(Optional.of(InstanceVO.builder()
                .name("local").vendor(null).build()));
        NativeAlertMetricCatalogService service = new NativeAlertMetricCatalogService(repository);

        assertThat(service.list("local", AlertDomain.BUSINESS)).extracting(NativeAlertMetricInfo::key)
                .contains("consumer.lag.total", "dlq.message.count");
        assertThat(service.list("local", AlertDomain.CLUSTER)).extracting(NativeAlertMetricInfo::key)
                .contains("broker.availability", "proxy.availability");
    }

    @Test
    void validateAcceptsSupportedNativeMetricsAndNullRulesTest() {
        InstanceRepository repository = mock(InstanceRepository.class);
        when(repository.findByIdentifier("apache")).thenReturn(Optional.of(InstanceVO.builder()
                .name("apache").vendor(InstanceVendor.APACHE).build()));
        NativeAlertMetricCatalogService service = new NativeAlertMetricCatalogService(repository);

        service.validate(null);
        assertThatCode(() -> service.validate(AlertRuleVO.builder().domain(AlertDomain.CLUSTER)
                .instanceId("apache").metric("broker.availability").build()))
                .doesNotThrowAnyException();
    }
}
