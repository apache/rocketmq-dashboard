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
package org.apache.rocketmq.studio.rocketmq;

import org.apache.rocketmq.studio.cluster.config.ClusterConfigVO;
import org.apache.rocketmq.studio.common.domain.enums.FlushDiskType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Slf4j
@Service
public class RocketMQBrokerConfigService {

    private final DefaultMQAdminExt adminExt;
    private final AuditService auditService;

    public RocketMQBrokerConfigService(@Nullable DefaultMQAdminExt adminExt, AuditService auditService) {
        this.adminExt = adminExt;
        this.auditService = auditService;
    }

    /**
     * Read broker config from the live broker via admin API.
     */
    public ClusterConfigVO getBrokerConfig(String brokerAddr) {
        requireAdmin();
        try {
            Properties props = adminExt.getBrokerConfig(brokerAddr);
            return mapToClusterConfigVO(props);
        } catch (Exception e) {
            log.error("Failed to get broker config from {}", brokerAddr, e);
            throw new BusinessException(500, "Failed to get broker config: " + e.getMessage());
        }
    }

    /**
     * Update broker config on the live broker via admin API, then record audit.
     */
    public void updateBrokerConfig(String brokerAddr, String clusterId, Properties newConfig) {
        requireAdmin();
        try {
            adminExt.updateBrokerConfig(brokerAddr, newConfig);
            String detail = "brokerAddr=" + brokerAddr + ", config=" + newConfig;
            auditService.record("UPDATE_BROKER_CONFIG", "CLUSTER:" + clusterId, detail, "SUCCESS");
            log.info("Broker config updated successfully: {}", brokerAddr);
        } catch (Exception e) {
            log.error("Failed to update broker config at {}", brokerAddr, e);
            String detail = "brokerAddr=" + brokerAddr + ", error=" + e.getMessage();
            auditService.record("UPDATE_BROKER_CONFIG", "CLUSTER:" + clusterId, detail, "FAILED");
            throw new BusinessException(500, "Failed to update broker config: " + e.getMessage());
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

    private void requireAdmin() {
        if (adminExt == null) {
            throw new BusinessException(503, "RocketMQ admin is not configured. Set studio.rocketmq.namesrv-addr.");
        }
    }
}
