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

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.common.domain.enums.AlertLevel;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertNotificationOutbox;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertNotificationOutboxMapper;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NotificationWebhookEncodingTest {
    private static final String WEBHOOK = "https://192.0.2.1/robot/send";
    private static final String SYNTHETIC_SECRET = "synthetic-test-secret";

    private final RmqAlertNotificationOutboxMapper mapper = mock(RmqAlertNotificationOutboxMapper.class);
    private final SettingsRepository settings = mock(SettingsRepository.class);
    private final AlertRepository alerts = mock(AlertRepository.class);
    private final OperationAuditService audit = mock(OperationAuditService.class);
    private final RestTemplate client = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
    private final NotificationOutboxService service = new NotificationOutboxService(mapper, settings,
            mock(AlertSilenceService.class), alerts, audit, client);

    @AfterEach
    void close() {
        service.closeHeartbeatExecutor();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?access_token=synthetic", "?access_token=a%2Bb%2Fc%3D"})
    void signsTestNotificationsWithExactlyOneLayerOfEncodingTest(String query) throws Exception {
        when(settings.loadGeneralSettings()).thenReturn(GeneralSettingsVO.builder()
                .dingtalkWebhook(WEBHOOK + query).dingtalkSigningSecret(SYNTHETIC_SECRET).build());
        AtomicReference<URI> requestUri = new AtomicReference<>();
        server.expect(request -> requestUri.set(request.getURI()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"errcode\":0}", MediaType.APPLICATION_JSON));

        service.sendTestMessage("dingtalk");

        server.verify();
        assertValidSignature(requestUri.get());
        if (!query.isEmpty()) {
            assertThat(requestUri.get().getRawQuery()).startsWith(query.substring(1) + "&timestamp=");
        }
    }

    @Test
    void signsQueuedDeliveriesWithExactlyOneLayerOfEncodingTest() throws Exception {
        when(settings.loadGeneralSettings()).thenReturn(GeneralSettingsVO.builder()
                .dingtalkWebhook(WEBHOOK + "?access_token=synthetic")
                .dingtalkSigningSecret(SYNTHETIC_SECRET).build());
        RmqAlertNotificationOutbox row = new RmqAlertNotificationOutbox();
        row.setId(8L);
        row.setAlertId(9L);
        row.setChannel("dingtalk");
        row.setStatus("PENDING");
        row.setAttemptCount(0);
        when(mapper.findDispatchable(any(LocalDateTime.class), any(LocalDateTime.class), any(Integer.class)))
                .thenReturn(List.of(row));
        when(mapper.claimForDispatch(any(), any(LocalDateTime.class), any(LocalDateTime.class),
                any(LocalDateTime.class), anyString())).thenReturn(1);
        when(mapper.update(any(), any())).thenReturn(1);
        when(alerts.findAlertById(9L)).thenReturn(Optional.of(SystemAlertVO.builder().id(9L)
                .level(AlertLevel.warning).title("Lag").description("high").instanceId("local").build()));
        AtomicReference<URI> requestUri = new AtomicReference<>();
        server.expect(request -> requestUri.set(request.getURI()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"errcode\":0}", MediaType.APPLICATION_JSON));

        service.dispatch();

        server.verify();
        assertValidSignature(requestUri.get());
        verify(mapper).update(any(), any());
        verify(audit).record("DELIVER_ALERT_NOTIFICATION", "ALERT_NOTIFICATION", "8", null,
                "alertId=9, channel=dingtalk", "SUCCESS", null);
    }

    @ParameterizedTest
    @MethodSource("unsignedWebhooks")
    void preservesUnsignedWebhookUrisTest(String channel, String query) {
        // A configured DingTalk secret must not add signing parameters to SMS requests.
        when(settings.loadGeneralSettings()).thenReturn(GeneralSettingsVO.builder()
                .dingtalkWebhook(WEBHOOK + query).smsWebhook(WEBHOOK + query)
                .dingtalkSigningSecret("sms".equals(channel) ? SYNTHETIC_SECRET : null).build());
        server.expect(requestTo(WEBHOOK + query))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("dingtalk".equals(channel) ? "{\"errcode\":0}" : "accepted",
                        "dingtalk".equals(channel) ? MediaType.APPLICATION_JSON : MediaType.TEXT_PLAIN));

        service.sendTestMessage(channel);

        server.verify();
    }

    private static Stream<Arguments> unsignedWebhooks() {
        return Stream.of("dingtalk", "sms").flatMap(channel -> Stream.of("", "?token=synthetic",
                "?token=a%2Bb%2Fc%3D&label=hello%20world").map(query -> Arguments.of(channel, query)));
    }

    private static void assertValidSignature(URI uri) throws Exception {
        Map<String, String> rawQuery = Arrays.stream(uri.getRawQuery().split("&"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1]));
        String timestamp = rawQuery.get("timestamp");
        assertThat(timestamp).matches("[0-9]+");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SYNTHETIC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = Base64.getEncoder().encodeToString(
                mac.doFinal((timestamp + "\n" + SYNTHETIC_SECRET).getBytes(StandardCharsets.UTF_8)));
        // Inspect the real RestTemplate request, not the URL before Spring processes it.
        assertThat(URLDecoder.decode(rawQuery.get("sign"), StandardCharsets.UTF_8)).isEqualTo(expected);
        assertThat(rawQuery.get("sign")).isEqualTo(URLEncoder.encode(expected, StandardCharsets.UTF_8));
        assertThat(rawQuery.get("sign")).endsWith("%3D");
    }
}
