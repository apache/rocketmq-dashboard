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
package org.apache.rocketmq.studio.provider.apache;

import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.cluster.config.ClusterConfigVO;
import org.apache.rocketmq.studio.common.domain.enums.FlushDiskType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class RocketMQBrokerConfigService {

    private final MqAdminExtFactory adminFactory;
    private final RocketMQProperties properties;
    private final RuntimeAdminClientResolver runtimeAdminClientResolver;
    private final AuditService auditService;

    /**
     * Read broker config from the live broker via admin API.
     */
    public ClusterConfigVO getBrokerConfig(String brokerAddr) {
        return getBrokerConfig(brokerAddr, null);
    }

    public ClusterConfigVO getBrokerConfig(String brokerAddr, String instanceId) {
        return execute(instanceId, admin -> {
            try {
                Properties props = admin.getBrokerConfig(brokerAddr);
                return mapToClusterConfigVO(props);
            } catch (Exception e) {
                log.error("Failed to get broker config from {}", brokerAddr, e);
                throw new BusinessException(500, "Failed to get broker config: " + e.getMessage());
            }
        });
    }

    private <T> T execute(String instanceId, MqAdminExtFactory.AdminAction<T> action) {
        if (StringUtils.hasText(instanceId)) {
            return runtimeAdminClientResolver.execute(instanceId, action);
        }
        return adminFactory.execute(namesrvAddr(), null, action);
    }

    /**
     * Update broker config on the live broker via admin API, then record audit.
     */
    public void updateBrokerConfig(String brokerAddr, String clusterId, Properties newConfig) {
        updateBrokerConfig(brokerAddr, clusterId, null, newConfig);
    }

    public void updateBrokerConfig(String brokerAddr, String clusterId, String instanceId, Properties newConfig) {
        execute(instanceId, admin -> {
            try {
                admin.updateBrokerConfig(brokerAddr, newConfig);
                String detail = "brokerAddr=" + brokerAddr + ", config=" + newConfig;
                recordAudit(clusterId, detail, "SUCCESS");
                log.info("Broker config updated successfully: {}", brokerAddr);
                return null;
            } catch (Exception e) {
                log.error("Failed to update broker config at {}", brokerAddr, e);
                String detail = "brokerAddr=" + brokerAddr + ", error=" + e.getMessage();
                recordAudit(clusterId, detail, "FAILED");
                throw new BusinessException(500, "Failed to update broker config: " + e.getMessage());
            }
        });
    }

    private void recordAudit(String clusterId, String detail, String result) {
        try {
            auditService.record("UPDATE_BROKER_CONFIG", "BROKER", "CLUSTER:" + clusterId,
                    clusterId, detail, result);
        } catch (Exception auditFailure) {
            log.warn("Failed to record broker config audit for cluster {}: {}", clusterId,
                    auditFailure.getMessage());
        }
    }

    private ClusterConfigVO mapToClusterConfigVO(Properties props) {
        Properties safeProps = props == null ? new Properties() : props;
        ClusterConfigVO vo = new ClusterConfigVO();
        vo.setFlushDiskType(parseFlushDiskType(safeProps.getProperty("flushDiskType")));
        vo.setAutoCreateTopicEnable(parseBoolean(safeProps.getProperty("autoCreateTopicEnable"), true));
        vo.setAutoCreateSubscriptionGroup(
                parseBoolean(safeProps.getProperty("autoCreateSubscriptionGroup"), true));
        vo.setMaxMessageSize(parseIntInRange(safeProps.getProperty("maxMessageSize"), 4194304, 1,
                Integer.MAX_VALUE));
        int queueCount = parseIntInRange(safeProps.getProperty("defaultTopicQueueNums"), 8, 1,
                Integer.MAX_VALUE);
        vo.setWriteQueueNums(queueCount);
        vo.setReadQueueNums(queueCount);
        vo.setFileReservedTime(parseIntInRange(safeProps.getProperty("fileReservedTime"), 72, 1,
                Integer.MAX_VALUE));
        vo.setBrokerPermission(parseIntInRange(safeProps.getProperty("brokerPermission"), 6, 0, 7));
        vo.setDeleteWhen(textOrDefault(safeProps.getProperty("deleteWhen"), "04"));
        vo.setMsgTraceTopicName(textOrDefault(
                safeProps.getProperty("msgTraceTopicName"), "RMQ_SYS_TRACE_TOPIC"));
        return vo;
    }

    private FlushDiskType parseFlushDiskType(String value) {
        if (!StringUtils.hasText(value)) {
            return FlushDiskType.ASYNC_FLUSH;
        }
        try {
            return FlushDiskType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return FlushDiskType.ASYNC_FLUSH;
        }
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        String normalized = value.trim();
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        return defaultValue;
    }

    private int parseIntInRange(String value, int defaultValue, int minimum, int maximum) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= minimum && parsed <= maximum ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String textOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String namesrvAddr() {
        String namesrvAddr = properties.getNamesrvAddr();
        if (!StringUtils.hasText(namesrvAddr)) {
            throw new BusinessException(503, "RocketMQ admin is not configured. Set studio.rocketmq.namesrv-addr.");
        }
        return namesrvAddr;
    }
}
