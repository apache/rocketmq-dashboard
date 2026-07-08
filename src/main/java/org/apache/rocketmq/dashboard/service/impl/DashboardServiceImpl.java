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

package org.apache.rocketmq.dashboard.service.impl;

import com.google.common.collect.Lists;
import jakarta.annotation.Resource;
import org.apache.rocketmq.dashboard.service.ClusterService;
import org.apache.rocketmq.dashboard.service.ConsumerService;
import org.apache.rocketmq.dashboard.service.DashboardCollectService;
import org.apache.rocketmq.dashboard.service.DashboardService;
import org.apache.rocketmq.dashboard.architecture.AdminClient;
import org.apache.rocketmq.dashboard.model.ConsumerGroupInfo;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.heartbeat.SubscriptionGroupConfig;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardServiceImpl implements DashboardService {

    private static final Logger logger = LoggerFactory.getLogger(DashboardServiceImpl.class);

    @Resource
    private DashboardCollectService dashboardCollectService;

    @Resource
    private ConsumerService consumerService;

    @Resource
    private ClusterService clusterService;

    @Resource
    private MQAdminExt mqAdminExt;

    @Resource
    private AdminClient adminClient;

    /**
     * @param date format yyyy-MM-dd
     */
    @Override
    public Map<String, List<String>> queryBrokerData(String date) {
        return dashboardCollectService.getBrokerCache(date);
    }

    @Override
    public Map<String, List<String>> queryTopicData(String date) {
        return dashboardCollectService.getTopicCache(date);
    }

    /**
     * @param date      format yyyy-MM-dd
     * @param topicName
     */
    @Override
    public List<String> queryTopicData(String date, String topicName) {
        if (null != dashboardCollectService.getTopicCache(date)) {
            return dashboardCollectService.getTopicCache(date).get(topicName);
        }
        return null;
    }

    @Override
    public List<String> queryTopicCurrentData() {
        Date date = new Date();
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Map<String, List<String>> topicCache = dashboardCollectService.getTopicCache(format.format(date));
        List<String> result = Lists.newArrayList();
        for (Map.Entry<String, List<String>> entry : topicCache.entrySet()) {
            List<String> value = entry.getValue();
            result.add(entry.getKey() + "," + value.get(value.size() - 1).split(",")[4]);
        }
        return result;
    }

    @Override
    public Map<String, List<String>> queryAccumulationData(String date) {
        return dashboardCollectService.getAccumulationCache(date);
    }

    @Override
    public List<String> queryAccumulationData(String date, String topicName) {
        Map<String, List<String>> accumulationCache = dashboardCollectService.getAccumulationCache(date);
        if (null != accumulationCache) {
            return accumulationCache.get(topicName);
        }
        return null;
    }

    @Override
    public Map<String, List<String>> queryTransactionData(String date) {
        return dashboardCollectService.getTransactionCache(date);
    }

    @Override
    public List<String> queryTransactionData(String date, String topicName) {
        Map<String, List<String>> transactionCache = dashboardCollectService.getTransactionCache(date);
        if (null != transactionCache) {
            return transactionCache.get(topicName);
        }
        return null;
    }

    @Override
    public Map<String, List<String>> queryStorageLatencyData(String date) {
        return dashboardCollectService.getStorageLatencyCache(date);
    }

    @Override
    public List<String> queryStorageLatencyData(String date, String topicName) {
        Map<String, List<String>> storageLatencyCache = dashboardCollectService.getStorageLatencyCache(date);
        if (null != storageLatencyCache) {
            return storageLatencyCache.get(topicName);
        }
        return null;
    }

    @Override
    public Map<String, List<String>> queryNetworkThroughputData(String date) {
        return dashboardCollectService.getNetworkThroughputCache(date);
    }

    @Override
    public List<String> queryNetworkThroughputData(String date, String brokerName) {
        Map<String, List<String>> networkThroughputCache = dashboardCollectService.getNetworkThroughputCache(date);
        if (null != networkThroughputCache) {
            return networkThroughputCache.get(brokerName);
        }
        return null;
    }

    @Override
    public Map<String, List<String>> queryReplicaSyncData(String date) {
        return dashboardCollectService.getReplicaSyncCache(date);
    }

    @Override
    public List<String> queryReplicaSyncData(String date, String brokerName) {
        Map<String, List<String>> replicaSyncCache = dashboardCollectService.getReplicaSyncCache(date);
        if (null != replicaSyncCache) {
            return replicaSyncCache.get(brokerName);
        }
        return null;
    }

    @Override
    public Map<String, List<String>> queryHotTopicData(String date) {
        return dashboardCollectService.getHotTopicCache(date);
    }

    @Override
    public List<String> queryHotTopicData(String date, String topicName) {
        Map<String, List<String>> hotTopicCache = dashboardCollectService.getHotTopicCache(date);
        if (null != hotTopicCache) {
            return hotTopicCache.get(topicName);
        }
        return null;
    }

    @Override
    public List<Map<String, Object>> queryConsumerConcurrency() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<ConsumerGroupInfo> consumerGroups = consumerService.listConsumerGroups();
            if (consumerGroups == null || consumerGroups.isEmpty()) {
                return result;
            }

            // Get cluster info to find broker addresses for subscription config lookup
            ClusterInfo clusterInfo = mqAdminExt.examineBrokerClusterInfo();
            String firstBrokerAddr = null;
            for (BrokerData brokerData : clusterInfo.getBrokerAddrTable().values()) {
                // Prefer master (brokerId=0) address
                String addr = brokerData.getBrokerAddrs().get(0L);
                if (addr != null) {
                    firstBrokerAddr = addr;
                    break;
                }
            }

            for (ConsumerGroupInfo groupInfo : consumerGroups) {
                try {
                    String groupName = groupInfo.getConsumerGroupName();
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("groupName", groupName);

                    // Get subscription group config for thread limits from broker
                    int consumeThreadMax = 20; // RocketMQ default
                    int consumeThreadMin = 20;
                    if (firstBrokerAddr != null) {
                        try {
                            SubscriptionGroupConfig config = mqAdminExt.examineSubscriptionGroupConfig(firstBrokerAddr, groupName);
                            if (config != null) {
                                consumeThreadMax = config.getConsumeThreadMax();
                                consumeThreadMin = config.getConsumeThreadMin();
                            }
                        } catch (Exception e) {
                            logger.debug("Failed to get subscription config for group: {}", groupName);
                        }
                    }
                    entry.put("consumeThreadMax", consumeThreadMax);
                    entry.put("consumeThreadMin", consumeThreadMin);

                    // Get consumer connection for actual client count
                    int clientCount = 0;
                    try {
                        ConsumerConnection connection = adminClient.getConsumerConnection(groupName);
                        if (connection != null && connection.getConnectionSet() != null) {
                            clientCount = connection.getConnectionSet().size();
                        }
                    } catch (Exception e) {
                        logger.debug("Failed to get connection for group: {}", groupName);
                    }
                    // Fallback to onlineClientCount if connection query failed
                    if (clientCount == 0 && groupInfo.getOnlineClientCount() != null) {
                        clientCount = groupInfo.getOnlineClientCount();
                    }
                    entry.put("clientCount", clientCount);

                    result.add(entry);
                } catch (Exception e) {
                    logger.warn("Failed to get concurrency data for group: {}", groupInfo.getConsumerGroupName(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to query consumer concurrency", e);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> queryBrokerJvmStats() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            ClusterInfo clusterInfo = mqAdminExt.examineBrokerClusterInfo();
            for (BrokerData brokerData : clusterInfo.getBrokerAddrTable().values()) {
                for (Map.Entry<Long, String> brokerAddr : brokerData.getBrokerAddrs().entrySet()) {
                    try {
                        KVTable kvTable = mqAdminExt.fetchBrokerRuntimeStats(brokerAddr.getValue());
                        Map<String, String> stats = kvTable.getTable();

                        Map<String, Object> entry = new HashMap<>();
                        entry.put("brokerName", brokerData.getBrokerName());
                        entry.put("brokerId", brokerAddr.getKey());
                        entry.put("brokerAddr", brokerAddr.getValue());

                        // GC statistics
                        entry.put("gcCount", parseLong(stats.get("gcCount"), 0));
                        entry.put("gcTimeMillis", parseLong(stats.get("gcTimeMillis"), 0));
                        entry.put("jvmUptime", parseLong(stats.get("jvmUptime"), 0));

                        // Heap memory
                        entry.put("heapCommitted", parseLong(stats.get("brokerMemoryHeapCommitted"), 0));
                        entry.put("heapUsed", parseLong(stats.get("brokerMemoryHeapUsed"), 0));
                        entry.put("heapMax", parseLong(stats.get("brokerMemoryHeapMax"), 0));

                        // Non-heap memory
                        entry.put("nonHeapUsed", parseLong(stats.get("brokerMemoryNonHeapUsed"), 0));
                        entry.put("nonHeapCommitted", parseLong(stats.get("brokerMemoryNonHeapCommitted"), 0));

                        // Thread count
                        entry.put("threadCount", parseInt(stats.get("threadCount"), 0));

                        result.add(entry);
                    } catch (Exception e) {
                        logger.warn("Failed to get JVM stats for broker: {}", brokerAddr.getValue(), e);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to query broker JVM stats", e);
        }
        return result;
    }

    private static long parseLong(String value, long defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
