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
package org.apache.rocketmq.studio.ops.ai.tool;

import org.apache.rocketmq.studio.instance.message.ConsumerStatusVO;
import org.apache.rocketmq.studio.instance.message.MessageService;
import org.apache.rocketmq.studio.instance.message.TraceNodeVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Returns the message trace timeline for one message id. Read-only and safe for AI/MCP/CLI callers.
 */
@Component
@RequiredArgsConstructor
public class MessageTraceToolHandler implements ToolHandler {

    private static final String NAME = "rmq.message.trace";

    private final MessageService messageService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Object execute(Map<String, Object> input) {
        String instanceId = (String) input.get("cluster");
        String msgId = (String) input.get("msgId");
        String topic = (String) input.get("topic");
        TraceRecordVO trace = messageService.getMessageTrace(instanceId, msgId, topic);
        return project(msgId, trace);
    }

    /**
     * Providers may report a missing trace as {@code null} or as a record whose node and
     * consumer-status lists are absent; the output schema only requires the arrays to be
     * present, so project empty timelines instead of turning the tool call into an NPE 500.
     */
    private static Map<String, Object> project(String msgId, TraceRecordVO trace) {
        List<TraceNodeVO> nodes = trace == null || trace.getNodes() == null
                ? List.of()
                : trace.getNodes();
        List<ConsumerStatusVO> consumerStatus = trace == null || trace.getConsumerStatus() == null
                ? List.of()
                : trace.getConsumerStatus();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("msgId", msgId);
        result.put("nodes", nodes.stream()
                .map(MessageTraceToolHandler::projectNode)
                .toList());
        result.put("consumerStatus", consumerStatus.stream()
                .map(MessageTraceToolHandler::projectConsumerStatus)
                .toList());
        return result;
    }

    private static Map<String, Object> projectNode(TraceNodeVO node) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", blankIfNull(node.getTitle()));
        result.put("timestamp", node.getTimestamp());
        result.put("status", blankIfNull(node.getStatus()));
        result.put("costTime", node.getCostTime());
        result.put("description", blankIfNull(node.getDescription()));
        return result;
    }

    private static Map<String, Object> projectConsumerStatus(ConsumerStatusVO status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("group", blankIfNull(status.getGroup()));
        result.put("deliveryStatus",
                status.getDeliveryStatus() == null ? "" : status.getDeliveryStatus().name());
        result.put("consumeTime", status.getConsumeTime());
        result.put("retryCount", status.getRetryCount());
        return result;
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
