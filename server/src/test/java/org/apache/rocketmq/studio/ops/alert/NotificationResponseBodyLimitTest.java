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

import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.common.domain.enums.AlertLevel;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertNotificationOutbox;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertNotificationOutboxMapper;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NotificationResponseBodyLimitTest {
    private static final int LIMIT = 5 * 1024 * 1024;
    private static final String WEBHOOK_URL = "https://192.0.2.1/hook";

    private final RmqAlertNotificationOutboxMapper mapper = mock(RmqAlertNotificationOutboxMapper.class);
    private final SettingsRepository settings = mock(SettingsRepository.class);
    private final AlertRepository alerts = mock(AlertRepository.class);
    private final OperationAuditService audit = mock(OperationAuditService.class);
    private NotificationOutboxService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUpTest() {
        RestTemplate client = new RestTemplate();
        server = MockRestServiceServer.bindTo(client).build();
        service = new NotificationOutboxService(mapper, settings, mock(AlertSilenceService.class), alerts, audit, client);
        when(settings.loadGeneralSettings()).thenReturn(GeneralSettingsVO.builder()
                .dingtalkWebhook(WEBHOOK_URL).smsWebhook(WEBHOOK_URL).build());
    }

    @AfterEach
    void tearDownTest() {
        service.closeHeartbeatExecutor();
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"200,false", "200,true", "400,false", "400,true", "500,false"})
    void testNotificationShouldRejectOversizedSuccessAndErrorBodiesTest(int status, boolean declaredLength) {
        byte[] oversizedJson = ("{\"errcode\":0}" + " ".repeat(LIMIT + 1)).getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream body = new ByteArrayInputStream(oversizedJson);
        MockClientHttpResponse response = spy(new MockClientHttpResponse(body, HttpStatus.valueOf(status)));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (declaredLength) {
            response.getHeaders().setContentLength(oversizedJson.length);
        }
        server.expect(requestTo(WEBHOOK_URL)).andRespond(request -> response);

        assertThatThrownBy(() -> service.sendTestMessage("dingtalk"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Test notification failed: HTTP response body exceeds " + LIMIT + " bytes");

        assertThat(body.available()).isEqualTo(declaredLength ? oversizedJson.length
                : oversizedJson.length - LIMIT - 1);
        verify(response, atLeastOnce()).close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dingtalk", "sms"})
    void acceptsAWebhookBodyExactlyAtTheLimitTest(String channel) {
        String json = "{\"errcode\":0}";
        server.expect(requestTo(WEBHOOK_URL))
                .andRespond(withSuccess(json + " ".repeat(LIMIT - json.length()), MediaType.APPLICATION_JSON));

        assertThatCode(() -> service.sendTestMessage(channel)).doesNotThrowAnyException();
    }

    @Test
    void preservesDingTalkErrorDetailsTest() {
        server.expect(requestTo(WEBHOOK_URL)).andRespond(withSuccess(
                "{\"errcode\":310000,\"errmsg\":\"keywords not in content\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.sendTestMessage("dingtalk"))
                .hasMessage("Test notification failed: DingTalk rejected webhook: keywords not in content");
    }

    @Test
    void preservesHttpErrorHandlingForSmallWebhookBodiesTest() {
        server.expect(requestTo(WEBHOOK_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.TEXT_PLAIN).body("temporarily unavailable"));

        assertThatThrownBy(() -> service.sendTestMessage("sms"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("503").hasMessageContaining("temporarily unavailable");
    }

    @ParameterizedTest
    @CsvSource({"200,0", "500,0", "200,4", "500,4"})
    void oversizedBodiesShouldFollowTheExistingRetryAndExhaustionPolicyTest(int status, int attempts) {
        RmqAlertNotificationOutbox row = new RmqAlertNotificationOutbox();
        row.setId(8L);
        row.setAlertId(9L);
        row.setChannel("dingtalk");
        row.setStatus("PENDING");
        row.setAttemptCount(attempts);
        when(mapper.findDispatchable(any(LocalDateTime.class), any(LocalDateTime.class), any(Integer.class)))
                .thenReturn(List.of(row));
        when(mapper.claimForDispatch(any(), any(LocalDateTime.class), any(LocalDateTime.class),
                any(LocalDateTime.class), anyString(), eq(attempts))).thenReturn(1);
        when(mapper.update(any(), any())).thenReturn(1);
        when(alerts.findAlertById(9L)).thenReturn(Optional.of(SystemAlertVO.builder().id(9L)
                .level(AlertLevel.warning).title("Lag").description("high").instanceId("local").build()));
        MockClientHttpResponse response = spy(new MockClientHttpResponse(
                new ByteArrayInputStream(("{\"errcode\":0}" + " ".repeat(LIMIT + 1))
                        .getBytes(StandardCharsets.UTF_8)), HttpStatus.valueOf(status)));
        server.expect(requestTo(WEBHOOK_URL)).andRespond(request -> response);

        service.dispatch();

        verify(mapper).update(any(), any());
        verify(audit).record(attempts == 4 ? "FAIL_ALERT_NOTIFICATION" : "RETRY_ALERT_NOTIFICATION",
                "ALERT_NOTIFICATION", "8", null, "alertId=9, channel=dingtalk",
                "FAILED", "HTTP response body exceeds " + LIMIT + " bytes");
        verify(response, atLeastOnce()).close();
    }

    @Test
    void webhookClientShouldKeepTimeoutsAndRedirectPolicyTest() {
        RestTemplate client = ReflectionTestUtils.invokeMethod(NotificationOutboxService.class, "newClient");
        assertThat(client).isNotNull();
        HttpURLConnection connection = mock(HttpURLConnection.class);

        ReflectionTestUtils.invokeMethod(client.getRequestFactory(), "prepareConnection", connection, "POST");

        verify(connection).setConnectTimeout(3000);
        verify(connection).setReadTimeout(5000);
        verify(connection, atLeastOnce()).setInstanceFollowRedirects(false);
    }
}
