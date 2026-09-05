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
package org.apache.rocketmq.studio.ops.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.enums.AlertLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemAlertController.class)
@AutoConfigureMockMvc(addFilters = false)
class SystemAlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AlertService alertService;

    @MockBean
    private NotificationOutboxService notificationOutboxService;

    @Test
    void listAlertsShouldReturnSystemAlertsTest() throws Exception {
        SystemAlertVO alert = SystemAlertVO.builder()
                .id(1L)
                .level(AlertLevel.error)
                .title("Broker Down")
                .acknowledged(false)
                .build();
        when(alertService.listAlerts("error", null, null, null)).thenReturn(List.of(alert));

        mockMvc.perform(get("/api/system-alerts").param("level", "error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].level").value("error"))
                .andExpect(jsonPath("$.data[0].acknowledged").value(false));

        verify(alertService).listAlerts("error", null, null, null);
    }

    @Test
    void listAlertsPageShouldForwardFiltersAndPagingTest() throws Exception {
        SystemAlertVO alert = SystemAlertVO.builder().id(2L).level(AlertLevel.warning).build();
        LocalDateTime from = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 8, 2, 0, 0);
        when(alertService.listAlerts("warning", AlertDomain.BUSINESS, "local", "FIRING",
                "brokerName", "broker-a", from, to, 2, 10, true))
                .thenReturn(PageResult.of(List.of(alert), 11, 2, 10));

        mockMvc.perform(get("/api/system-alerts/page").param("level", "warning")
                        .param("domain", "BUSINESS").param("instanceId", "local")
                        .param("transition", "FIRING").param("labelKey", "brokerName")
                        .param("labelValue", "broker-a").param("from", "2026-08-01T00:00")
                        .param("to", "2026-08-02T00:00").param("notificationSuppressed", "true")
                        .param("page", "2").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(11))
                .andExpect(jsonPath("$.data.items[0].id").value(2));

        verify(alertService).listAlerts("warning", AlertDomain.BUSINESS, "local", "FIRING",
                "brokerName", "broker-a", from, to, 2, 10, true);
    }

    @Test
    void relatedAlertsShouldReturnCrossDomainEventsTest() throws Exception {
        SystemAlertVO related = SystemAlertVO.builder().id(2L).domain(AlertDomain.CLUSTER)
                .title("Broker unavailable").transition("FIRING").build();
        when(alertService.findRelatedAlerts(1L)).thenReturn(List.of(related));

        mockMvc.perform(get("/api/system-alerts/1/related"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(2))
                .andExpect(jsonPath("$.data[0].domain").value("CLUSTER"));

        verify(alertService).findRelatedAlerts(1L);
    }

    @Test
    void listDeliveriesPageShouldForwardFiltersAndPagingTest() throws Exception {
        NotificationDeliveryPageVO delivery = NotificationDeliveryPageVO.builder().id(8L).alertId(9L)
                .channel("dingtalk").status(NotificationOutboxStatus.DELIVERED).attemptCount(0)
                .alertTitle("Disk usage high").instanceId("local")
                .messageContent("[info] Disk usage high").build();
        when(notificationOutboxService.listDeliveries("dingtalk", "DELIVERED", "local", 2, 10))
                .thenReturn(PageResult.of(List.of(delivery), 11, 2, 10));

        mockMvc.perform(get("/api/system-alerts/deliveries/page").param("channel", "dingtalk")
                        .param("status", "DELIVERED").param("instanceId", "local")
                        .param("page", "2").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(11))
                .andExpect(jsonPath("$.data.items[0].channel").value("dingtalk"))
                .andExpect(jsonPath("$.data.items[0].alertTitle").value("Disk usage high"))
                .andExpect(jsonPath("$.data.items[0].messageContent").value("[info] Disk usage high"));

        verify(notificationOutboxService).listDeliveries("dingtalk", "DELIVERED", "local", 2, 10);
    }

    @Test
    void retryFailedDeliveryShouldForwardDeliveryIdTest() throws Exception {
        mockMvc.perform(post("/api/system-alerts/deliveries/8/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(notificationOutboxService).retryFailedDelivery(8L);
    }

    @Test
    void retryFailedDeliveriesShouldReturnPartialResultTest() throws Exception {
        NotificationDeliveryBulkRetryResult result = new NotificationDeliveryBulkRetryResult(
                List.of(8L), Map.of(9L, "Delivery is not failed"));
        when(notificationOutboxService.retryFailedDeliveries(List.of(8L, 9L))).thenReturn(result);

        mockMvc.perform(post("/api/system-alerts/deliveries/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(8L, 9L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeededIds[0]").value(8))
                .andExpect(jsonPath("$.data.failures.9").value("Delivery is not failed"));

        verify(notificationOutboxService).retryFailedDeliveries(List.of(8L, 9L));
    }

    @Test
    void acknowledgeAlertShouldPassValidatedRequestTest() throws Exception {
        SystemAlertVO acknowledged = SystemAlertVO.builder()
                .id(1L)
                .level(AlertLevel.warning)
                .title("High Lag")
                .acknowledged(true)
                .build();
        when(alertService.acknowledgeAlert(1L)).thenReturn(acknowledged);

        mockMvc.perform(post("/api/system-alerts/acknowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.acknowledged").value(true));

        verify(alertService).acknowledgeAlert(1L);
    }

    @Test
    void acknowledgeAlertShouldRejectNullRequestBodyTest() throws Exception {
        mockMvc.perform(post("/api/system-alerts/acknowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("System alert acknowledge request is required"));

        verifyNoInteractions(alertService);
    }

    @Test
    void acknowledgeAlertShouldRejectBlankIdTest() throws Exception {
        mockMvc.perform(post("/api/system-alerts/acknowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Collections.singletonMap("id", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id is required"));

        verifyNoInteractions(alertService);
    }

    @Test
    void acknowledgeAlertShouldRejectMissingIdTest() throws Exception {
        mockMvc.perform(post("/api/system-alerts/acknowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id is required"));

        verifyNoInteractions(alertService);
    }

    @Test
    void clearAcknowledgedShouldReturnClearedCountTest() throws Exception {
        when(alertService.clearAcknowledged()).thenReturn(3);

        mockMvc.perform(post("/api/system-alerts/clear-acknowledged"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.cleared").value(3));

        verify(alertService).clearAcknowledged();
    }
    @Test
    void listRelatedAlertsShouldForwardAlertId() throws Exception {
        when(alertService.findRelatedAlerts(9L)).thenReturn(List.of());

        mockMvc.perform(get("/api/system-alerts/9/related"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(alertService).findRelatedAlerts(9L);
    }

    @Test
    void retryFailedDeliveryShouldDelegateDeliveryId() throws Exception {
        mockMvc.perform(post("/api/system-alerts/deliveries/5/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(notificationOutboxService).retryFailedDelivery(5L);
    }

    @Test
    void acknowledgeShouldReturnAcknowledgedAlert() throws Exception {
        SystemAlertVO acknowledged = SystemAlertVO.builder()
                .id(21L)
                .title("disk-high")
                .build();
        when(alertService.acknowledgeAlert(21L)).thenReturn(acknowledged);

        mockMvc.perform(post("/api/system-alerts/acknowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":21}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(21));

        verify(alertService).acknowledgeAlert(21L);
    }

}
