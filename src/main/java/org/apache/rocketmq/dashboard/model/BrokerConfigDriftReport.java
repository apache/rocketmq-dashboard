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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BrokerConfigDriftReport {

    private int totalBrokersInspected;
    private int driftedBrokerCount;
    private boolean hasCriticalDrift;
    private Map<String, String> baselineConfigs = new HashMap<>();
    private List<ConfigDriftItem> driftItems = new ArrayList<>();

    public int getTotalBrokersInspected() {
        return totalBrokersInspected;
    }

    public void setTotalBrokersInspected(int totalBrokersInspected) {
        this.totalBrokersInspected = totalBrokersInspected;
    }

    public int getDriftedBrokerCount() {
        return driftedBrokerCount;
    }

    public void setDriftedBrokerCount(int driftedBrokerCount) {
        this.driftedBrokerCount = driftedBrokerCount;
    }

    public boolean isHasCriticalDrift() {
        return hasCriticalDrift;
    }

    public void setHasCriticalDrift(boolean hasCriticalDrift) {
        this.hasCriticalDrift = hasCriticalDrift;
    }

    public Map<String, String> getBaselineConfigs() {
        return baselineConfigs;
    }

    public void setBaselineConfigs(Map<String, String> baselineConfigs) {
        this.baselineConfigs = baselineConfigs;
    }

    public List<ConfigDriftItem> getDriftItems() {
        return driftItems;
    }

    public void setDriftItems(List<ConfigDriftItem> driftItems) {
        this.driftItems = driftItems;
    }

    public static class ConfigDriftItem {
        private String brokerAddr;
        private String brokerName;
        private String configKey;
        private String expectedBaselineValue;
        private String actualBrokerValue;
        private String driftSeverity; // LOW, MEDIUM, HIGH

        public ConfigDriftItem() {}

        public ConfigDriftItem(String brokerAddr, String brokerName, String configKey, String expectedBaselineValue, String actualBrokerValue, String driftSeverity) {
            this.brokerAddr = brokerAddr;
            this.brokerName = brokerName;
            this.configKey = configKey;
            this.expectedBaselineValue = expectedBaselineValue;
            this.actualBrokerValue = actualBrokerValue;
            this.driftSeverity = driftSeverity;
        }

        public String getBrokerAddr() {
            return brokerAddr;
        }

        public void setBrokerAddr(String brokerAddr) {
            this.brokerAddr = brokerAddr;
        }

        public String getBrokerName() {
            return brokerName;
        }

        public void setBrokerName(String brokerName) {
            this.brokerName = brokerName;
        }

        public String getConfigKey() {
            return configKey;
        }

        public void setConfigKey(String configKey) {
            this.configKey = configKey;
        }

        public String getExpectedBaselineValue() {
            return expectedBaselineValue;
        }

        public void setExpectedBaselineValue(String expectedBaselineValue) {
            this.expectedBaselineValue = expectedBaselineValue;
        }

        public String getActualBrokerValue() {
            return actualBrokerValue;
        }

        public void setActualBrokerValue(String actualBrokerValue) {
            this.actualBrokerValue = actualBrokerValue;
        }

        public String getDriftSeverity() {
            return driftSeverity;
        }

        public void setDriftSeverity(String driftSeverity) {
            this.driftSeverity = driftSeverity;
        }
    }
}
