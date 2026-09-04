/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/** Declares only metrics backed by native collectors for the selected provider. */
@Service
@RequiredArgsConstructor
public class NativeAlertMetricCatalogService {
    private static final Set<String> NATIVE_METRICS = Set.of("nameserver.availability", "broker.availability",
            "proxy.availability", "cloud.instance.availability",
            "broker.disk.usage_ratio", "broker.jvm.heap.usage_ratio", "consumer.lag.total",
            "broker.send_queue.usage_ratio",
            "consumer.lag.max_queue", "consumer.delay.seconds", "topic.backlog.total", "dlq.message.count");
    private static final List<NativeAlertMetricInfo> CLUSTER_APACHE = List.of(
            new NativeAlertMetricInfo("nameserver.availability", "NameServer availability", "", false),
            new NativeAlertMetricInfo("broker.availability", "Broker availability", "", false),
            new NativeAlertMetricInfo("proxy.availability", "Proxy availability", "", false),
            new NativeAlertMetricInfo("broker.disk.usage_ratio", "Broker disk usage ratio", "ratio", false),
            new NativeAlertMetricInfo("broker.jvm.heap.usage_ratio", "Broker JVM heap usage ratio", "ratio", false),
            new NativeAlertMetricInfo("broker.send_queue.usage_ratio", "Broker send queue usage ratio", "ratio", false));
    private static final List<NativeAlertMetricInfo> BUSINESS_APACHE = List.of(
            new NativeAlertMetricInfo("consumer.lag.total", "Consumer lag total", "messages", true),
            new NativeAlertMetricInfo("consumer.lag.max_queue", "Consumer lag max queue", "messages", true),
            new NativeAlertMetricInfo("consumer.delay.seconds", "Consumer delay", "seconds", true),
            new NativeAlertMetricInfo("topic.backlog.total", "Topic backlog (per consumer group)", "messages", true),
            new NativeAlertMetricInfo("dlq.message.count", "DLQ message count", "messages", true));
    private static final List<NativeAlertMetricInfo> BUSINESS_CLOUD = List.of(
            new NativeAlertMetricInfo("consumer.lag.total", "Consumer lag total", "messages", true),
            new NativeAlertMetricInfo("consumer.lag.max_queue", "Consumer lag max queue", "messages", true),
            new NativeAlertMetricInfo("topic.backlog.total", "Topic backlog (per consumer group)", "messages", true));
    private static final List<NativeAlertMetricInfo> CLUSTER_CLOUD = List.of(
            new NativeAlertMetricInfo("cloud.instance.availability", "Cloud instance availability", "", false));

    private final InstanceRepository instanceRepository;

    public List<NativeAlertMetricInfo> list(String instanceId, AlertDomain domain) {
        if (instanceId == null || instanceId.isBlank()) {
            throw new BusinessException(400, "instanceId is required");
        }
        InstanceVO instance = instanceRepository.findByIdentifier(instanceId.trim())
                .orElseThrow(() -> new BusinessException(404, "Instance not found: " + instanceId));
        if (instance.getVendor() == null || instance.getVendor() == InstanceVendor.APACHE) {
            return domain == AlertDomain.CLUSTER ? CLUSTER_APACHE : BUSINESS_APACHE;
        }
        if (domain == AlertDomain.BUSINESS && (instance.getVendor() == InstanceVendor.ALIYUN
                || instance.getVendor() == InstanceVendor.TENCENT)) {
            return BUSINESS_CLOUD;
        }
        if (domain == AlertDomain.CLUSTER && (instance.getVendor() == InstanceVendor.ALIYUN
                || instance.getVendor() == InstanceVendor.TENCENT)) {
            return CLUSTER_CLOUD;
        }
        return List.of();
    }

    public void validate(AlertRuleVO rule) {
        if (rule == null || rule.getMetric() == null) {
            return;
        }
        String metric = rule.getMetric().trim();
        rule.setMetric(metric);
        if (!NATIVE_METRICS.contains(metric)) {
            return;
        }
        boolean supported = list(rule.getInstanceId(), rule.getDomain()).stream()
                .anyMatch(candidate -> candidate.key().equals(metric));
        if (!supported) {
            throw new BusinessException(400, "Native metric " + metric
                    + " is not supported by the selected Studio instance");
        }
    }
}
