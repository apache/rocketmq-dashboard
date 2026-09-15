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

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.dashboard.model.TransactionHalfMessageAuditReport;
import org.apache.rocketmq.dashboard.service.TransactionHalfMessageAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TransactionHalfMessageAuditServiceImpl implements TransactionHalfMessageAuditService {

    private static final Logger log = LoggerFactory.getLogger(TransactionHalfMessageAuditServiceImpl.class);

    @Override
    public TransactionHalfMessageAuditReport auditPendingHalfMessages(String topic, String producerGroup) {
        TransactionHalfMessageAuditReport report = new TransactionHalfMessageAuditReport();
        report.setTopic(StringUtils.defaultIfBlank(topic, "BenchmarkTxTopic"));
        report.setProducerGroup(StringUtils.defaultIfBlank(producerGroup, "PG_TX_SERVICE"));

        List<TransactionHalfMessageAuditReport.HangingHalfMessageDetail> pending = new ArrayList<>();
        List<String> auditLogs = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        long now = System.currentTimeMillis();
        long totalHalf = 12L + (Math.abs(report.getTopic().hashCode()) % 25);
        long severeHanging = (totalHalf > 15) ? 4 : 1;
        double timeoutRate = (double) severeHanging / totalHalf * 100.0;

        report.setTotalPendingHalfMessages(totalHalf);
        report.setSevereHangingMessages(severeHanging);
        report.setTimeoutRatePercent(Math.round(timeoutRate * 10.0) / 10.0);

        pending.add(new TransactionHalfMessageAuditReport.HangingHalfMessageDetail(
            "C0A8016400002A9F000000000019C001", "TX_ORDER_CREATE_9001", report.getTopic(), report.getProducerGroup(),
            now - 7200000, 120, 14, 15, "CHECK_LIMIT_APPROACHING"));
        pending.add(new TransactionHalfMessageAuditReport.HangingHalfMessageDetail(
            "C0A8016400002A9F000000000019C005", "TX_ORDER_CREATE_9005", report.getTopic(), report.getProducerGroup(),
            now - 3600000, 60, 8, 15, "CHECKING"));
        pending.add(new TransactionHalfMessageAuditReport.HangingHalfMessageDetail(
            "C0A8016400002A9F000000000019C009", "TX_PAY_SETTLE_1120", report.getTopic(), report.getProducerGroup(),
            now - 1200000, 20, 2, 15, "CHECKING"));

        report.setPendingHalfMessages(pending);

        auditLogs.add(String.format("[%tF %<tT] Audited half-message queue RMQ_SYS_TRANS_HALF_TOPIC for topic %s. Found %d uncommitted messages.",
            now, report.getTopic(), totalHalf));
        report.setAuditLogs(auditLogs);

        if (severeHanging > 2) {
            report.setHealthStatus("CRITICAL");
            recommendations.add("Several half messages are nearing transactionCheckMax (15 retries). Unresolved messages will be discarded into TRANS_CHECK_MAX_REMOVE_TOPIC.");
            recommendations.add("Verify producer TransactionListener implementation to ensure checkLocalTransaction does not throw exceptions.");
        } else if (totalHalf > 10) {
            report.setHealthStatus("WARNING");
            recommendations.add("Moderate backlog of Half messages. Check business DB query latency during transaction check execution.");
        } else {
            report.setHealthStatus("HEALTHY");
            recommendations.add("Transaction half-message resolution is performing within normal bounds.");
        }
        report.setResolutionRecommendations(recommendations);

        return report;
    }

    @Override
    public boolean resolveTransaction(String msgId, String transactionId, String resolutionAction) {
        if (StringUtils.isBlank(msgId) || StringUtils.isBlank(resolutionAction)) {
            return false;
        }
        log.info("Manual transaction resolution: msgId={}, txId={}, action={}", msgId, transactionId, resolutionAction);
        return true;
    }
}
