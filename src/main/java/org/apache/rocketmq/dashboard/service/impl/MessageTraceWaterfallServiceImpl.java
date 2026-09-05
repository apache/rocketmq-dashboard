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
import org.apache.rocketmq.client.trace.TraceType;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.dashboard.model.MessageTraceWaterfallReport;
import org.apache.rocketmq.dashboard.model.trace.TraceView;
import org.apache.rocketmq.dashboard.service.MessageTraceService;
import org.apache.rocketmq.dashboard.service.MessageTraceWaterfallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class MessageTraceWaterfallServiceImpl implements MessageTraceWaterfallService {

    private final Logger log = LoggerFactory.getLogger(MessageTraceWaterfallServiceImpl.class);

    @Autowired
    private MessageTraceService messageTraceService;

    @Override
    public MessageTraceWaterfallReport analyzeMessageTraceWaterfall(String msgId, String traceTopic) {
        MessageTraceWaterfallReport report = new MessageTraceWaterfallReport();
        report.setMsgId(msgId);

        String actualTraceTopic = StringUtils.isNotBlank(traceTopic) ? traceTopic : MixAll.RMQ_SYS_TRACE_TOPIC;
        List<TraceView> traceViews = null;
        try {
            traceViews = messageTraceService.queryMessageTraceByMsgId(msgId, actualTraceTopic);
        } catch (Exception e) {
            log.warn("Failed to query trace views for msgId={}: {}", msgId, e.getMessage());
        }

        if (traceViews == null || traceViews.isEmpty()) {
            report.setBottleneckPhase("NO_TRACE_FOUND");
            report.setSpanNodes(new ArrayList<>());
            return report;
        }

        traceViews.sort(Comparator.comparingLong(TraceView::getTimeStamp));

        TraceView pubTrace = null;
        List<TraceView> subTraces = new ArrayList<>();

        for (TraceView tv : traceViews) {
            if (TraceType.Pub.name().equalsIgnoreCase(tv.getMsgType())) {
                if (pubTrace == null) {
                    pubTrace = tv;
                }
            } else if (TraceType.SubBefore.name().equalsIgnoreCase(tv.getMsgType()) ||
                    TraceType.SubAfter.name().equalsIgnoreCase(tv.getMsgType())) {
                subTraces.add(tv);
            }
        }

        List<MessageTraceWaterfallReport.TraceSpanNode> spanNodes = new ArrayList<>();
        long maxDuration = -1L;
        String bottleneckPhase = "NORMAL";
        long firstTime = 0L;
        long lastTime = 0L;

        if (pubTrace != null) {
            report.setTopic(pubTrace.getTopic());
            report.setTags(pubTrace.getTags());
            report.setKeys(pubTrace.getKeys());

            MessageTraceWaterfallReport.TraceSpanNode sendSpan = new MessageTraceWaterfallReport.TraceSpanNode();
            sendSpan.setSpanId("span_pub_" + msgId);
            sendSpan.setStage("PRODUCER_SEND");
            sendSpan.setClientHost(pubTrace.getClientHost());
            sendSpan.setTargetHost(pubTrace.getStoreHost());
            sendSpan.setGroupName(pubTrace.getGroupName());
            sendSpan.setStartTime(pubTrace.getTimeStamp());
            long cost = pubTrace.getCostTime() > 0 ? pubTrace.getCostTime() : 15L;
            sendSpan.setEndTime(pubTrace.getTimeStamp() + cost);
            sendSpan.setDurationMs(cost);
            sendSpan.setStatus(pubTrace.getStatus());
            sendSpan.setDetails("Message published to Broker Queue " + pubTrace.getStoreHost());

            spanNodes.add(sendSpan);
            firstTime = sendSpan.getStartTime();
            lastTime = sendSpan.getEndTime();

            if (cost > maxDuration) {
                maxDuration = cost;
                bottleneckPhase = "PRODUCER_SEND";
            }
        }

        if (pubTrace != null && !subTraces.isEmpty()) {
            TraceView firstSub = subTraces.get(0);
            long brokerQueueCost = Math.max(0L, firstSub.getTimeStamp() - (pubTrace.getTimeStamp() + pubTrace.getCostTime()));

            MessageTraceWaterfallReport.TraceSpanNode storeSpan = new MessageTraceWaterfallReport.TraceSpanNode();
            storeSpan.setSpanId("span_broker_store_" + msgId);
            storeSpan.setStage("BROKER_STORE_QUEUE");
            storeSpan.setClientHost(pubTrace.getStoreHost());
            storeSpan.setTargetHost(firstSub.getClientHost());
            storeSpan.setGroupName("BROKER_STORAGE");
            storeSpan.setStartTime(pubTrace.getTimeStamp() + pubTrace.getCostTime());
            storeSpan.setEndTime(firstSub.getTimeStamp());
            storeSpan.setDurationMs(brokerQueueCost);
            storeSpan.setStatus("SUCCESS");
            storeSpan.setDetails("CommitLog append & ConsumeQueue build latency in Broker storage");

            spanNodes.add(storeSpan);
            lastTime = Math.max(lastTime, storeSpan.getEndTime());

            if (brokerQueueCost > maxDuration) {
                maxDuration = brokerQueueCost;
                bottleneckPhase = "BROKER_STORE_QUEUE";
            }
        }

        for (int i = 0; i < subTraces.size(); i++) {
            TraceView sub = subTraces.get(i);
            MessageTraceWaterfallReport.TraceSpanNode subSpan = new MessageTraceWaterfallReport.TraceSpanNode();
            subSpan.setSpanId("span_sub_" + i + "_" + msgId);
            subSpan.setStage(TraceType.SubAfter.name().equalsIgnoreCase(sub.getMsgType()) ? "CONSUMER_EXECUTION" : "NETWORK_DISPATCH");
            subSpan.setClientHost(sub.getClientHost());
            subSpan.setGroupName(sub.getGroupName());
            subSpan.setStartTime(sub.getTimeStamp());
            long subCost = sub.getCostTime() > 0 ? sub.getCostTime() : 20L;
            subSpan.setEndTime(sub.getTimeStamp() + subCost);
            subSpan.setDurationMs(subCost);
            subSpan.setStatus(sub.getStatus());
            subSpan.setDetails("Consumer Group [" + sub.getGroupName() + "] message consumption processing");

            spanNodes.add(subSpan);
            lastTime = Math.max(lastTime, subSpan.getEndTime());

            if (subCost > maxDuration) {
                maxDuration = subCost;
                bottleneckPhase = subSpan.getStage();
            }
        }

        for (MessageTraceWaterfallReport.TraceSpanNode node : spanNodes) {
            if (node.getDurationMs() == maxDuration && maxDuration > 0) {
                node.setBottleneck(true);
            }
        }

        long totalE2e = Math.max(0L, lastTime - firstTime);
        report.setTotalE2eLatencyMs(totalE2e);
        report.setTimeout(totalE2e > 5000L);
        report.setBottleneckPhase(bottleneckPhase);
        report.setSpanNodes(spanNodes);

        return report;
    }
}
