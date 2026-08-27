/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
}
