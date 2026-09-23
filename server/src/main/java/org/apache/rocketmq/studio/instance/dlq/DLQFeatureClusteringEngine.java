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
package org.apache.rocketmq.studio.instance.dlq;

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.studio.instance.dlq.DLQClusteringReportVO.DLQMessageClusterVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DLQFeatureClusteringEngine {

    private static final Pattern EXCEPTION_PATTERN =
            Pattern.compile("(?<ex>[a-zA-Z0-9_.]+(?:Exception|Error|Failure))");

    public DLQClusteringReportVO clusterAndEvaluateReplay(String groupName, List<DLQMessageVO> messages) {
        DLQClusteringReportVO report = DLQClusteringReportVO.builder()
                .groupName(groupName)
                .totalSampledMessages(messages == null ? 0 : messages.size())
                .clusters(new ArrayList<>())
                .replayRecommendations(new ArrayList<>())
                .build();

        if (messages == null || messages.isEmpty()) {
            report.getReplayRecommendations().add("No DLQ messages found for analysis in the specified window.");
            return report;
        }

        Map<String, ClusterBucket> bucketMap = new LinkedHashMap<>();

        for (DLQMessageVO msg : messages) {
            String originTopic = extractOriginTopic(msg);
            String exceptionClass = extractExceptionSignature(msg);
            String clusterKey = originTopic + "@@" + exceptionClass;

            ClusterBucket bucket = bucketMap.computeIfAbsent(clusterKey,
                    k -> new ClusterBucket(k, originTopic, exceptionClass));
            bucket.messages.add(msg);
            bucket.totalReconsumeTimes += msg.getReconsumeTimes();
        }

        int total = messages.size();
        List<DLQMessageClusterVO> clusterVOList = new ArrayList<>();

        for (ClusterBucket bucket : bucketMap.values()) {
            int count = bucket.messages.size();
            double pct = total > 0 ? (double) count / total * 100.0 : 0.0;
            int avgReconsume = count > 0 ? (int) Math.round((double) bucket.totalReconsumeTimes / count) : 0;

            String risk;
            boolean recommended;
            if (bucket.exceptionClass.contains("NullPointer")
                    || bucket.exceptionClass.contains("ClassCast")
                    || bucket.exceptionClass.contains("ParseException")
                    || bucket.exceptionClass.contains("JsonSyntaxException")) {
                risk = "PERMANENT_SCHEMA_FAILURE";
                recommended = false;
            } else if (bucket.exceptionClass.contains("Timeout")
                    || bucket.exceptionClass.contains("ConnectException")
                    || bucket.exceptionClass.contains("HttpHostConnectException")
                    || bucket.exceptionClass.contains("SocketTimeoutException")) {
                risk = "SAFE_TO_REPLAY";
                recommended = true;
            } else {
                risk = "CAUTION_IDEMPOTENCY_RISK";
                recommended = false;
            }

            List<String> sampleIds = bucket.messages.stream()
                    .map(DLQMessageVO::getMsgId)
                    .filter(StringUtils::isNotBlank)
                    .limit(5)
                    .toList();

            clusterVOList.add(DLQMessageClusterVO.builder()
                    .clusterKey(bucket.clusterKey)
                    .originTopic(bucket.originTopic)
                    .detectedExceptionClass(bucket.exceptionClass)
                    .messageCount(count)
                    .percentage(Math.round(pct * 10.0) / 10.0)
                    .averageReconsumeTimes(avgReconsume)
                    .recommendedForReplay(recommended)
                    .riskAssessment(risk)
                    .sampleMsgIds(sampleIds)
                    .build());
        }

        // Sort clusters by message count descending
        clusterVOList.sort(Comparator.comparingInt(DLQMessageClusterVO::getMessageCount).reversed());

        report.setClusterCount(clusterVOList.size());
        report.setClusters(clusterVOList);

        long safeCount = clusterVOList.stream().filter(DLQMessageClusterVO::isRecommendedForReplay)
                .mapToInt(DLQMessageClusterVO::getMessageCount).sum();
        long permanentFailureCount = clusterVOList.stream()
                .filter(c -> "PERMANENT_SCHEMA_FAILURE".equals(c.getRiskAssessment()))
                .mapToInt(DLQMessageClusterVO::getMessageCount).sum();

        if (safeCount > 0) {
            report.getReplayRecommendations().add(String.format(
                    "Found %d transient-failure message(s) (network/timeout) safe for automated replay.", safeCount));
        }
        if (permanentFailureCount > 0) {
            report.getReplayRecommendations().add(String.format(
                    "Found %d message(s) with code/schema failure (NPE, syntax error). Fix business code before replaying to prevent replay loop.",
                    permanentFailureCount));
        }
        if (report.getReplayRecommendations().isEmpty()) {
            report.getReplayRecommendations().add("Messages have non-standard error signatures; verify consumer idempotency before replaying.");
        }

        return report;
    }

    private String extractOriginTopic(DLQMessageVO msg) {
        if (msg.getProperties() != null) {
            String retryTopic = msg.getProperties().get("RETRY_TOPIC");
            if (StringUtils.isNotBlank(retryTopic)) {
                return retryTopic.trim();
            }
            String realTopic = msg.getProperties().get("REAL_TOPIC");
            if (StringUtils.isNotBlank(realTopic)) {
                return realTopic.trim();
            }
        }
        if (StringUtils.isNotBlank(msg.getTopic()) && !msg.getTopic().startsWith("%DLQ%")) {
            return msg.getTopic().trim();
        }
        return "UNKNOWN_ORIGIN_TOPIC";
    }

    private String extractExceptionSignature(DLQMessageVO msg) {
        if (msg.getProperties() != null) {
            for (Map.Entry<String, String> entry : msg.getProperties().entrySet()) {
                if (entry.getKey().toLowerCase().contains("exception")
                        || entry.getKey().toLowerCase().contains("error")) {
                    Matcher m = EXCEPTION_PATTERN.matcher(entry.getValue());
                    if (m.find()) {
                        return m.group("ex");
                    }
                }
            }
        }
        if (StringUtils.isNotBlank(msg.getBody())) {
            Matcher m = EXCEPTION_PATTERN.matcher(msg.getBody());
            if (m.find()) {
                return m.group("ex");
            }
        }
        return "GenericProcessingFailure";
    }

    private static class ClusterBucket {
        final String clusterKey;
        final String originTopic;
        final String exceptionClass;
        final List<DLQMessageVO> messages = new ArrayList<>();
        long totalReconsumeTimes = 0;

        ClusterBucket(String clusterKey, String originTopic, String exceptionClass) {
            this.clusterKey = clusterKey;
            this.originTopic = originTopic;
            this.exceptionClass = exceptionClass;
        }
    }
}
