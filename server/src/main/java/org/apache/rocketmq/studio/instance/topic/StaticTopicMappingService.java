/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.topic;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.remoting.protocol.statictopic.TopicConfigAndQueueMapping;
import org.apache.rocketmq.remoting.protocol.statictopic.TopicQueueMappingDetail;
import org.apache.rocketmq.remoting.protocol.statictopic.TopicQueueMappingInfo;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StaticTopicMappingService {
    private final RuntimeAdminClientResolver resolver;

    public record CurrentQueue(int logicalQueueId, int physicalQueueId) { }
    public record RouteMapping(String epoch, String scope, int totalQueues, List<CurrentQueue> currentQueues) { }
    public record Segment(int generation, String brokerName, int physicalQueueId, String logicalStart,
            String physicalStart, String physicalEndExclusive) { }
    public record QueueHistory(int logicalQueueId, String lastMappedBroker, List<Segment> segments) { }
    public record LocalMapping(String epoch, String scope, int totalQueues, boolean dirty, List<QueueHistory> queues) { }
    public record Node(String brokerName, String address, String status, String error,
            RouteMapping advertised, LocalMapping local) { }
    public record Snapshot(String topic, Instant startedAt, Instant finishedAt, boolean partial, List<Node> nodes) { }

    public Snapshot inspect(String instanceId, String topic) {
        if (!StringUtils.hasText(topic)) {
            throw new BusinessException(400, "Topic is required");
        }
        return resolver.execute(instanceId, admin -> {
            try {
                return read(admin, topic);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BusinessException(502, "Static topic mapping inspection was interrupted");
            }
        });
    }

    private Snapshot read(MQAdminExt admin, String topic) throws Exception {
        Instant startedAt = Instant.now();
        var route = admin.examineTopicRouteInfo(topic);
        var cluster = admin.examineBrokerClusterInfo();
        if (route == null || route.getBrokerDatas() == null || cluster == null || cluster.getBrokerAddrTable() == null) {
            throw new BusinessException(502, "Topic route or broker registry is unavailable");
        }
        Set<String> brokerNames = new TreeSet<>();
        for (var broker : route.getBrokerDatas()) {
            if (broker == null || !StringUtils.hasText(broker.getBrokerName())) {
                throw new BusinessException(502, "Topic route contains an invalid broker identity");
            }
            brokerNames.add(broker.getBrokerName());
        }
        var advertisements = route.getTopicQueueMappingByBroker();
        if (advertisements != null) {
            for (String name : advertisements.keySet()) {
                if (!StringUtils.hasText(name)) {
                    throw new BusinessException(502, "Topic route contains an invalid mapping identity");
                }
                brokerNames.add(name);
            }
        }
        if (brokerNames.isEmpty()) {
            throw new BusinessException(404, "No route brokers are available for this topic");
        }
        List<Node> nodes = new ArrayList<>();
        for (String name : brokerNames) {
            String address = null;
            RouteMapping advertised = null;
            try {
                advertised = advertisement(topic, name, advertisements == null ? null : advertisements.get(name));
                var broker = cluster.getBrokerAddrTable().get(name);
                if (broker == null || broker.getBrokerAddrs() == null || !StringUtils.hasText(broker.getBrokerAddrs().get(0L))) {
                    throw new BusinessException(404, "No registered master address for this route broker");
                }
                address = broker.getBrokerAddrs().get(0L);
                var config = admin.examineTopicConfig(address, topic);
                if (!(config instanceof TopicConfigAndQueueMapping full) || !topic.equals(config.getTopicName())) {
                    throw new BusinessException(502, "Broker response does not contain the requested mapping-capable topic config");
                }
                var detail = full.getMappingDetail();
                LocalMapping local = detail == null ? null : mapping(topic, name, detail);
                nodes.add(new Node(name, address, local == null ? "NO_MAPPING" : "MAPPING", null, advertised, local));
            } catch (InterruptedException exception) {
                throw exception;
            } catch (Exception exception) {
                // Preserve other brokers' samples, without exposing arbitrary remote exception text.
                String error = exception instanceof BusinessException ? exception.getMessage() : exception.getClass().getSimpleName();
                nodes.add(new Node(name, address, "UNAVAILABLE", error, advertised, null));
            }
        }
        return new Snapshot(topic, startedAt, Instant.now(), nodes.stream().anyMatch(node -> "UNAVAILABLE".equals(node.status())),
                List.copyOf(nodes));
    }

    private RouteMapping advertisement(String topic, String broker, TopicQueueMappingInfo info) {
        if (info == null) {
            return null;
        }
        validateIdentity(topic, broker, info);
        if (info.getCurrIdMap() == null) {
            throw new BusinessException(502, "Advertised logical queue map is unavailable");
        }
        List<CurrentQueue> queues = info.getCurrIdMap().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> new CurrentQueue(entry.getKey(), entry.getValue())).toList();
        return new RouteMapping(Long.toString(info.getEpoch()), info.getScope(), info.getTotalQueues(), queues);
    }

    private LocalMapping mapping(String topic, String broker, TopicQueueMappingDetail detail) {
        validateIdentity(topic, broker, detail);
        if (detail.getHostedQueues() == null) {
            throw new BusinessException(502, "Hosted queue history is unavailable");
        }
        List<QueueHistory> queues = new ArrayList<>();
        for (var entry : new java.util.TreeMap<>(detail.getHostedQueues()).entrySet()) {
            List<Segment> segments = new ArrayList<>();
            if (entry.getKey() < 0 || entry.getValue().isEmpty()) {
                throw new BusinessException(502, "Broker returned an invalid logical queue history");
            }
            for (var item : entry.getValue()) {
                if (item == null || !StringUtils.hasText(item.getBname()) || item.getQueueId() < 0) {
                    throw new BusinessException(502, "Broker returned an invalid mapping segment");
                }
                segments.add(new Segment(item.getGen(), item.getBname(), item.getQueueId(), Long.toString(item.getLogicOffset()),
                        Long.toString(item.getStartOffset()), Long.toString(item.getEndOffset())));
            }
            // Order is the broker's mapping history. The final item determines its local view of ownership.
            queues.add(new QueueHistory(entry.getKey(), segments.get(segments.size() - 1).brokerName(), List.copyOf(segments)));
        }
        return new LocalMapping(Long.toString(detail.getEpoch()), detail.getScope(), detail.getTotalQueues(), detail.isDirty(), queues);
    }

    private void validateIdentity(String topic, String broker, TopicQueueMappingInfo info) {
        if (!topic.equals(info.getTopic()) || !broker.equals(info.getBname())) {
            throw new BusinessException(502, "Mapping identity does not match the requested topic and broker");
        }
    }
}
