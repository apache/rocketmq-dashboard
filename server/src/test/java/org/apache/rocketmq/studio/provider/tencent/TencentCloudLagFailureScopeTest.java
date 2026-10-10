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
package org.apache.rocketmq.studio.provider.tencent;

import com.tencentcloudapi.trocket.v20230308.TrocketClient;
import com.tencentcloudapi.trocket.v20230308.models.ConsumeGroupItem;
import com.tencentcloudapi.trocket.v20230308.models.DescribeConsumerGroupListResponse;
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicListByGroupResponse;
import com.tencentcloudapi.trocket.v20230308.models.SubscriptionData;
import org.apache.rocketmq.studio.cluster.metrics.MetricAvailability;
import org.apache.rocketmq.studio.cluster.metrics.collectors.CloudRocketMqBusinessMetricsCollector;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.alert.AlertDomain;
import org.apache.rocketmq.studio.ops.alert.AlertRuleEvaluator;
import org.apache.rocketmq.studio.ops.alert.AlertRuleTestResultVO;
import org.apache.rocketmq.studio.ops.alert.AlertRuleVO;
import org.apache.rocketmq.studio.ops.alert.NativeAlertRuleTestService;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TencentCloudLagFailureScopeTest {
    @ParameterizedTest
    @ValueSource(strings = {"consumer.lag.total", "consumer.lag.max_queue", "topic.backlog.total"})
    void clusterScopedRuleTestReportsTheKnownGroupOutageTest(String metric) throws Exception {
        NativeAlertRuleTestService service = serviceWithFailedProgress("legacy-cluster");

        AlertRuleTestResultVO result = service.test(rule(metric, "legacy-cluster"));

        assertThat(result.samples()).singleElement().satisfies(sample -> {
            assertThat(sample.availability()).isEqualTo("UNAVAILABLE");
            assertThat(sample.currentValue()).isNull();
            assertThat(sample.conditionMet()).isFalse();
            assertThat(sample.labels()).containsEntry("consumerGroup", "orders");
        });
    }

    @Test
    void failureDoesNotAppearInAnotherClustersRuleTest() throws Exception {
        assertThat(serviceWithFailedProgress("legacy-cluster")
                .test(rule("consumer.lag.total", "other-cluster")).samples()).isEmpty();
    }

    @Test
    void unscopedRuleStillReportsFailureWithoutAProviderClusterIdTest() throws Exception {
        assertThat(serviceWithFailedProgress(null).test(rule("consumer.lag.total", null)).samples())
                .singleElement().satisfies(sample -> assertThat(sample.availability()).isEqualTo("UNAVAILABLE"));
    }

    @Test
    void successfulClusterScopedRuleStillReportsItsMeasuredLagTest() throws Exception {
        var result = serviceWithProgress("legacy-cluster", false)
                .test(rule("consumer.lag.total", "legacy-cluster"));

        assertThat(result.samples()).singleElement().satisfies(sample -> {
            assertThat(sample.availability()).isEqualTo("AVAILABLE");
            assertThat(sample.currentValue()).isEqualTo(80D);
            assertThat(sample.conditionMet()).isTrue();
        });
    }

    @Test
    void wholeInstanceFailureRemainsUnscopedTest() {
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        when(registry.byInstanceId("tencent-prod"))
                .thenThrow(new BusinessException(502, "provider unavailable"));
        InstanceVO instance = InstanceVO.builder().name("tencent-prod").vendor(InstanceVendor.TENCENT).build();

        assertThat(new CloudRocketMqBusinessMetricsCollector(registry).collect(instance))
                .hasSize(3).allSatisfy(sample -> {
                    assertThat(sample.clusterId()).isNull();
                    assertThat(sample.labels()).isEmpty();
                    assertThat(sample.value()).isNull();
                    assertThat(sample.availability()).isEqualTo(MetricAvailability.UNAVAILABLE);
                });
    }

    private static AlertRuleVO rule(String metric, String cluster) {
        return AlertRuleVO.builder().domain(AlertDomain.BUSINESS).instanceId("tencent-prod")
                .consumerGroup("orders").clusterName(cluster).metric(metric)
                .operator(">").threshold(50).consecutiveSamples(1).build();
    }

    private static NativeAlertRuleTestService serviceWithFailedProgress(String cluster) throws Exception {
        return serviceWithProgress(cluster, true);
    }

    private static NativeAlertRuleTestService serviceWithProgress(String cluster, boolean failure) throws Exception {
        InstanceRepository instances = mock(InstanceRepository.class);
        InstanceVO instance = InstanceVO.builder().name("tencent-prod").vendor(InstanceVendor.TENCENT)
                .cloudInstanceId("rmq-test").regionId("ap-chengdu").credentialId(1L).build();
        when(instances.findByIdentifier("tencent-prod")).thenReturn(Optional.of(instance));
        TrocketClient client = mock(TrocketClient.class);
        TencentClientFactory factory = mock(TencentClientFactory.class);
        when(factory.call(any(Long.class), anyString(), any())).thenAnswer(invocation ->
                invocation.<TencentClientFactory.TencentCall<Object>>getArgument(2).execute(client));
        ConsumeGroupItem item = new ConsumeGroupItem();
        item.setConsumerGroup("orders");
        // Real Tencent mapping retains the source cluster of a migrated consumer group.
        item.setClusterIdV4(cluster);
        DescribeConsumerGroupListResponse groups = new DescribeConsumerGroupListResponse();
        groups.setData(new ConsumeGroupItem[] {item});
        groups.setTotalCount(1L);
        when(client.DescribeConsumerGroupList(any())).thenReturn(groups);
        if (failure) {
            when(client.DescribeTopicListByGroup(any())).thenThrow(new BusinessException(502, "progress unavailable"));
        } else {
            SubscriptionData subscription = new SubscriptionData();
            subscription.setTopic("orders-topic");
            subscription.setConsumerLag(80L);
            DescribeTopicListByGroupResponse progress = new DescribeTopicListByGroupResponse();
            progress.setData(new SubscriptionData[] {subscription});
            progress.setTotalCount(1L);
            when(client.DescribeTopicListByGroup(any())).thenReturn(progress);
        }
        TencentInstanceProvider provider = new TencentInstanceProvider(factory, instances);
        assertThat(provider.listConsumerGroups("tencent-prod", null)).singleElement()
                .satisfies(group -> assertThat(group.getClusterId()).isEqualTo(cluster));
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        when(registry.byInstanceId("tencent-prod")).thenReturn(Optional.of(provider));
        return new NativeAlertRuleTestService(instances, List.of(),
                List.of(new CloudRocketMqBusinessMetricsCollector(registry)), new AlertRuleEvaluator());
    }
}
