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

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.protocol.body.Connection;
import org.apache.rocketmq.common.protocol.body.ConsumerConnection;
import org.apache.rocketmq.common.protocol.body.ConsumerRunningInfo;
import org.apache.rocketmq.dashboard.model.ConsumerSlowRootCauseReport;
import org.apache.rocketmq.dashboard.service.ConsumerService;
import org.apache.rocketmq.dashboard.service.ConsumerSlowRootCauseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

@Service
public class ConsumerSlowRootCauseServiceImpl implements ConsumerSlowRootCauseService {

    private final Logger log = LoggerFactory.getLogger(ConsumerSlowRootCauseServiceImpl.class);

    @Autowired
    private ConsumerService consumerService;

    @Override
    public ConsumerSlowRootCauseReport analyzeSlowRootCause(String consumerGroup) {
        ConsumerSlowRootCauseReport report = new ConsumerSlowRootCauseReport();
        report.setConsumerGroup(consumerGroup);
        report.setDiagnoseTime(System.currentTimeMillis());

        ConsumerConnection connection;
        try {
            connection = consumerService.getConsumerConnection(consumerGroup);
        } catch (Exception e) {
            log.warn("Failed to get consumer connection for {}: {}", consumerGroup, e.getMessage());
            report.setPrimaryRootCause("UNKNOWN");
            report.setSeverity("CRITICAL");
            report.setRootCauseDescription("Unable to connect to consumer group or NameServer: " + e.getMessage());
            report.setActionableRemedy("Verify network connectivity and consumer group registration.");
            return report;
        }

        if (connection == null || CollectionUtils.isEmpty(connection.getConnectionSet())) {
            report.setTotalClients(0);
            report.setPrimaryRootCause("NO_CLIENT_ONLINE");
            report.setSeverity("CRITICAL");
            report.setRootCauseDescription("All consumer client instances are currently offline. Messages accumulating without consumption.");
            report.setActionableRemedy("Restart consumer client pods/nodes to resume consumption.");
            return report;
        }

        Set<Connection> clients = connection.getConnectionSet();
        report.setTotalClients(clients.size());

        List<ConsumerSlowRootCauseReport.ClientDiagnosticFinding> findings = new ArrayList<>();
        int blockedThreadCount = 0;
        int flowControlCount = 0;
        int highRtCount = 0;

        for (Connection conn : clients) {
            ConsumerSlowRootCauseReport.ClientDiagnosticFinding finding =
                    new ConsumerSlowRootCauseReport.ClientDiagnosticFinding();
            finding.setClientId(conn.getClientId());
            finding.setClientAddr(conn.getClientAddr());

            ConsumerRunningInfo runningInfo = null;
            try {
                runningInfo = consumerService.getConsumerRunningInfo(consumerGroup, conn.getClientId(), false);
            } catch (Exception e) {
                log.warn("Failed to retrieve consumer running info for {}: {}", conn.getClientId(), e.getMessage());
            }

            if (runningInfo != null) {
                Properties prop = runningInfo.getProperties();
                if (prop != null) {
                    try {
                        String consumeTpsStr = prop.getProperty(ConsumerRunningInfo.PROP_CONSUME_OK_TPS, "0.0");
                        finding.setConsumeTps(Double.parseDouble(consumeTpsStr));
                        String pullTpsStr = prop.getProperty(ConsumerRunningInfo.PROP_PULL_TPS, "0.0");
                        finding.setPullTps(Double.parseDouble(pullTpsStr));
                    } catch (Exception ignored) {
                    }
                }

                // Inspect ProcessQueue cache count & size for flow control triggers
                if (runningInfo.getMqTable() != null) {
                    long totalCachedCount = 0L;
                    for (org.apache.rocketmq.common.protocol.body.ProcessQueueInfo pq : runningInfo.getMqTable().values()) {
                        totalCachedCount += pq.getCachedMsgCount();
                    }
                    finding.setCachedMessageCount(totalCachedCount);
                    finding.setCachedMessageSizeMb(totalCachedCount * 2L / 1024L); // approximate MB

                    // Default RocketMQ client threshold is 1000 msgs per queue
                    if (totalCachedCount > 1000 * Math.max(1, runningInfo.getMqTable().size())) {
                        finding.setFlowControlTriggered(true);
                        flowControlCount++;
                    }
                }

                // Inspect Thread Stack for lock wait or IO block
                String jstack = runningInfo.getJstack();
                if (StringUtils.isNotBlank(jstack)) {
                    if (jstack.contains("java.lang.Thread.State: BLOCKED") ||
                            jstack.contains("waiting to lock") ||
                            jstack.contains("SocketInputStream.socketRead0")) {
                        finding.setBlockedThreadDetected(true);
                        finding.setBlockedThreadSignature("Thread blocked in socket read or lock contention");
                        blockedThreadCount++;
                    }
                }

                if (finding.isBlockedThreadDetected()) {
                    finding.setDiagnosisSummary("Business consumption thread pool blocked on external I/O or synchronized lock.");
                } else if (finding.isFlowControlTriggered()) {
                    finding.setDiagnosisSummary("Client ProcessQueue reached pull-threshold (flow control); pulling suspended.");
                } else {
                    finding.setDiagnosisSummary("Client internal pipeline operating nominally.");
                }
            } else {
                finding.setDiagnosisSummary("Running metrics unreachable. Heartbeat active but telemetry unresponsive.");
            }

            findings.add(finding);
        }

        report.setFindings(findings);

        if (blockedThreadCount > 0) {
            report.setPrimaryRootCause("THREAD_BLOCKED");
            report.setSeverity("CRITICAL");
            report.setRootCauseDescription("Detected " + blockedThreadCount + " consumer clients with threads stuck in BLOCKED/waiting state.");
            report.setActionableRemedy("Inspect thread dumps for downstream database lock contention or slow third-party HTTP timeouts.");
        } else if (flowControlCount > 0) {
            report.setPrimaryRootCause("FLOW_CONTROL_EXCEEDED");
            report.setSeverity("WARNING");
            report.setRootCauseDescription("Detected " + flowControlCount + " clients hitting ProcessQueue cachedMsgCount threshold.");
            report.setActionableRemedy("Tune consumeThreadMin/Max or optimize per-message processing speed in Consumer Listener.");
        } else {
            report.setPrimaryRootCause("HEALTHY");
            report.setSeverity("NORMAL");
            report.setRootCauseDescription("No thread blockages or client-side flow control stalls detected across active instances.");
            report.setActionableRemedy("If lag exists, check Broker throughput quotas or scale up consumer replica instances.");
        }

        return report;
    }
}
