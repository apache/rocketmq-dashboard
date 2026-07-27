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
package com.rocketmq.studio.ops.metrics;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Component
public class MetricsProviderStub implements MetricsProvider {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public MetricsOverviewVO getMetricsOverview() {
        List<BrokerMetricsVO> brokers = Arrays.asList(
                BrokerMetricsVO.builder()
                        .brokerId("broker-a-0").brokerName("broker-a")
                        .address("192.168.1.10:10911").role("MASTER")
                        .cpuUsage(45.2).memoryUsage(62.8)
                        .diskUsage(350L * 1024 * 1024 * 1024).diskTotal(500L * 1024 * 1024 * 1024)
                        .tpsIn(4200).tpsOut(4300).messageBacklog(1200)
                        .putTps(4250.5).getTransferTps(4310.2)
                        .dispatchMaxBuffer(0).putMessageSizeTotal(890_000_000L)
                        .status("healthy").build(),
                BrokerMetricsVO.builder()
                        .brokerId("broker-a-1").brokerName("broker-a")
                        .address("192.168.1.11:10911").role("SLAVE")
                        .cpuUsage(32.1).memoryUsage(48.5)
                        .diskUsage(280L * 1024 * 1024 * 1024).diskTotal(500L * 1024 * 1024 * 1024)
                        .tpsIn(0).tpsOut(2100).messageBacklog(0)
                        .putTps(0).getTransferTps(2150.3)
                        .dispatchMaxBuffer(0).putMessageSizeTotal(0L)
                        .status("healthy").build(),
                BrokerMetricsVO.builder()
                        .brokerId("broker-b-0").brokerName("broker-b")
                        .address("192.168.1.20:10911").role("MASTER")
                        .cpuUsage(78.5).memoryUsage(85.3)
                        .diskUsage(420L * 1024 * 1024 * 1024).diskTotal(500L * 1024 * 1024 * 1024)
                        .tpsIn(3800).tpsOut(3600).messageBacklog(5600)
                        .putTps(3850.1).getTransferTps(3620.8)
                        .dispatchMaxBuffer(1024).putMessageSizeTotal(760_000_000L)
                        .status("warning").build()
        );

        List<TopicMetricsVO> topTopics = Arrays.asList(
                TopicMetricsVO.builder().topicName("ORDER_TOPIC")
                        .queueCount(16).totalMessageCount(5_200_000L).messageCountToday(120_000L)
                        .putTps(1250.5).getTransferTps(1248.3)
                        .maxOffset(5_200_000L).minOffset(4_900_000L).messageBacklog(300_000L)
                        .lastUpdateTime(LocalDateTime.now().format(FORMATTER)).build(),
                TopicMetricsVO.builder().topicName("TRADE_TOPIC")
                        .queueCount(8).totalMessageCount(3_800_000L).messageCountToday(85_000L)
                        .putTps(890.2).getTransferTps(885.7)
                        .maxOffset(3_800_000L).minOffset(3_600_000L).messageBacklog(200_000L)
                        .lastUpdateTime(LocalDateTime.now().format(FORMATTER)).build(),
                TopicMetricsVO.builder().topicName("NOTIFICATION_TOPIC")
                        .queueCount(4).totalMessageCount(1_500_000L).messageCountToday(45_000L)
                        .putTps(520.8).getTransferTps(518.1)
                        .maxOffset(1_500_000L).minOffset(1_400_000L).messageBacklog(100_000L)
                        .lastUpdateTime(LocalDateTime.now().format(FORMATTER)).build(),
                TopicMetricsVO.builder().topicName("LOG_TOPIC")
                        .queueCount(8).totalMessageCount(12_000_000L).messageCountToday(350_000L)
                        .putTps(3200.0).getTransferTps(3180.5)
                        .maxOffset(12_000_000L).minOffset(11_500_000L).messageBacklog(500_000L)
                        .lastUpdateTime(LocalDateTime.now().format(FORMATTER)).build(),
                TopicMetricsVO.builder().topicName("DLQ_TOPIC")
                        .queueCount(4).totalMessageCount(50_000L).messageCountToday(1_200L)
                        .putTps(15.3).getTransferTps(12.1)
                        .maxOffset(50_000L).minOffset(48_000L).messageBacklog(2_000L)
                        .lastUpdateTime(LocalDateTime.now().format(FORMATTER)).build()
        );

        List<ConsumerGroupMetricsVO> consumerGroups = Arrays.asList(
                ConsumerGroupMetricsVO.builder().groupName("order-service-group")
                        .consumerCount(5).totalDiff(1200).consumeTps(248.5)
                        .consumeModel("CLUSTERING").consumeType("CONSUME_ACTIVELY")
                        .messageModel("BROADCASTING").status("healthy")
                        .lastConsumeTimestamp(System.currentTimeMillis()).build(),
                ConsumerGroupMetricsVO.builder().groupName("trade-service-group")
                        .consumerCount(3).totalDiff(3500).consumeTps(185.2)
                        .consumeModel("CLUSTERING").consumeType("CONSUME_ACTIVELY")
                        .messageModel("CLUSTERING").status("warning")
                        .lastConsumeTimestamp(System.currentTimeMillis() - 60000).build(),
                ConsumerGroupMetricsVO.builder().groupName("notification-service-group")
                        .consumerCount(2).totalDiff(0).consumeTps(98.7)
                        .consumeModel("CLUSTERING").consumeType("CONSUME_ACTIVELY")
                        .messageModel("CLUSTERING").status("healthy")
                        .lastConsumeTimestamp(System.currentTimeMillis()).build(),
                ConsumerGroupMetricsVO.builder().groupName("log-processor-group")
                        .consumerCount(8).totalDiff(15000).consumeTps(580.3)
                        .consumeModel("CLUSTERING").consumeType("CONSUME_ACTIVELY")
                        .messageModel("CLUSTERING").status("critical")
                        .lastConsumeTimestamp(System.currentTimeMillis() - 300000).build()
        );

        SystemResourceMetricsVO systemResources = SystemResourceMetricsVO.builder()
                .cpuUsagePercent(52.3)
                .memoryUsagePercent(65.4)
                .memoryUsedMb(8192L)
                .memoryTotalMb(16384L)
                .diskUsagePercent(70.0)
                .diskUsedGb(700L)
                .diskTotalGb(1000L)
                .networkInKbps(125_000L)
                .networkOutKbps(98_000L)
                .gcCount(156L)
                .gcTimeMs(2340L)
                .heapUsedMb(4096L)
                .heapMaxMb(8192L)
                .activeThreadCount(256)
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();

        return MetricsOverviewVO.builder()
                .totalTpsIn(8000)
                .totalTpsOut(7900)
                .totalMessageCountToday(12_500_000L)
                .healthyBrokerCount(2)
                .totalBrokerCount(3)
                .healthyConsumerGroupCount(2)
                .totalConsumerGroupCount(4)
                .brokers(brokers)
                .topTopics(topTopics)
                .consumerGroups(consumerGroups)
                .systemResources(systemResources)
                .build();
    }

    @Override
    public BrokerMetricsVO getBrokerMetrics(String brokerId) {
        return getMetricsOverview().getBrokers().stream()
                .filter(b -> b.getBrokerId().equals(brokerId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public TopicMetricsVO getTopicMetrics(String topicName) {
        return getMetricsOverview().getTopTopics().stream()
                .filter(t -> t.getTopicName().equals(topicName))
                .findFirst()
                .orElse(null);
    }

    @Override
    public ConsumerGroupMetricsVO getConsumerGroupMetrics(String groupName) {
        return getMetricsOverview().getConsumerGroups().stream()
                .filter(g -> g.getGroupName().equals(groupName))
                .findFirst()
                .orElse(null);
    }
}