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
        String normalizedAddr = requireBrokerAddress(brokerAddr);
        return execute(instanceId, admin -> {
            try {
                Properties props = admin.getBrokerConfig(normalizedAddr);
                return mapToClusterConfigVO(props);
            } catch (Exception e) {
                log.error("Failed to get broker config from {}", normalizedAddr, e);
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
        String normalizedAddr = requireBrokerAddress(brokerAddr);
        execute(instanceId, admin -> {
            try {
                admin.updateBrokerConfig(normalizedAddr, newConfig);
                String detail = "brokerAddr=" + normalizedAddr + ", config=" + newConfig;
                recordAudit(clusterId, detail, "SUCCESS");
                log.info("Broker config updated successfully: {}", normalizedAddr);
                return null;
            } catch (Exception e) {
                log.error("Failed to update broker config at {}", normalizedAddr, e);
                String detail = "brokerAddr=" + normalizedAddr + ", error=" + e.getMessage();
                recordAudit(clusterId, detail, "FAILED");
                throw new BusinessException(500, "Failed to update broker config: " + e.getMessage());
            }
        });
    }

    private static String requireBrokerAddress(String brokerAddr) {
        if (!StringUtils.hasText(brokerAddr)) {
            throw new BusinessException(400, "brokerAddr is required");
        }
        return brokerAddr.trim();
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
        ClusterConfigVO vo = new ClusterConfigVO();
        vo.setFlushDiskType(parseFlushDiskType(props.getProperty("flushDiskType", "ASYNC_FLUSH")));
        vo.setAutoCreateTopicEnable(Boolean.parseBoolean(props.getProperty("autoCreateTopicEnable", "true")));
        vo.setAutoCreateSubscriptionGroup(Boolean.parseBoolean(props.getProperty("autoCreateSubscriptionGroup", "true")));
        vo.setMaxMessageSize(parseIntSafe(props.getProperty("maxMessageSize"), 4194304));
        vo.setWriteQueueNums(parseIntSafe(props.getProperty("defaultTopicQueueNums"), 8));
        vo.setReadQueueNums(parseIntSafe(props.getProperty("defaultTopicQueueNums"), 8));
        vo.setFileReservedTime(parseIntSafe(props.getProperty("fileReservedTime"), 72));
        vo.setBrokerPermission(parseIntSafe(props.getProperty("brokerPermission"), 6));
        vo.setDeleteWhen(props.getProperty("deleteWhen", "04"));
        vo.setMsgTraceTopicName(props.getProperty("msgTraceTopicName", "RMQ_SYS_TRACE_TOPIC"));
        return vo;
    }

    private FlushDiskType parseFlushDiskType(String value) {
        try {
            return FlushDiskType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return FlushDiskType.ASYNC_FLUSH;
        }
    }

    private int parseIntSafe(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String namesrvAddr() {
        String namesrvAddr = properties.getNamesrvAddr();
        if (!StringUtils.hasText(namesrvAddr)) {
            throw new BusinessException(503, "RocketMQ admin is not configured. Set studio.rocketmq.namesrv-addr.");
        }
        return namesrvAddr;
    }
}
