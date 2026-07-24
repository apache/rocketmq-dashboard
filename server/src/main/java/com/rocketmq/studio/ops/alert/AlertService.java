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
package com.rocketmq.studio.ops.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;


    public List<AlertRuleVO> listRules() {
        log.info("Listing all alert rules");
        return alertRepository.findAllRules();
    }

    public String exportPrometheusRulesYaml() {
        List<AlertRuleVO> rules = alertRepository.findAllRules();
        List<PrometheusAlertRule> prometheusRules = rules.isEmpty()
                ? defaultPrometheusRules()
                : rules.stream().map(this::toPrometheusRule).toList();

        StringBuilder yaml = new StringBuilder();
        yaml.append("groups:\n");
        int index = 1;
        for (PrometheusAlertRule rule : prometheusRules) {
            yaml.append("  - name: ").append(rule.group()).append('\n');
            yaml.append("    rules:\n");
            yaml.append("      # Rule ").append(index++).append(": ").append(rule.alert()).append('\n');
            yaml.append("      - alert: ").append(rule.alert()).append('\n');
            yaml.append("        expr: ").append(rule.expr()).append('\n');
            yaml.append("        for: ").append(rule.duration()).append('\n');
            yaml.append("        labels:\n");
            yaml.append("          severity: ").append(rule.severity()).append('\n');
            yaml.append("          team: ").append(rule.team()).append('\n');
            yaml.append("        annotations:\n");
            yaml.append("          summary: \"").append(escapeYaml(rule.summary())).append("\"\n");
            yaml.append("          description: \"").append(escapeYaml(rule.description())).append("\"\n");
        }
        return yaml.toString();
    }


    public AlertRuleVO createRule(AlertRuleVO rule) {
        log.info("Creating alert rule: {}", rule.getName());
        rule.setId(UUID.randomUUID().toString());
        return alertRepository.saveRule(rule);
    }


    public AlertRuleVO updateRule(AlertRuleVO rule) {
        log.info("Updating alert rule: {}", rule.getId());
        return alertRepository.saveRule(rule);
    }


    public AlertRuleVO toggleRule(String id, boolean enabled) {
        log.info("Toggling alert rule id={}, enabled={}", id, enabled);
        List<AlertRuleVO> rules = alertRepository.findAllRules();
        AlertRuleVO rule = rules.stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new com.rocketmq.studio.common.exception.BusinessException(404, "Alert rule not found: " + id));
        rule.setEnabled(enabled);
        return alertRepository.saveRule(rule);
    }


    public void deleteRule(String id) {
        log.info("Deleting alert rule id={}", id);
        alertRepository.deleteRule(id);
    }


    public List<SystemAlertVO> listAlerts(String level) {
        log.info("Listing system alerts, level={}", level);
        return alertRepository.findAlerts(level);
    }


    public SystemAlertVO acknowledgeAlert(String id) {
        log.info("Acknowledging system alert id={}", id);
        List<SystemAlertVO> alerts = alertRepository.findAlerts(null);
        SystemAlertVO alert = alerts.stream()
                .filter(a -> a.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new com.rocketmq.studio.common.exception.BusinessException(404, "System alert not found: " + id));
        alert.setAcknowledged(true);
        return alertRepository.saveAlert(alert);
    }


    public int clearAcknowledged() {
        log.info("Clearing acknowledged system alerts");
        return alertRepository.deleteAcknowledgedAlerts();
    }

    private List<PrometheusAlertRule> defaultPrometheusRules() {
        List<PrometheusAlertRule> rules = new ArrayList<>();
        rules.add(rule("rocketmq-broker.rules", "RocketMQBrokerDown", "up{job=~\".*rocketmq.*\"} == 0", "1m",
                "critical", "broker", "RocketMQ broker is down",
                "A RocketMQ broker scrape target has been unavailable for more than 1 minute."));
        rules.add(rule("rocketmq-consumer.rules", "RocketMQConsumerLagHigh",
                "rocketmq_consumer_lag_messages > 100000", "5m",
                "warning", "consumer", "Consumer lag is high",
                "A consumer group has accumulated more than 100000 messages."));
        rules.add(rule("rocketmq-consumer.rules", "RocketMQConsumerLagCritical",
                "rocketmq_consumer_lag_messages > 1000000", "5m",
                "critical", "consumer", "Consumer lag is critical",
                "A consumer group has accumulated more than 1000000 messages."));
        rules.add(rule("rocketmq-client.rules", "RocketMQProducerSendLatencyHigh",
                "rocketmq_producer_send_to_back_rt > 1000", "5m",
                "warning", "client", "Producer send latency is high",
                "Producer send-to-broker latency has stayed above 1000 ms."));
        rules.add(rule("rocketmq-broker.rules", "RocketMQProcessorWatermarkHigh",
                "rocketmq_processor_watermark > 80", "5m",
                "warning", "broker", "Processor watermark is high",
                "Broker processor watermark is above 80 percent."));
        rules.add(rule("rocketmq-topic.rules", "RocketMQMessageInDrop",
                "rate(rocketmq_messages_in_total[5m]) == 0", "10m",
                "info", "topic", "No incoming messages",
                "No incoming messages have been observed for 10 minutes."));
        rules.add(rule("rocketmq-consumer.rules", "RocketMQMessageOutDrop",
                "rate(rocketmq_messages_out_total[5m]) == 0", "10m",
                "info", "consumer", "No outgoing messages",
                "No outgoing messages have been observed for 10 minutes."));
        return rules;
    }

    private PrometheusAlertRule rule(String group, String alert, String expr, String duration,
                                     String severity, String team, String summary, String description) {
        return new PrometheusAlertRule(group, alert, expr, duration, severity, team, summary, description);
    }

    private PrometheusAlertRule toPrometheusRule(AlertRuleVO rule) {
        String team = inferTeam(rule.getMetric());
        return new PrometheusAlertRule(
                groupName(team),
                alertName(rule),
                expression(rule),
                duration(rule),
                "warning",
                team,
                summary(rule),
                description(rule));
    }

    private String groupName(String team) {
        if ("client".equals(team)) {
            return "rocketmq-client.rules";
        }
        if ("consumer".equals(team)) {
            return "rocketmq-consumer.rules";
        }
        if ("topic".equals(team)) {
            return "rocketmq-topic.rules";
        }
        return "rocketmq-broker.rules";
    }

    private String alertName(AlertRuleVO rule) {
        return hasText(rule.getName()) ? rule.getName().replaceAll("[^A-Za-z0-9_]", "") : "RocketMQAlert";
    }

    private String expression(AlertRuleVO rule) {
        String metric = hasText(rule.getMetric()) ? rule.getMetric() : "rocketmq_consumer_lag_messages";
        String operator = hasText(rule.getOperator()) ? rule.getOperator() : ">";
        return metric + " " + operator + " " + formatThreshold(rule.getThreshold());
    }

    private String formatThreshold(double threshold) {
        if (threshold == Math.rint(threshold)) {
            return Long.toString((long) threshold);
        }
        return Double.toString(threshold);
    }

    private String duration(AlertRuleVO rule) {
        return hasText(rule.getDuration()) ? rule.getDuration() : "5m";
    }

    private String inferTeam(String metric) {
        if (!hasText(metric)) {
            return "broker";
        }
        if (metric.contains("consumer") || metric.contains("lag")) {
            return "consumer";
        }
        if (metric.contains("producer") || metric.contains("client")) {
            return "client";
        }
        if (metric.contains("topic") || metric.contains("messages_in") || metric.contains("messages_out")) {
            return "topic";
        }
        return "broker";
    }

    private String summary(AlertRuleVO rule) {
        String description = rule.getDescription();
        if (hasText(description) && description.contains(" - ")) {
            return description.substring(0, description.indexOf(" - "));
        }
        return hasText(rule.getName()) ? rule.getName() : "RocketMQ alert";
    }

    private String description(AlertRuleVO rule) {
        String description = rule.getDescription();
        if (hasText(description) && description.contains(" - ")) {
            return description.substring(description.indexOf(" - ") + 3);
        }
        return hasText(description) ? description : "RocketMQ alert condition matched.";
    }

    private String escapeYaml(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private record PrometheusAlertRule(String group, String alert, String expr, String duration,
                                       String severity, String team, String summary, String description) {
    }
}
