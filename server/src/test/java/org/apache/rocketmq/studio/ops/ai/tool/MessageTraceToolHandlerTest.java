/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.rocketmq.studio.ops.ai.tool;

import org.apache.rocketmq.studio.common.domain.enums.DeliveryStatus;
import org.apache.rocketmq.studio.instance.message.ConsumerStatusVO;
import org.apache.rocketmq.studio.instance.message.MessageProvider;
import org.apache.rocketmq.studio.instance.message.TraceNodeVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageTraceToolHandlerTest {

    @Mock
    private MessageProvider messageProvider;

    @InjectMocks
    private MessageTraceToolHandler handler;

    @Test
    void executeShouldProjectTraceTimeline() {
        TraceRecordVO trace = TraceRecordVO.builder()
                .nodes(List.of(TraceNodeVO.builder()
                        .title("produce")
                        .timestamp(1000L)
                        .status("finish")
                        .costTime(5L)
                        .description("prod-group")
                        .build()))
                .consumerStatus(List.of(ConsumerStatusVO.builder()
                        .group("cons-group")
                        .deliveryStatus(DeliveryStatus.success)
                        .consumeTime(2000L)
                        .retryCount(0)
                        .build()))
                .build();
        when(messageProvider.getMessageTrace("msg-1")).thenReturn(trace);

        Object result = handler.execute(Map.of("cluster", "cluster-1", "msgId", "msg-1"));

        assertThat(result).isInstanceOf(Map.class);
        Map<?, ?> map = (Map<?, ?>) result;
        assertThat(map.get("msgId")).isEqualTo("msg-1");

        List<?> nodes = (List<?>) map.get("nodes");
        assertThat(nodes).hasSize(1);
        Map<?, ?> node = (Map<?, ?>) nodes.get(0);
        assertThat(node.get("title")).isEqualTo("produce");
        assertThat(node.get("status")).isEqualTo("finish");
        assertThat(node.get("timestamp")).isEqualTo(1000L);

        List<?> consumers = (List<?>) map.get("consumerStatus");
        assertThat(consumers).hasSize(1);
        Map<?, ?> consumer = (Map<?, ?>) consumers.get(0);
        assertThat(consumer.get("group")).isEqualTo("cons-group");
        assertThat(consumer.get("deliveryStatus")).isEqualTo("success");
        assertThat(consumer.get("retryCount")).isEqualTo(0);
    }
}
