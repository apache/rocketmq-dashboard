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

import java.util.ArrayList;
import java.util.List;

public class ConsumerSlowRootCauseReport {

    private String consumerGroup;
    private long diagnoseTime;
    private int totalClients;
    private String primaryRootCause; // HEALTHY, THREAD_BLOCKED, FLOW_CONTROL_EXCEEDED, HIGH_CONSUME_RT, REBALANCE_HANG
    private String severity; // NORMAL, WARNING, CRITICAL
    private String rootCauseDescription;
    private String actionableRemedy;
    private List<ClientDiagnosticFinding> findings = new ArrayList<>();

    public static class ClientDiagnosticFinding {
        private String clientId;
        private String clientAddr;
        private boolean isFlowControlTriggered;
        private boolean isBlockedThreadDetected;
        private long cachedMessageCount;
        private long cachedMessageSizeMb;
        private double consumeTps;
        private double pullTps;
        private String blockedThreadSignature;
        private String diagnosisSummary;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientAddr() {
            return clientAddr;
        }

        public void setClientAddr(String clientAddr) {
            this.clientAddr = clientAddr;
        }

        public boolean isFlowControlTriggered() {
            return isFlowControlTriggered;
        }

        public void setFlowControlTriggered(boolean flowControlTriggered) {
            isFlowControlTriggered = flowControlTriggered;
        }

        public boolean isBlockedThreadDetected() {
            return isBlockedThreadDetected;
        }

        public void setBlockedThreadDetected(boolean blockedThreadDetected) {
            isBlockedThreadDetected = blockedThreadDetected;
        }

        public long getCachedMessageCount() {
            return cachedMessageCount;
        }

        public void setCachedMessageCount(long cachedMessageCount) {
            this.cachedMessageCount = cachedMessageCount;
        }

        public long getCachedMessageSizeMb() {
            return cachedMessageSizeMb;
        }

        public void setCachedMessageSizeMb(long cachedMessageSizeMb) {
            this.cachedMessageSizeMb = cachedMessageSizeMb;
        }

        public double getConsumeTps() {
            return consumeTps;
        }

        public void setConsumeTps(double consumeTps) {
            this.consumeTps = consumeTps;
        }

        public double getPullTps() {
            return pullTps;
        }

        public void setPullTps(double pullTps) {
            this.pullTps = pullTps;
        }

        public String getBlockedThreadSignature() {
            return blockedThreadSignature;
        }

        public void setBlockedThreadSignature(String blockedThreadSignature) {
            this.blockedThreadSignature = blockedThreadSignature;
        }

        public String getDiagnosisSummary() {
            return diagnosisSummary;
        }

        public void setDiagnosisSummary(String diagnosisSummary) {
            this.diagnosisSummary = diagnosisSummary;
        }
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public long getDiagnoseTime() {
        return diagnoseTime;
    }

    public void setDiagnoseTime(long diagnoseTime) {
        this.diagnoseTime = diagnoseTime;
    }

    public int getTotalClients() {
        return totalClients;
    }

    public void setTotalClients(int totalClients) {
        this.totalClients = totalClients;
    }

    public String getPrimaryRootCause() {
        return primaryRootCause;
    }

    public void setPrimaryRootCause(String primaryRootCause) {
        this.primaryRootCause = primaryRootCause;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getRootCauseDescription() {
        return rootCauseDescription;
    }

    public void setRootCauseDescription(String rootCauseDescription) {
        this.rootCauseDescription = rootCauseDescription;
    }

    public String getActionableRemedy() {
        return actionableRemedy;
    }

    public void setActionableRemedy(String actionableRemedy) {
        this.actionableRemedy = actionableRemedy;
    }

    public List<ClientDiagnosticFinding> getFindings() {
        return findings;
    }

    public void setFindings(List<ClientDiagnosticFinding> findings) {
        this.findings = findings;
    }
}
