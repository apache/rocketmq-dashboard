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
package com.rocketmq.studio.instance.dlq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class DLQProviderStub implements DLQProvider {

    private final List<StubDLQGroup> stubData = List.of(
            new StubDLQGroup("rmq-cn-v5-prod-01", DLQGroupVO.builder()
                    .groupName("cg-order-payment")
                    .dlqTopic("%DLQ%cg-order-payment")
                    .messageCount(128)
                    .lastEnqueueTime(LocalDateTime.of(2026, 7, 24, 10, 15, 30))
                    .retryCount(16)
                    .status("ACTIVE")
                    .build()),
            new StubDLQGroup("rmq-cn-v5-prod-01", DLQGroupVO.builder()
                    .groupName("cg-inventory-sync")
                    .dlqTopic("%DLQ%cg-inventory-sync")
                    .messageCount(24)
                    .lastEnqueueTime(LocalDateTime.of(2026, 7, 24, 9, 40, 12))
                    .retryCount(8)
                    .status("ACTIVE")
                    .build()),
            new StubDLQGroup("rmq-cn-v4-prod-02", DLQGroupVO.builder()
                    .groupName("legacy-order-consumer")
                    .dlqTopic("%DLQ%legacy-order-consumer")
                    .messageCount(6)
                    .lastEnqueueTime(LocalDateTime.of(2026, 7, 23, 22, 5, 0))
                    .retryCount(3)
                    .status("ACKED")
                    .build())
    );

    @Override
    public List<DLQGroupVO> listDLQGroups(String clusterId) {
        log.info("DLQProviderStub.listDLQGroups called. clusterId={}", clusterId);
        return stubData.stream()
                .filter(item -> !StringUtils.hasText(clusterId) || item.clusterId().equals(clusterId))
                .map(StubDLQGroup::group)
                .map(DLQProviderStub::copyGroup)
                .toList();
    }

    @Override
    public void resendMessages(String groupName, Long startTime, Long endTime, String targetTopic) {
        log.warn("DLQProviderStub.resendMessages called - no-op. group={}, targetTopic={}", groupName, targetTopic);
    }

    private static DLQGroupVO copyGroup(DLQGroupVO group) {
        return DLQGroupVO.builder()
                .groupName(group.getGroupName())
                .dlqTopic(group.getDlqTopic())
                .messageCount(group.getMessageCount())
                .lastEnqueueTime(group.getLastEnqueueTime())
                .retryCount(group.getRetryCount())
                .status(group.getStatus())
                .build();
    }

    private record StubDLQGroup(String clusterId, DLQGroupVO group) {
    }
}
