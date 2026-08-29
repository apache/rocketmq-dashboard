/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.metrics.AlertingProperties;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.util.NoRedirectClientHttpRequestFactory;
import org.apache.rocketmq.studio.common.util.UrlHostGuard;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertNotificationOutbox;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertNotificationOutboxMapper;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.function.Supplier;
import java.util.UUID;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

/** Persists notification work so collection never blocks on remote webhook availability. */
@Slf4j
@Service
public class NotificationOutboxService {
    private static final int MAX_ATTEMPTS = 5;
    private static final int BATCH_SIZE = 20;
    private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(1);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RmqAlertNotificationOutboxMapper mapper;
    private final SettingsRepository settingsRepository;
    private final AlertSilenceService silenceService;
    private final AlertRepository alertRepository;
    private final OperationAuditService operationAuditService;
    private final RestTemplate restTemplate;
    private final Supplier<JavaMailSender> mailSender;
    private final AlertingProperties alertingProperties;

    NotificationOutboxService(RmqAlertNotificationOutboxMapper mapper, SettingsRepository settingsRepository,
            AlertSilenceService silenceService, AlertRepository alertRepository,
            OperationAuditService operationAuditService) {
        this(mapper, settingsRepository, silenceService, alertRepository, operationAuditService, newClient(),
                () -> null, new AlertingProperties());
    }

    @Autowired
    public NotificationOutboxService(RmqAlertNotificationOutboxMapper mapper, SettingsRepository settingsRepository,
            AlertSilenceService silenceService, AlertRepository alertRepository,
            OperationAuditService operationAuditService, ObjectProvider<JavaMailSender> mailSender,
            AlertingProperties alertingProperties) {
        this(mapper, settingsRepository, silenceService, alertRepository, operationAuditService, newClient(),
                mailSender::getIfAvailable, alertingProperties);
    }

    NotificationOutboxService(RmqAlertNotificationOutboxMapper mapper, SettingsRepository settingsRepository,
            AlertSilenceService silenceService, AlertRepository alertRepository,
            OperationAuditService operationAuditService, RestTemplate restTemplate) {
        this(mapper, settingsRepository, silenceService, alertRepository, operationAuditService, restTemplate,
                () -> null, new AlertingProperties());
    }

    NotificationOutboxService(RmqAlertNotificationOutboxMapper mapper, SettingsRepository settingsRepository,
            AlertSilenceService silenceService, AlertRepository alertRepository,
            OperationAuditService operationAuditService, AlertingProperties alertingProperties) {
        this(mapper, settingsRepository, silenceService, alertRepository, operationAuditService, newClient(),
                () -> null, alertingProperties);
    }

    NotificationOutboxService(RmqAlertNotificationOutboxMapper mapper, SettingsRepository settingsRepository,
            AlertSilenceService silenceService, AlertRepository alertRepository,
            OperationAuditService operationAuditService, RestTemplate restTemplate,
            Supplier<JavaMailSender> mailSender) {
        this(mapper, settingsRepository, silenceService, alertRepository, operationAuditService, restTemplate,
                mailSender, new AlertingProperties());
    }

    NotificationOutboxService(RmqAlertNotificationOutboxMapper mapper, SettingsRepository settingsRepository,
            AlertSilenceService silenceService, AlertRepository alertRepository,
            OperationAuditService operationAuditService, RestTemplate restTemplate,
            Supplier<JavaMailSender> mailSender, AlertingProperties alertingProperties) {
        this.mapper = mapper;
        this.settingsRepository = settingsRepository;
        this.silenceService = silenceService;
        this.alertRepository = alertRepository;
        this.operationAuditService = operationAuditService;
        this.restTemplate = restTemplate;
        this.mailSender = mailSender;
        this.alertingProperties = alertingProperties == null ? new AlertingProperties() : alertingProperties;
    }

    public void enqueue(SystemAlertVO alert, AlertRuleVO rule) {
        enqueue(alert, rule, Map.of());
    }

    public void sendTestMessage(String channel) {
        if (!"dingtalk".equals(channel) && !"email".equals(channel) && !"sms".equals(channel)) {
            throw new IllegalArgumentException("Unsupported notification channel: " + channel);
        }
        GeneralSettingsVO settings = settingsRepository.loadGeneralSettings();
        SystemAlertVO alert = SystemAlertVO.builder().level(org.apache.rocketmq.studio.common.domain.enums.AlertLevel.info)
                .title("RocketMQ Studio test notification").description("DingTalk notification configuration is working.")
                .build();
        try {
            String content = AlertNotificationTemplate.render(null, alert, null);
            if ("email".equals(channel)) sendEmail(settings, alert, content);
            else sendWebhook(settings, alert, channel, content);
        } catch (Exception error) {
            throw new IllegalStateException("Test notification failed: " + error.getMessage(), error);
        }
    }

    public void enqueue(SystemAlertVO alert, AlertRuleVO rule, Map<String, String> labels) {
        if (alert.getId() == null) {
            throw new IllegalStateException("Cannot enqueue notification for an alert without a persistent ID");
        }
        Map<String, String> effectiveLabels = labels == null ? Map.of() : labels;
        LocalDateTime silenceEndsAt = silenceService.activeUntil(rule, alert.getInstanceId(), effectiveLabels,
                alert.getTime());
        Set<String> channels = new LinkedHashSet<>();
        if (rule.getChannels() != null) {
            rule.getChannels().stream().filter(StringUtils::hasText)
                    .map(value -> value.trim().toLowerCase(Locale.ROOT)).forEach(channels::add);
        }
        for (String channel : channels) {
            if (!"dingtalk".equals(channel) && !"sms".equals(channel) && !"email".equals(channel)) {
                continue;
            }
            RmqAlertNotificationOutbox row = new RmqAlertNotificationOutbox();
            row.setAlertId(alert.getId());
            row.setChannel(channel);
            row.setStatus(NotificationOutboxStatus.PENDING.name());
            row.setAttemptCount(0);
            row.setMessageContent(AlertNotificationTemplate.render(rule.getNotificationTemplate(), alert, rule));
            row.setNextAttemptAt(silenceEndsAt == null ? utcNow() : silenceEndsAt);
            mapper.insert(row);
        }
    }

    public List<NotificationDeliveryVO> listDeliveries(Long alertId) {
        if (alertId == null) {
            throw new org.apache.rocketmq.studio.common.exception.BusinessException(400, "Alert ID is required");
        }
        return mapper.selectList(new QueryWrapper<RmqAlertNotificationOutbox>().eq("alert_id", alertId)
                        .orderByAsc("id"))
                .stream().map(row -> NotificationDeliveryVO.builder().id(row.getId()).channel(row.getChannel())
                        .status(NotificationOutboxStatus.valueOf(row.getStatus()))
                        .attemptCount(row.getAttemptCount() == null ? 0 : row.getAttemptCount())
                        .nextAttemptAt(row.getNextAttemptAt()).lastError(row.getLastError())
                        .deliveredAt(row.getDeliveredAt()).build()).toList();
    }

    public PageResult<NotificationDeliveryPageVO> listDeliveries(String channel, String status, String instanceId,
            int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        String normalizedChannel = normalizeFilter(channel);
        String normalizedStatus = normalizeStatus(status);
        String normalizedInstanceId = normalizeTrim(instanceId);
        long total = mapper.countPage(normalizedChannel, normalizedStatus, normalizedInstanceId);
        if (total == 0) {
            return PageResult.empty(safePage, safePageSize);
        }
        return PageResult.of(mapper.findPage(normalizedChannel, normalizedStatus, normalizedInstanceId, safePageSize,
                (long) (safePage - 1) * safePageSize), total, safePage, safePageSize);
    }

    public void retryFailedDelivery(Long deliveryId) {
        if (deliveryId == null || deliveryId <= 0) {
            throw new org.apache.rocketmq.studio.common.exception.BusinessException(400,
                    "Notification delivery ID is required");
        }
        RmqAlertNotificationOutbox row = mapper.selectById(deliveryId);
        if (row == null || !NotificationOutboxStatus.FAILED.name().equals(row.getStatus())) {
            throw new org.apache.rocketmq.studio.common.exception.BusinessException(400,
                    "Only failed notification deliveries can be retried");
        }
        LocalDateTime now = utcNow();
        int updated = mapper.update(null, new UpdateWrapper<RmqAlertNotificationOutbox>()
                .set("status", NotificationOutboxStatus.PENDING.name()).set("attempt_count", 0)
                .set("next_attempt_at", now).set("sending_started_at", null).set("claim_token", null)
                .set("last_error", null).eq("id", deliveryId).eq("status", NotificationOutboxStatus.FAILED.name()));
        if (updated != 1) {
            throw new org.apache.rocketmq.studio.common.exception.BusinessException(400,
                    "Only failed notification deliveries can be retried");
        }
        recordDelivery(row, "RETRY_ALERT_NOTIFICATION_MANUALLY", "SUCCESS", null);
    }

    public NotificationDeliveryBulkRetryResult retryFailedDeliveries(List<Long> deliveryIds) {
        if (deliveryIds == null || deliveryIds.isEmpty() || deliveryIds.size() > 100) {
            throw new org.apache.rocketmq.studio.common.exception.BusinessException(400,
                    "Provide between 1 and 100 notification delivery IDs");
        }
        List<Long> succeeded = new ArrayList<>();
        Map<Long, String> failures = new java.util.LinkedHashMap<>();
        for (Long deliveryId : new LinkedHashSet<>(deliveryIds)) {
            try {
                retryFailedDelivery(deliveryId);
                succeeded.add(deliveryId);
            } catch (org.apache.rocketmq.studio.common.exception.BusinessException error) {
                failures.put(deliveryId, error.getMessage());
            }
        }
        return new NotificationDeliveryBulkRetryResult(succeeded, failures);
    }

    private static String normalizeFilter(String value) {
        String normalized = normalizeTrim(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeTrim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeStatus(String status) {
        String value = normalizeFilter(status);
        if (value == null) {
            return null;
        }
        try {
            return NotificationOutboxStatus.valueOf(value.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException error) {
            throw new org.apache.rocketmq.studio.common.exception.BusinessException(400,
                    "Unknown notification delivery status: " + status);
        }
    }

    @Scheduled(fixedDelayString = "${studio.alerting.notification-dispatch-interval:PT10S}")
    public void dispatch() {
        LocalDateTime now = utcNow();
        LocalDateTime staleBefore = now.minus(CLAIM_TIMEOUT);
        List<RmqAlertNotificationOutbox> due = mapper.findDispatchable(now, staleBefore, BATCH_SIZE);
        for (RmqAlertNotificationOutbox row : due) {
            String claimToken = UUID.randomUUID().toString();
            if (mapper.claimForDispatch(row.getId(), now, staleBefore, now, claimToken) != 1) {
                continue;
            }
            send(row, now, claimToken);
        }
    }

    @Scheduled(fixedDelayString = "${studio.alerting.notification-cleanup-interval:PT1H}")
    public int cleanupTerminalDeliveries() {
        Duration retention;
        try {
            retention = Duration.parse(alertingProperties.getNotificationRetention());
        } catch (RuntimeException error) {
            log.warn("Skipping alert notification cleanup because retention is invalid: {}",
                    alertingProperties.getNotificationRetention());
            return 0;
        }
        if (retention.isZero() || retention.isNegative()) {
            return 0;
        }
        int batchSize = Math.max(1, alertingProperties.getNotificationCleanupBatchSize());
        int maxBatches = Math.max(1, alertingProperties.getNotificationCleanupMaxBatches());
        LocalDateTime cutoff = utcNow().minus(retention);
        int total = 0;
        for (int batch = 0; batch < maxBatches; batch++) {
            int deleted;
            try {
                deleted = mapper.deleteTerminalBefore(cutoff, batchSize);
            } catch (RuntimeException error) {
                log.warn("Stopped alert notification cleanup after deleting {} rows", total, error);
                return total;
            }
            total += deleted;
            if (deleted < batchSize) {
                break;
            }
        }
        return total;
    }

    private void send(RmqAlertNotificationOutbox row, LocalDateTime now, String claimToken) {
        try {
            SystemAlertVO alert = loadAlert(row.getAlertId());
            LocalDateTime silenceEndsAt = silenceService.activeUntil(
                    AlertRuleVO.builder().id(alert.getRuleId()).domain(alert.getDomain()).build(),
                    alert.getInstanceId(), alert.getLabels(), now);
            if (silenceEndsAt != null) {
                deferUntilSilenceEnds(row, silenceEndsAt, claimToken);
                return;
            }
            GeneralSettingsVO settings = settingsRepository.loadGeneralSettings();
            if ("email".equals(row.getChannel())) {
                sendEmail(settings, alert, message(row, alert));
            } else {
                sendWebhook(settings, alert, row.getChannel(), message(row, alert));
            }
            if (!updateClaimed(row, claimToken, new UpdateWrapper<RmqAlertNotificationOutbox>()
                    .set("status", NotificationOutboxStatus.DELIVERED.name()).set("delivered_at", now)
                    .set("sending_started_at", null)
                    .set("last_error", null))) {
                return;
            }
            recordDelivery(row, "DELIVER_ALERT_NOTIFICATION", "SUCCESS", null);
        } catch (Exception error) {
            retry(row, now, claimToken, error.getMessage());
        }
    }

    private void deferUntilSilenceEnds(RmqAlertNotificationOutbox row, LocalDateTime silenceEndsAt, String claimToken) {
        updateClaimed(row, claimToken, new UpdateWrapper<RmqAlertNotificationOutbox>()
                .set("status", NotificationOutboxStatus.PENDING.name()).set("next_attempt_at", silenceEndsAt)
                .set("sending_started_at", null));
    }

    private void sendWebhook(GeneralSettingsVO settings, SystemAlertVO alert, String channel, String content) {
        String webhook = webhook(settings, channel);
        if (!StringUtils.hasText(webhook)) {
            throw new IllegalStateException("No configured " + channel + " webhook");
        }
        UrlHostGuard.check(webhook, false);
        ResponseEntity<String> response = restTemplate.postForEntity(dingTalkWebhook(webhook, settings, channel),
                payload(alert, channel, content), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Webhook returned " + response.getStatusCode());
        }
        if ("dingtalk".equals(channel)) {
            validateDingTalkResponse(response.getBody());
        }
    }

    private static String dingTalkWebhook(String webhook, GeneralSettingsVO settings, String channel) {
        if (!"dingtalk".equals(channel) || !StringUtils.hasText(settings.getDingtalkSigningSecret())) {
            return webhook;
        }
        long timestamp = System.currentTimeMillis();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(settings.getDingtalkSigningSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String sign = Base64.getEncoder().encodeToString(mac.doFinal((timestamp + "\n" + settings.getDingtalkSigningSecret()).getBytes(StandardCharsets.UTF_8)));
            return webhook + (webhook.contains("?") ? "&" : "?") + "timestamp=" + timestamp + "&sign="
                    + URLEncoder.encode(sign, StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to sign DingTalk webhook", error);
        }
    }

    private static void validateDingTalkResponse(String response) {
        try {
            JsonNode body = JSON.readTree(response);
            if (body != null && body.path("errcode").canConvertToInt() && body.path("errcode").asInt() == 0) {
                return;
            }
            String error = body == null ? null : body.path("errmsg").asText(null);
            throw new IllegalStateException("DingTalk rejected webhook: "
                    + (StringUtils.hasText(error == null ? null : error.toString()) ? error : "missing errcode"));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("DingTalk returned an invalid JSON response", error);
        }
    }

    private SystemAlertVO loadAlert(Long id) {
        return alertRepository.findAlertById(id)
                .orElseThrow(() -> new IllegalStateException("Alert event no longer exists: " + id));
    }

    private String webhook(GeneralSettingsVO settings, String channel) {
        return settings == null ? null : ("dingtalk".equals(channel)
                ? settings.getDingtalkWebhook() : settings.getSmsWebhook());
    }

    private void sendEmail(GeneralSettingsVO settings, SystemAlertVO alert, String content) throws AddressException {
        if (settings == null || !StringUtils.hasText(settings.getEmailRecipients())) {
            throw new IllegalStateException("No configured email recipients");
        }
        JavaMailSender sender = mailSender.get();
        if (sender == null) {
            throw new IllegalStateException("SMTP is not configured");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(parseRecipients(settings.getEmailRecipients()));
        message.setSubject("[RocketMQ Studio] " + alert.getTitle());
        message.setText(content);
        sender.send(message);
    }

    private static String[] parseRecipients(String raw) throws AddressException {
        List<String> recipients = new ArrayList<>();
        for (String value : raw.split("[,;]")) {
            String recipient = value.trim();
            if (!recipient.isEmpty()) {
                InternetAddress address = new InternetAddress(recipient, true);
                address.validate();
                recipients.add(address.getAddress());
            }
        }
        if (recipients.isEmpty()) {
            throw new AddressException("No valid email recipients");
        }
        return recipients.toArray(String[]::new);
    }

    private Map<String, Object> payload(SystemAlertVO alert, String channel, String content) {
        if ("dingtalk".equals(channel)) {
            return Map.of("msgtype", "text", "text", Map.of("content", content));
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("title", alert.getTitle());
        payload.put("description", content);
        payload.put("eventDescription", alert.getDescription());
        payload.put("content", content);
        payload.put("level", alert.getLevel());
        payload.put("transition", alert.getTransition());
        payload.put("instanceId", alert.getInstanceId());
        payload.put("labels", alert.getLabels() == null ? Map.of() : alert.getLabels());
        return payload;
    }

    private static String message(RmqAlertNotificationOutbox row, SystemAlertVO alert) {
        return StringUtils.hasText(row.getMessageContent()) ? row.getMessageContent()
                : AlertNotificationTemplate.render(null, alert, null);
    }

    private void retry(RmqAlertNotificationOutbox row, LocalDateTime now, String claimToken, String error) {
        int attempts = (row.getAttemptCount() == null ? 0 : row.getAttemptCount()) + 1;
        boolean exhausted = attempts >= MAX_ATTEMPTS;
        if (!updateClaimed(row, claimToken, new UpdateWrapper<RmqAlertNotificationOutbox>()
                .set("attempt_count", attempts).set("status", (exhausted ? NotificationOutboxStatus.FAILED
                        : NotificationOutboxStatus.RETRY_WAIT).name())
                .set("next_attempt_at", now.plusSeconds(Math.min(300, 5L << Math.min(attempts - 1, 5))))
                .set("sending_started_at", null)
                .set("last_error", abbreviate(error)))) {
            return;
        }
        recordDelivery(row, exhausted ? "FAIL_ALERT_NOTIFICATION" : "RETRY_ALERT_NOTIFICATION",
                exhausted ? "FAILURE" : "RETRYING", abbreviate(error));
        log.warn("Alert notification {} for event {}: {}", exhausted ? "failed" : "will retry", row.getAlertId(), error);
    }

    private void recordDelivery(RmqAlertNotificationOutbox row, String operation, String result, String error) {
        operationAuditService.record(operation, "ALERT_NOTIFICATION", String.valueOf(row.getId()), null,
                "alertId=" + row.getAlertId() + ", channel=" + row.getChannel(), result, error);
    }

    private static String abbreviate(String value) {
        if (value == null) return "Delivery failed";
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    private boolean updateClaimed(RmqAlertNotificationOutbox row, String claimToken,
            UpdateWrapper<RmqAlertNotificationOutbox> updates) {
        return mapper.update(null, updates.eq("id", row.getId()).eq("claim_token", claimToken)) == 1;
    }

    private static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static RestTemplate newClient() {
        NoRedirectClientHttpRequestFactory factory = new NoRedirectClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }
}
