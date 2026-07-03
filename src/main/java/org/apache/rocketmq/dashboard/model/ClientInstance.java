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
package org.apache.rocketmq.dashboard.model;

import lombok.Data;
import java.util.Date;
import java.util.List;

/**
 *
 *
 */
@Data
public class ClientInstance {

    /**
 *
     */
    private String clientId;

    /**
 *
     */
    private String clientAddress;

    /**
 *
     */
    private ClientType clientType;

    /**
     * Topics associated with this client (published topics for producers, subscribed topics for consumers)
     */
    private List<String> topics;

    /**
 *
     */
    private String clientSubType;

    /**
 *
     */
    private String language;

    /**
 *
     */
    private String sdkVersion;

    /**
 *
     */
    private ProtocolType protocolType;

    /**
 *
     */
    private String endpoint;

    /**
 *
     */
    private Date lastHeartbeatTime;

    /**
 *
     */
    private boolean active;

    /**
 *
     */
    private Date connectTime;

    /**
 *
     */
    private String instanceName;

    /**
 *
     */
    private String consumerGroup;

    /**
 *
     */
    private String producerGroup;

    /**
 *
     */
    private List<SubscriptionInfo> subscriptions;

    /**
 *
     */
    private List<String> publishTopics;

    /**
 *
     */
    private ConsumerProgress consumerProgress;

    // Removed

    /**
 *
     */
    private String clientVersion;

    /**
 *
     */
    private Boolean vipChannelEnabled;

    // Removed

    /**
 *
     */
    private String telemetrySessionId;

    /**
 *
     */
    private Boolean longConnectionActive;

    /**
 *
     */
    private String settingsVersion;

    /**
 *
     */
    private String authFailureReason;

    // Removed

    /**
 *
     */
    private Boolean popEnabled;

    /**
 *
     */
    private Integer pendingAckCount;

    /**
     * Subscription count for consumer clients
     */
    private Integer subscriptionCount;

    /**
     * Client status (ONLINE, OFFLINE, etc.)
     */
    private String status;

    /**
 *
     */
    public String getDisplayName() {
        if (instanceName != null && !instanceName.trim().isEmpty()) {
            return instanceName;
        }
        return clientId;
    }

    /**
 *
     */
    public long getClientDelay() {
        if (lastHeartbeatTime == null) {
            return -1;
        }
        return (System.currentTimeMillis() - lastHeartbeatTime.getTime()) / 1000;
    }

    /**
 *
     */
    public boolean isGrpcClient() {
        return ProtocolType.GRPC.equals(protocolType);
    }

    /**
 *
     */
    public boolean isRemotingClient() {
        return ProtocolType.REMOTING.equals(protocolType);
    }

    /**
 *
     */
    public boolean isConsumer() {
        return ClientType.CONSUMER.equals(clientType);
    }

    /**
 *
     */
    public boolean isProducer() {
        return ClientType.PRODUCER.equals(clientType);
    }

    /**
     * Get version string (alias for sdkVersion for backward compatibility)
     */
    public String getVersion() {
        return sdkVersion;
    }

    /**
     * Set version string (alias for sdkVersion for backward compatibility)
     */
    public void setVersion(String version) {
        this.sdkVersion = version;
    }

    public enum ClientType {
        PRODUCER, CONSUMER, PUSH_CONSUMER, PULL_CONSUMER, SIMPLE_CONSUMER
    }

    public enum ProtocolType {
        REMOTING, GRPC
    }

    @Data
    public static class ConsumerProgress {
        private Long totalConsumed;
        private Long totalBacklog;
        private Double consumptionRate;
        private Date lastConsumeTime;
        private String consumptionMode; // PULL / PUSH / POP
        private Boolean orderlyConsume;
    }
}