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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MetricsController.class)
@AutoConfigureMockMvc(addFilters = false)
class MetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MetricsService metricsService;

    @Test
    void getMetricsOverviewShouldReturnData() throws Exception {
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
                .lastConsumeTimestamp(1722000000000L).build();

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

        when(metricsService.getMetricsOverview()).thenReturn(overview);

        mockMvc.perform(get("/api/metrics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.errMsg").isEmpty())
                .andExpect(jsonPath("$.data.totalTpsIn").value(8000))
                .andExpect(jsonPath("$.data.totalTpsOut").value(7900))
                .andExpect(jsonPath("$.data.totalMessageCountToday").value(12500000))
                .andExpect(jsonPath("$.data.healthyBrokerCount").value(2))
                .andExpect(jsonPath("$.data.totalBrokerCount").value(3))
                .andExpect(jsonPath("$.data.brokers").isArray())
                .andExpect(jsonPath("$.data.brokers[0].brokerId").value("broker-a-0"))
                .andExpect(jsonPath("$.data.brokers[0].brokerName").value("broker-a"))
                .andExpect(jsonPath("$.data.brokers[0].role").value("MASTER"))
                .andExpect(jsonPath("$.data.topTopics").isArray())
                .andExpect(jsonPath("$.data.topTopics[0].topicName").value("ORDER_TOPIC"))
                .andExpect(jsonPath("$.data.consumerGroups").isArray())
                .andExpect(jsonPath("$.data.consumerGroups[0].groupName").value("order-service-group"))
                .andExpect(jsonPath("$.data.systemResources.cpuUsagePercent").value(52.3));

        verify(metricsService).getMetricsOverview();
    }

    @Test
    void getBrokerMetricsShouldReturnSpecificBroker() throws Exception {
        BrokerMetricsVO broker = BrokerMetricsVO.builder()
                .brokerId("broker-a-0").brokerName("broker-a")
                .address("192.168.1.10:10911").role("MASTER")
                .cpuUsage(45.2).memoryUsage(62.8)
                .status("healthy").build();

        when(metricsService.getBrokerMetrics("broker-a-0")).thenReturn(broker);

        mockMvc.perform(get("/api/metrics/brokers/broker-a-0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.data.brokerId").value("broker-a-0"))
                .andExpect(jsonPath("$.data.brokerName").value("broker-a"))
                .andExpect(jsonPath("$.data.role").value("MASTER"));

        verify(metricsService).getBrokerMetrics("broker-a-0");
    }

    @Test
    void getTopicMetricsShouldReturnSpecificTopic() throws Exception {
        TopicMetricsVO topic = TopicMetricsVO.builder()
                .topicName("ORDER_TOPIC")
                .queueCount(16).totalMessageCount(5_200_000L)
                .putTps(1250.5).getTransferTps(1248.3)
                .messageBacklog(300_000L).build();

        when(metricsService.getTopicMetrics("ORDER_TOPIC")).thenReturn(topic);

        mockMvc.perform(get("/api/metrics/topics/ORDER_TOPIC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.data.topicName").value("ORDER_TOPIC"))
                .andExpect(jsonPath("$.data.queueCount").value(16));

        verify(metricsService).getTopicMetrics("ORDER_TOPIC");
    }

    @Test
    void getConsumerGroupMetricsShouldReturnSpecificGroup() throws Exception {
        ConsumerGroupMetricsVO group = ConsumerGroupMetricsVO.builder()
                .groupName("order-service-group")
                .consumerCount(5).totalDiff(1200).consumeTps(248.5)
                .status("healthy").build();

        when(metricsService.getConsumerGroupMetrics("order-service-group")).thenReturn(group);

        mockMvc.perform(get("/api/metrics/consumer-groups/order-service-group"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.data.groupName").value("order-service-group"))
                .andExpect(jsonPath("$.data.consumerCount").value(5));

        verify(metricsService).getConsumerGroupMetrics("order-service-group");
    }

    @Test
    void getMetricsOverviewShouldReturnEmptyWhenNoData() throws Exception {
        MetricsOverviewVO emptyOverview = MetricsOverviewVO.builder()
                .brokers(List.of())
                .topTopics(List.of())
                .consumerGroups(List.of())
                .build();

        when(metricsService.getMetricsOverview()).thenReturn(emptyOverview);

        mockMvc.perform(get("/api/metrics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.data.brokers").isArray())
                .andExpect(jsonPath("$.data.brokers").isEmpty())
                .andExpect(jsonPath("$.data.topTopics").isEmpty())
                .andExpect(jsonPath("$.data.consumerGroups").isEmpty());
    }
}