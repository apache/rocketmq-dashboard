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

package org.apache.rocketmq.dashboard.service;

import java.util.List;
import java.util.Map;

public interface DashboardService {
    /**
     * @param date format yyyy-MM-dd
     */
    Map<String, List<String>> queryBrokerData(String date);

    /**
     * @param date format yyyy-MM-dd
     */
    Map<String, List<String>> queryTopicData(String date);

    /**
     * @param date      format yyyy-MM-dd
     * @param topicName
     */
    List<String> queryTopicData(String date, String topicName);

    List<String> queryTopicCurrentData();

    /**
     * Query accumulation depth trend data for all topics on a given date.
     * Returns map of topicName -> list of "timestamp,diffTotal" strings.
     *
     * @param date format yyyy-MM-dd
     */
    Map<String, List<String>> queryAccumulationData(String date);

    /**
     * Query accumulation depth trend data for a specific topic on a given date.
     * Returns list of "timestamp,diffTotal" strings.
     *
     * @param date      format yyyy-MM-dd
     * @param topicName topic name
     */
    List<String> queryAccumulationData(String date, String topicName);

    /**
     * Query transaction message metrics for all topics on a given date.
     * Returns map of topicName -> list of "timestamp,sndbckPutNums,groupCkNums" strings.
     *
     * @param date format yyyy-MM-dd
     */
    Map<String, List<String>> queryTransactionData(String date);

    /**
     * Query transaction message metrics for a specific topic on a given date.
     * Returns list of "timestamp,sndbckPutNums,groupCkNums" strings.
     *
     * @param date      format yyyy-MM-dd
     * @param topicName topic name
     */
    List<String> queryTransactionData(String date, String topicName);

    /**
     * Query storage write latency data for all topics on a given date.
     * Returns map of topicName -> list of "timestamp,putLatency,getLatency,fallSize,fallTime" strings.
     *
     * @param date format yyyy-MM-dd
     */
    Map<String, List<String>> queryStorageLatencyData(String date);

    /**
     * Query storage write latency data for a specific topic on a given date.
     * Returns list of "timestamp,putLatency,getLatency,fallSize,fallTime" strings.
     *
     * @param date      format yyyy-MM-dd
     * @param topicName topic name
     */
    List<String> queryStorageLatencyData(String date, String topicName);

    /**
     * Query broker network throughput data for all brokers on a given date.
     * Returns map of brokerName -> list of "timestamp,putTps,putNumsToday,getTps,getNumsToday" strings.
     *
     * @param date format yyyy-MM-dd
     */
    Map<String, List<String>> queryNetworkThroughputData(String date);

    /**
     * Query broker network throughput data for a specific broker on a given date.
     * Returns list of "timestamp,putTps,putNumsToday,getTps,getNumsToday" strings.
     *
     * @param date       format yyyy-MM-dd
     * @param brokerName broker name
     */
    List<String> queryNetworkThroughputData(String date, String brokerName);

    /**
     * Query replica sync latency data for all brokers on a given date.
     * Returns map of brokerName -> list of "timestamp,maxDiff,totalTransferredBytes,inSyncSlaveNums,slaveCount" strings.
     *
     * @param date format yyyy-MM-dd
     */
    Map<String, List<String>> queryReplicaSyncData(String date);

    /**
     * Query replica sync latency data for a specific broker on a given date.
     * Returns list of "timestamp,maxDiff,totalTransferredBytes,inSyncSlaveNums,slaveCount" strings.
     *
     * @param date       format yyyy-MM-dd
     * @param brokerName broker name
     */
    List<String> queryReplicaSyncData(String date, String brokerName);

    /**
     * Query hot topic data for all topics on a given date.
     * Returns map of topicName -> list of "timestamp,putTps,putNumsToday,getTps,getNumsToday" strings.
     *
     * @param date format yyyy-MM-dd
     */
    Map<String, List<String>> queryHotTopicData(String date);

    /**
     * Query hot topic data for a specific topic on a given date.
     * Returns list of "timestamp,putTps,putNumsToday,getTps,getNumsToday" strings.
     *
     * @param date      format yyyy-MM-dd
     * @param topicName topic name
     */
    List<String> queryHotTopicData(String date, String topicName);

    /**
     * Query real-time consumer concurrency data for all consumer groups.
     * Returns a list of maps, each containing:
     * - groupName: consumer group name
     * - clientCount: number of connected consumer instances
     * - consumeThreadMax: configured max consume threads
     * - consumeThreadMin: configured min consume threads
     * - consumeTps: current consume TPS
     */
    List<Map<String, Object>> queryConsumerConcurrency();

    /**
     * Query real-time JVM GC and memory statistics for all broker nodes.
     * Returns a list of maps, each containing:
     * - brokerName, brokerId, brokerAddr
     * - gcCount, gcTimeMillis, jvmUptime
     * - heapCommitted, heapUsed, heapMax, nonHeapUsed
     * - threadCount
     */
    List<Map<String, Object>> queryBrokerJvmStats();
}
