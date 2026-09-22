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
package org.apache.rocketmq.studio.instance.group;

import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.body.ConsumerRunningInfo;
import org.apache.rocketmq.remoting.protocol.body.ProcessQueueInfo;
import org.apache.rocketmq.studio.instance.group.ConsumerHangReportVO.QueueHangFindingVO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
public class ConsumerHangDiagnosticEngine {

    // Thresholds for detecting consumer hang
    public static final long STALL_WARNING_THRESHOLD_MS = 30_000L; // 30 seconds
    public static final long STALL_CRITICAL_THRESHOLD_MS = 120_000L; // 2 minutes
    public static final int CACHED_MSG_PRESSURE_THRESHOLD = 500;

    public ConsumerHangReportVO analyze(String instanceId, String groupName, String clientId,
                                       ConsumerRunningInfo runningInfo) {
        ConsumerHangReportVO report = ConsumerHangReportVO.builder()
                .instanceId(instanceId)
                .groupName(groupName)
                .clientId(clientId)
                .overallHealth("HEALTHY")
                .analyzedAt(LocalDateTime.now())
                .queueFindings(new ArrayList<>())
                .diagnosticSuggestions(new ArrayList<>())
                .build();

        if (runningInfo == null) {
            report.setOverallHealth("UNKNOWN");
            report.getDiagnosticSuggestions().add("No ConsumerRunningInfo available for client: " + clientId);
            return report;
        }

        TreeMap<MessageQueue, ProcessQueueInfo> pqTable = runningInfo.getMqTable();
        if (pqTable == null || pqTable.isEmpty()) {
            report.setTotalQueuesAnalyzed(0);
            report.getDiagnosticSuggestions().add("Client has no allocated message queues in process queue table");
            return report;
        }

        report.setTotalQueuesAnalyzed(pqTable.size());
        long now = System.currentTimeMillis();
        int stalledQueues = 0;
        long maxStall = 0L;

        for (Map.Entry<MessageQueue, ProcessQueueInfo> entry : pqTable.entrySet()) {
            MessageQueue mq = entry.getKey();
            ProcessQueueInfo pq = entry.getValue();

            long lastConsumeTimestamp = pq.getLastConsumeTimestamp();
            long lastPullTimestamp = pq.getLastPullTimestamp();
            int cachedCount = pq.getCachedMsgCount();
            long cachedSize = pq.getCachedMsgSizeInMiB();

            long stallDuration = 0L;
            if (cachedCount > 0 && lastConsumeTimestamp > 0) {
                stallDuration = Math.max(0, now - lastConsumeTimestamp);
            }

            if (stallDuration > maxStall) {
                maxStall = stallDuration;
            }

            if (stallDuration >= STALL_WARNING_THRESHOLD_MS) {
                stalledQueues++;
                boolean isCritical = stallDuration >= STALL_CRITICAL_THRESHOLD_MS;
                String severity = isCritical ? "CRITICAL" : "HIGH";

                String rootCause;
                String recommendation;
                if (cachedCount >= CACHED_MSG_PRESSURE_THRESHOLD) {
                    rootCause = "PULL_FLOW_CONTROL";
                    recommendation = "Client flow control triggered due to cached message count exceeding threshold. " +
                            "Increase consumption concurrency or scale consumer instances.";
                } else if (pq.isDroped()) {
                    rootCause = "CLIENT_DROPPED_QUEUE";
                    recommendation = "MessageQueue has been dropped by consumer rebalance. Wait for rebalance settlement.";
                } else {
                    rootCause = "BUSINESS_LISTENER_BLOCKED";
                    recommendation = "Message listener thread is blocked or processing a single message too slowly. " +
                            "Inspect thread stack dump for lock contention, I/O wait, or external call timeouts.";
                }

                report.getQueueFindings().add(QueueHangFindingVO.builder()
                        .topic(mq.getTopic())
                        .brokerName(mq.getBrokerName())
                        .queueId(mq.getQueueId())
                        .cachedMsgCount(cachedCount)
                        .cachedMsgSizeInMiB(cachedSize)
                        .lastPullTimestamp(lastPullTimestamp)
                        .lastConsumeTimestamp(lastConsumeTimestamp)
                        .stallDurationMs(stallDuration)
                        .rootCause(rootCause)
                        .severity(severity)
                        .recommendation(recommendation)
                        .build());
            }
        }

        report.setStalledQueueCount(stalledQueues);
        report.setMaxStallDurationMs(maxStall);

        // Detect blocked threads from jstack
        String jstack = runningInfo.getJstack();
        int blockedThreads = countBlockedThreads(jstack);
        report.setBlockedThreadCount(blockedThreads);

        if (stalledQueues > 0 || blockedThreads > 2) {
            report.setOverallHealth(maxStall >= STALL_CRITICAL_THRESHOLD_MS ? "CRITICAL" : "WARNING");
            if (blockedThreads > 0) {
                report.getDiagnosticSuggestions().add(String.format(
                        "Found %d thread(s) in BLOCKED or WAITING state in jstack dump. Check lock deadlocks.", blockedThreads));
            }
            report.getDiagnosticSuggestions().add(String.format(
                    "Detected %d queue(s) with consumption stall exceeding %d seconds. Max stall: %d ms.",
                    stalledQueues, STALL_WARNING_THRESHOLD_MS / 1000, maxStall));
        } else {
            report.setOverallHealth("HEALTHY");
            report.getDiagnosticSuggestions().add("All process queues are advancing consumption offsets normally.");
        }

        return report;
    }

    private int countBlockedThreads(String jstack) {
        if (jstack == null || jstack.isEmpty()) {
            return 0;
        }
        int count = 0;
        String[] lines = jstack.split("\n");
        for (String line : lines) {
            if (line.contains("java.lang.Thread.State: BLOCKED")
                    || line.contains("java.lang.Thread.State: WAITING (parking)")) {
                count++;
            }
        }
        return count;
    }
}
