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
package org.apache.rocketmq.studio.model;

import java.util.Date;
import java.util.List;

public class ClientInstance {

    private String clientId;

    private String clientAddress;

    private ClientType clientType;

    /**
     * Topics associated with this client (published topics for producers, subscribed topics for consumers)
     */
    private List<String> topics;

    private String clientSubType;

    private String language;

    private String sdkVersion;

    private ProtocolType protocolType;

    private String endpoint;

    private Date lastHeartbeatTime;

    private boolean active;

    private Date connectTime;

    private String instanceName;

    private String consumerGroup;

    private String producerGroup;

    private List<SubscriptionInfo> subscriptions;

    private List<String> publishTopics;

    private ConsumerProgress consumerProgress;

    private String clientVersion;

    private Boolean vipChannelEnabled;

    private String telemetrySessionId;

    private Boolean longConnectionActive;

    private String settingsVersion;

    private String authFailureReason;

    private Boolean popEnabled;

    private Integer pendingAckCount;

    /**
     * Subscription count for consumer clients
     */
    private Integer subscriptionCount;

    /**
     * Client status (ONLINE, OFFLINE, etc.)
     */
    private String status;

    public String getDisplayName() {
        if (instanceName != null && !instanceName.trim().isEmpty()) {
            return instanceName;
        }
        return clientId;
    }

    public long getClientDelay() {
        if (lastHeartbeatTime == null) {
            return -1;
        }
        return (System.currentTimeMillis() - lastHeartbeatTime.getTime()) / 1000;
    }

    public boolean isGrpcClient() {
        return ProtocolType.GRPC.equals(protocolType);
    }

    public boolean isRemotingClient() {
        return ProtocolType.REMOTING.equals(protocolType);
    }

    public boolean isConsumer() {
        return ClientType.CONSUMER.equals(clientType);
    }

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

    public static class ConsumerProgress {
        private Long totalConsumed;
        private Long totalBacklog;
        private Double consumptionRate;
        private Date lastConsumeTime;
        private String consumptionMode; // PULL / PUSH / POP
        private Boolean orderlyConsume;
    }
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientAddress() {
        return clientAddress;
    }

    public void setClientAddress(String clientAddress) {
        this.clientAddress = clientAddress;
    }

    public ClientType getClientType() {
        return clientType;
    }

    public void setClientType(ClientType clientType) {
        this.clientType = clientType;
    }

    public List<String> getTopics() {
        return topics;
    }

    public void setTopics(List<String> topics) {
        this.topics = topics;
    }

    public String getClientSubType() {
        return clientSubType;
    }

    public void setClientSubType(String clientSubType) {
        this.clientSubType = clientSubType;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getSdkVersion() {
        return sdkVersion;
    }

    public void setSdkVersion(String sdkVersion) {
        this.sdkVersion = sdkVersion;
    }

    public ProtocolType getProtocolType() {
        return protocolType;
    }

    public void setProtocolType(ProtocolType protocolType) {
        this.protocolType = protocolType;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Date getLastHeartbeatTime() {
        return lastHeartbeatTime;
    }

    public void setLastHeartbeatTime(Date lastHeartbeatTime) {
        this.lastHeartbeatTime = lastHeartbeatTime;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Date getConnectTime() {
        return connectTime;
    }

    public void setConnectTime(Date connectTime) {
        this.connectTime = connectTime;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public List<SubscriptionInfo> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(List<SubscriptionInfo> subscriptions) {
        this.subscriptions = subscriptions;
    }

    public List<String> getPublishTopics() {
        return publishTopics;
    }

    public void setPublishTopics(List<String> publishTopics) {
        this.publishTopics = publishTopics;
    }

    public ConsumerProgress getConsumerProgress() {
        return consumerProgress;
    }

    public void setConsumerProgress(ConsumerProgress consumerProgress) {
        this.consumerProgress = consumerProgress;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    public Boolean getVipChannelEnabled() {
        return vipChannelEnabled;
    }

    public void setVipChannelEnabled(Boolean vipChannelEnabled) {
        this.vipChannelEnabled = vipChannelEnabled;
    }

    public String getTelemetrySessionId() {
        return telemetrySessionId;
    }

    public void setTelemetrySessionId(String telemetrySessionId) {
        this.telemetrySessionId = telemetrySessionId;
    }

    public Boolean getLongConnectionActive() {
        return longConnectionActive;
    }

    public void setLongConnectionActive(Boolean longConnectionActive) {
        this.longConnectionActive = longConnectionActive;
    }

    public String getSettingsVersion() {
        return settingsVersion;
    }

    public void setSettingsVersion(String settingsVersion) {
        this.settingsVersion = settingsVersion;
    }

    public String getAuthFailureReason() {
        return authFailureReason;
    }

    public void setAuthFailureReason(String authFailureReason) {
        this.authFailureReason = authFailureReason;
    }

    public Boolean getPopEnabled() {
        return popEnabled;
    }

    public void setPopEnabled(Boolean popEnabled) {
        this.popEnabled = popEnabled;
    }

    public Integer getPendingAckCount() {
        return pendingAckCount;
    }

    public void setPendingAckCount(Integer pendingAckCount) {
        this.pendingAckCount = pendingAckCount;
    }

    public Integer getSubscriptionCount() {
        return subscriptionCount;
    }

    public void setSubscriptionCount(Integer subscriptionCount) {
        this.subscriptionCount = subscriptionCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}