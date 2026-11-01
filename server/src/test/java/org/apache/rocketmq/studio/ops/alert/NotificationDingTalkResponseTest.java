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

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Stream;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.metrics.AlertingProperties;
import org.apache.rocketmq.studio.common.domain.enums.AlertLevel;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertNotificationOutbox;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertNotificationOutboxMapper;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NotificationDingTalkResponseTest {
    private final RmqAlertNotificationOutboxMapper mapper = mock(RmqAlertNotificationOutboxMapper.class);
    private final SettingsRepository settings = mock(SettingsRepository.class);
    private final AlertRepository alerts = mock(AlertRepository.class);
    private NotificationOutboxService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUpTest() {
        RestTemplate client = new RestTemplate();
        server = MockRestServiceServer.bindTo(client).build();
        when(settings.loadGeneralSettings()).thenReturn(GeneralSettingsVO.builder()
                .dingtalkWebhook("https://example.com/hook").build());
        service = new NotificationOutboxService(mapper, settings, mock(AlertSilenceService.class), alerts,
                mock(OperationAuditService.class), client, () -> null,
                new AlertingProperties(), mock(ScheduledExecutorService.class));
    }

    @AfterEach
    void closeTest() {
        service.closeHeartbeatExecutor();
    }

    static Stream<Arguments> responses() {
        return Stream.of(
                Arguments.of("{\"errcode\":0}", true),
                Arguments.of("{\"errcode\":0.0}", true),
                Arguments.of("{\"errcode\":-0.0}", true),
                Arguments.of("{\"errcode\":0e-400}", true),
                Arguments.of("{\"errcode\":0.5}", false),
                Arguments.of("{\"errcode\":-0.5}", false),
                Arguments.of("{\"errcode\":1e-400}", false),
                Arguments.of("{\"errcode\":-1e-400}", false),
                Arguments.of("{\"errcode\":310000,\"errmsg\":\"rejected\"}", false),
                Arguments.of("{\"errcode\":2147483648}", false),
                Arguments.of("{}", false),
                Arguments.of("{\"errcode\":null}", false),
                Arguments.of("{\"errcode\":\"0\"}", false),
                Arguments.of("accepted", false));
    }

    @ParameterizedTest
    @MethodSource("responses")
    void testNotificationRequiresANumericZeroResponseTest(String response, boolean accepted) {
        respondWith(response);

        if (accepted) {
            assertThatCode(() -> service.sendTestMessage("dingtalk")).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> service.sendTestMessage("dingtalk"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("Test notification failed:");
        }

        server.verify();
    }

    @ParameterizedTest
    @MethodSource("responses")
    void queuedDeliveryRequiresANumericZeroResponseTest(String response, boolean accepted) {
        RmqAlertNotificationOutbox row = new RmqAlertNotificationOutbox();
        row.setId(8L);
        row.setAlertId(9L);
        row.setChannel("dingtalk");
        row.setStatus("PENDING");
        row.setAttemptCount(0);
        when(mapper.findDispatchable(any(LocalDateTime.class), any(LocalDateTime.class), any(Integer.class)))
                .thenReturn(List.of(row));
        when(mapper.claimForDispatch(any(), any(LocalDateTime.class), any(LocalDateTime.class),
                any(LocalDateTime.class), anyString(), anyInt())).thenReturn(1);
        when(mapper.update(any(), any())).thenReturn(1);
        when(alerts.findAlertById(9L)).thenReturn(Optional.of(SystemAlertVO.builder().id(9L)
                .level(AlertLevel.warning).title("Lag").description("high").instanceId("local").build()));
        respondWith(response);

        service.dispatch();

        server.verify();
        assertStatus(accepted ? "DELIVERED" : "RETRY_WAIT");
    }

    private void respondWith(String response) {
        server.expect(requestTo("https://example.com/hook"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertStatus(String expected) {
        ArgumentCaptor<UpdateWrapper> update = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(any(), update.capture());
        update.getValue().getSqlSet();
        assertThat(update.getValue().getParamNameValuePairs().values()).contains(expected);
    }
}
