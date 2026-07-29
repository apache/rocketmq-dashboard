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
package org.apache.rocketmq.studio.ops.metrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock
    private MetricsProvider metricsProvider;

    @InjectMocks
    private MetricsService metricsService;

    @Test
    void getMetricsOverviewShouldReturnData() {
        BrokerMetricsVO broker = BrokerMetricsVO.builder()
                .brokerId("broker-a-0").brokerName("broker-a")
                .address("192.168.1.10:10911").role("MASTER")
                .cpuUsage(45.2).memoryUsage(62.8)
                .diskUsage(350L * 1024 * 1024 * 1024).diskTotal(500L * 1024 * 1024 * 1024)
                .tpsIn(4200).tpsOut(4300).messageBacklog(1200)
                .putTps(4250.5).getTransferTps(4310.2)
                .dispatchMaxBuffer(0).putMessageSizeTotal(890_000_000L)
                .status("healthy").build();

        TopicMetricsVO topic = TopicMetricsVO.builder()
                .topicName("ORDER_TOPIC")
                .queueCount(16).totalMessageCount(5_200_000L).messageCountToday(120_000L)
                .putTps(1250.5).getTransferTps(1248.3)
                .maxOffset(5_200_000L).minOffset(4_900_000L).messageBacklog(300_000L)
                .lastUpdateTime("2026-07-27 10:00:00").build();

        ConsumerGroupMetricsVO group = ConsumerGroupMetricsVO.builder()
                .groupName("order-service-group")
                .consumerCount(5).totalDiff(1200).consumeTps(248.5)
                .consumeModel("CLUSTERING").consumeType("CONSUME_ACTIVELY")
                .messageModel("CLUSTERING").status("healthy")
                .lastConsumeTimestamp(System.currentTimeMillis()).build();

        SystemResourceMetricsVO sysRes = SystemResourceMetricsVO.builder()
                .cpuUsagePercent(52.3).memoryUsagePercent(65.4)
                .memoryUsedMb(8192L).memoryTotalMb(16384L)
                .diskUsagePercent(70.0).diskUsedGb(700L).diskTotalGb(1000L)
                .networkInKbps(125_000L).networkOutKbps(98_000L)
                .gcCount(156L).gcTimeMs(2340L)
                .heapUsedMb(4096L).heapMaxMb(8192L)
                .activeThreadCount(256)
                .timestamp("2026-07-27 10:00:00").build();

        MetricsOverviewVO overview = MetricsOverviewVO.builder()
                .totalTpsIn(8000).totalTpsOut(7900)
                .totalMessageCountToday(12_500_000L)
                .healthyBrokerCount(2).totalBrokerCount(3)
                .healthyConsumerGroupCount(2).totalConsumerGroupCount(4)
                .brokers(List.of(broker))
                .topTopics(List.of(topic))
                .consumerGroups(List.of(group))
                .systemResources(sysRes)
                .build();

        when(metricsProvider.getMetricsOverview()).thenReturn(overview);

        MetricsOverviewVO result = metricsService.getMetricsOverview();

        assertThat(result).isNotNull();
        assertThat(result.getTotalTpsIn()).isEqualTo(8000);
        assertThat(result.getTotalTpsOut()).isEqualTo(7900);
        assertThat(result.getTotalMessageCountToday()).isEqualTo(12_500_000L);
        assertThat(result.getHealthyBrokerCount()).isEqualTo(2);
        assertThat(result.getTotalBrokerCount()).isEqualTo(3);
        assertThat(result.getBrokers()).hasSize(1);
        assertThat(result.getBrokers().get(0).getBrokerId()).isEqualTo("broker-a-0");
        assertThat(result.getTopTopics()).hasSize(1);
        assertThat(result.getConsumerGroups()).hasSize(1);
        assertThat(result.getSystemResources()).isNotNull();
        assertThat(result.getSystemResources().getCpuUsagePercent()).isEqualTo(52.3);

        verify(metricsProvider).getMetricsOverview();
    }

    @Test
    void getBrokerMetricsShouldReturnSpecificBroker() {
        BrokerMetricsVO broker = BrokerMetricsVO.builder()
                .brokerId("broker-a-0").brokerName("broker-a")
                .address("192.168.1.10:10911").role("MASTER")
                .cpuUsage(45.2).memoryUsage(62.8)
                .status("healthy").build();

        when(metricsProvider.getBrokerMetrics("broker-a-0")).thenReturn(broker);

        BrokerMetricsVO result = metricsService.getBrokerMetrics("broker-a-0");

        assertThat(result).isNotNull();
        assertThat(result.getBrokerId()).isEqualTo("broker-a-0");
        assertThat(result.getBrokerName()).isEqualTo("broker-a");
        assertThat(result.getRole()).isEqualTo("MASTER");
        verify(metricsProvider).getBrokerMetrics("broker-a-0");
    }

    @Test
    void getBrokerMetricsShouldReturnNullForUnknownBroker() {
        when(metricsProvider.getBrokerMetrics("unknown")).thenReturn(null);

        BrokerMetricsVO result = metricsService.getBrokerMetrics("unknown");

        assertThat(result).isNull();
        verify(metricsProvider).getBrokerMetrics("unknown");
    }

    @Test
    void getTopicMetricsShouldReturnSpecificTopic() {
        TopicMetricsVO topic = TopicMetricsVO.builder()
                .topicName("ORDER_TOPIC")
                .queueCount(16).totalMessageCount(5_200_000L)
                .putTps(1250.5).getTransferTps(1248.3)
                .messageBacklog(300_000L).build();

        when(metricsProvider.getTopicMetrics("ORDER_TOPIC")).thenReturn(topic);

        TopicMetricsVO result = metricsService.getTopicMetrics("ORDER_TOPIC");

        assertThat(result).isNotNull();
        assertThat(result.getTopicName()).isEqualTo("ORDER_TOPIC");
        assertThat(result.getQueueCount()).isEqualTo(16);
        verify(metricsProvider).getTopicMetrics("ORDER_TOPIC");
    }

    @Test
    void getConsumerGroupMetricsShouldReturnSpecificGroup() {
        ConsumerGroupMetricsVO group = ConsumerGroupMetricsVO.builder()
                .groupName("order-service-group")
                .consumerCount(5).totalDiff(1200).consumeTps(248.5)
                .status("healthy").build();

        when(metricsProvider.getConsumerGroupMetrics("order-service-group")).thenReturn(group);

        ConsumerGroupMetricsVO result = metricsService.getConsumerGroupMetrics("order-service-group");

        assertThat(result).isNotNull();
        assertThat(result.getGroupName()).isEqualTo("order-service-group");
        assertThat(result.getConsumerCount()).isEqualTo(5);
        verify(metricsProvider).getConsumerGroupMetrics("order-service-group");
    }

    @Test
    void getMetricsOverviewShouldDelegateToProvider() {
        MetricsOverviewVO emptyOverview = MetricsOverviewVO.builder()
                .brokers(List.of())
                .topTopics(List.of())
                .consumerGroups(List.of())
                .build();

        when(metricsProvider.getMetricsOverview()).thenReturn(emptyOverview);

        MetricsOverviewVO result = metricsService.getMetricsOverview();

        assertThat(result).isNotNull();
        assertThat(result.getBrokers()).isEmpty();
        assertThat(result.getTopTopics()).isEmpty();
        assertThat(result.getConsumerGroups()).isEmpty();
        verify(metricsProvider).getMetricsOverview();
    }
}