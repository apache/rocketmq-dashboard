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
package org.apache.rocketmq.studio.ops.ai.tool.handler.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.ops.ai.tool.contract.alert.NotificationDeliveryItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.alert.NotificationDeliveryListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageRequest;
import org.apache.rocketmq.studio.ops.alert.AlertDomain;
import org.apache.rocketmq.studio.ops.alert.NotificationDeliveryPageVO;
import org.apache.rocketmq.studio.ops.alert.NotificationOutboxService;
import org.apache.rocketmq.studio.ops.alert.NotificationOutboxStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryListToolHandlerTest {

    @Mock
    private NotificationOutboxService notificationOutboxService;

    @InjectMocks
    private NotificationDeliveryListToolHandler handler;

    @Test
    void executeShouldForwardFiltersAndExposeFailedDeliveryDiagnosticsTest() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 22, 8, 0);
        NotificationDeliveryPageVO delivery = NotificationDeliveryPageVO.builder()
                .id(51L)
                .alertId(41L)
                .channel("email")
                .status(NotificationOutboxStatus.FAILED)
                .attemptCount(3)
                .createdAt(createdAt)
                .lastError("SMTP unavailable")
                .alertTitle("Consumer lag")
                .alertDomain(AlertDomain.CLUSTER)
                .transition("FIRING")
                .instanceId("instance-a")
                .messageContent("private notification payload")
                .build();
        when(notificationOutboxService.listDeliveries("email", "FAILED", "instance-a", 1, 20))
                .thenReturn(PageResult.of(List.of(delivery), 1, 1, 20));

        PageOutput<NotificationDeliveryItem> output = handler.execute(
                new NotificationDeliveryListInput("email", "FAILED", "instance-a", new PageRequest(1, 20)),
                context("instance-a"));

        assertThat(output.total()).isEqualTo(1);
        assertThat(output.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(51L);
            assertThat(item.status()).isEqualTo("FAILED");
            assertThat(item.attemptCount()).isEqualTo(3);
            assertThat(item.lastError()).isEqualTo("SMTP unavailable");
            assertThat(item.alertDomain()).isEqualTo("CLUSTER");
        });
        assertThat(new ObjectMapper().writeValueAsString(output))
                .doesNotContain("messageContent")
                .doesNotContain("private notification payload");
        verify(notificationOutboxService).listDeliveries("email", "FAILED", "instance-a", 1, 20);
    }
}
