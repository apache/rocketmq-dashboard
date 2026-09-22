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
package org.apache.rocketmq.studio.instance.message;

import org.apache.rocketmq.studio.instance.message.MessageTraceWaterfallVO.TraceStageSpanVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MessageTraceLifecycleAggregator {

    public MessageTraceWaterfallVO aggregate(String msgId, String topic, TraceRecordVO traceRecord) {
        MessageTraceWaterfallVO waterfall = MessageTraceWaterfallVO.builder()
                .msgId(msgId)
                .topic(topic)
                .bottleneckStage("NONE")
                .stages(new ArrayList<>())
                .diagnosticAlerts(new ArrayList<>())
                .build();

        if (traceRecord == null || traceRecord.getNodes() == null || traceRecord.getNodes().isEmpty()) {
            waterfall.getDiagnosticAlerts().add("No trace nodes available for message: " + msgId);
            return waterfall;
        }

        List<TraceNodeVO> nodes = traceRecord.getNodes();
        TraceNodeVO pubNode = null;
        TraceNodeVO subNode = null;

        for (TraceNodeVO node : nodes) {
            String title = node.getTitle() != null ? node.getTitle().toUpperCase() : "";
            if (title.contains("SEND") || title.contains("PUB")) {
                pubNode = node;
            } else if (title.contains("SUB") || title.contains("CONSUME")) {
                subNode = node;
            }
        }

        long pubCost = pubNode != null ? Math.max(0, pubNode.getCostTime()) : 0L;
        long pubTime = pubNode != null ? pubNode.getTimestamp() : 0L;

        long subCost = subNode != null ? Math.max(0, subNode.getCostTime()) : 0L;
        long subTime = subNode != null ? subNode.getTimestamp() : 0L;

        long transitDuration = 0L;
        if (pubTime > 0 && subTime >= pubTime) {
            transitDuration = Math.max(0, (subTime - (pubTime + pubCost)));
        }

        long totalDuration = pubCost + transitDuration + subCost;
        waterfall.setTotalDurationMs(totalDuration);
        waterfall.setProducerSendDurationMs(pubCost);
        waterfall.setBrokerTransitDurationMs(transitDuration);
        waterfall.setConsumerProcessingDurationMs(subCost);
        waterfall.setCompleted(subNode != null);

        // Build Stage Spans
        if (pubNode != null) {
            double percent = totalDuration > 0 ? (double) pubCost / totalDuration * 100.0 : 0.0;
            waterfall.getStages().add(TraceStageSpanVO.builder()
                    .stageName("PRODUCER_SEND")
                    .status(pubNode.getStatus() != null ? pubNode.getStatus() : "SUCCESS")
                    .startTimestamp(pubTime)
                    .endTimestamp(pubTime + pubCost)
                    .durationMs(pubCost)
                    .durationPercent(Math.round(percent * 10.0) / 10.0)
                    .executorNode("PRODUCER_CLIENT")
                    .details(pubNode.getDescription())
                    .build());
        }

        if (transitDuration > 0 || (pubNode != null && subNode != null)) {
            double percent = totalDuration > 0 ? (double) transitDuration / totalDuration * 100.0 : 0.0;
            waterfall.getStages().add(TraceStageSpanVO.builder()
                    .stageName("BROKER_TRANSIT")
                    .status("SUCCESS")
                    .startTimestamp(pubTime + pubCost)
                    .endTimestamp(subTime)
                    .durationMs(transitDuration)
                    .durationPercent(Math.round(percent * 10.0) / 10.0)
                    .executorNode("BROKER_CLUSTER")
                    .details("Message committed and queued on broker commitLog/consumeQueue.")
                    .build());
        }

        if (subNode != null) {
            double percent = totalDuration > 0 ? (double) subCost / totalDuration * 100.0 : 0.0;
            waterfall.getStages().add(TraceStageSpanVO.builder()
                    .stageName("CONSUMER_PROCESSING")
                    .status(subNode.getStatus() != null ? subNode.getStatus() : "SUCCESS")
                    .startTimestamp(subTime)
                    .endTimestamp(subTime + subCost)
                    .durationMs(subCost)
                    .durationPercent(Math.round(percent * 10.0) / 10.0)
                    .executorNode("CONSUMER_CLIENT")
                    .details(subNode.getDescription())
                    .build());
        }

        // Evaluate Bottleneck
        if (pubCost > transitDuration && pubCost > subCost && pubCost > 500) {
            waterfall.setBottleneckStage("PRODUCER_SEND");
            waterfall.getDiagnosticAlerts().add(String.format(
                    "Producer send latency is high (%d ms). Check network latency to broker or client thread pool.", pubCost));
        } else if (transitDuration > pubCost && transitDuration > subCost && transitDuration > 3000) {
            waterfall.setBottleneckStage("BROKER_STORAGE_TRANSIT");
            waterfall.getDiagnosticAlerts().add(String.format(
                    "High queuing transit delay on broker (%d ms). Consumers may be lagging or under-provisioned.", transitDuration));
        } else if (subCost > pubCost && subCost > transitDuration && subCost > 1000) {
            waterfall.setBottleneckStage("CONSUMER_EXECUTION");
            waterfall.getDiagnosticAlerts().add(String.format(
                    "Consumer execution latency is long (%d ms). Optimize listener logic or avoid heavy downstream RPCs.", subCost));
        }

        return waterfall;
    }
}
