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

package org.apache.rocketmq.dashboard.service.impl;

import org.apache.commons.collections.MapUtils;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.dashboard.model.BrokerConfigDriftReport;
import org.apache.rocketmq.dashboard.model.BrokerConfigDriftReport.ConfigDriftItem;
import org.apache.rocketmq.dashboard.service.BrokerConfigDriftService;
import org.apache.rocketmq.dashboard.service.ClusterService;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@Service
public class BrokerConfigDriftServiceImpl implements BrokerConfigDriftService {

    private static final Logger logger = LoggerFactory.getLogger(BrokerConfigDriftServiceImpl.class);

    @Autowired
    private ClusterService clusterService;

    private static final Set<String> SENSITIVE_CONFIG_KEYS = new HashSet<>();

    static {
        SENSITIVE_CONFIG_KEYS.add("flushDiskType");
        SENSITIVE_CONFIG_KEYS.add("brokerRole");
        SENSITIVE_CONFIG_KEYS.add("autoCreateTopicEnable");
        SENSITIVE_CONFIG_KEYS.add("autoCreateSubscriptionGroup");
        SENSITIVE_CONFIG_KEYS.add("sendMessageThreadPoolNums");
    }

    @Override
    public BrokerConfigDriftReport inspectConfigDrift() {
        BrokerConfigDriftReport report = new BrokerConfigDriftReport();

        try {
            ClusterInfo clusterInfo = clusterService.getClusterInfo();
            if (clusterInfo == null || MapUtils.isEmpty(clusterInfo.getBrokerAddrTable())) {
                return report;
            }

            Map<String, Properties> brokerConfigMap = new HashMap<>();
            for (BrokerData brokerData : clusterInfo.getBrokerAddrTable().values()) {
                String masterAddr = brokerData.getBrokerAddrs().get(MixAll.MASTER_ID);
                if (masterAddr != null) {
                    try {
                        Properties properties = clusterService.getBrokerConfig(masterAddr);
                        if (properties != null) {
                            brokerConfigMap.put(masterAddr, properties);
                        }
                    } catch (Exception ex) {
                        logger.warn("Failed to get broker config for {}", masterAddr, ex);
                    }
                }
            }

            report.setTotalBrokersInspected(brokerConfigMap.size());
            if (brokerConfigMap.isEmpty()) {
                return report;
            }

            // Determine baseline from the first master broker
            String baselineAddr = brokerConfigMap.keySet().iterator().next();
            Properties baselineProperties = brokerConfigMap.get(baselineAddr);
            Map<String, String> baselineMap = new HashMap<>();
            for (String key : baselineProperties.stringPropertyNames()) {
                baselineMap.put(key, baselineProperties.getProperty(key));
            }
            report.setBaselineConfigs(baselineMap);

            Set<String> driftedBrokers = new HashSet<>();
            boolean criticalDrift = false;

            for (Map.Entry<String, Properties> entry : brokerConfigMap.entrySet()) {
                String addr = entry.getKey();
                Properties properties = entry.getValue();

                for (String key : SENSITIVE_CONFIG_KEYS) {
                    String expectedVal = baselineMap.get(key);
                    String actualVal = properties.getProperty(key);

                    if (expectedVal != null && actualVal != null && !expectedVal.equals(actualVal)) {
                        driftedBrokers.add(addr);
                        String severity = ("flushDiskType".equals(key) || "brokerRole".equals(key)) ? "HIGH" : "MEDIUM";
                        if ("HIGH".equals(severity)) {
                            criticalDrift = true;
                        }

                        report.getDriftItems().add(new ConfigDriftItem(
                                addr,
                                addr,
                                key,
                                expectedVal,
                                actualVal,
                                severity
                        ));
                    }
                }
            }

            report.setDriftedBrokerCount(driftedBrokers.size());
            report.setHasCriticalDrift(criticalDrift);

        } catch (Exception e) {
            logger.error("Failed to inspect broker config drift", e);
        }

        return report;
    }

    @Override
    public Map<String, String> exportBrokerConfigSnapshot(String brokerAddr) {
        Map<String, String> snapshot = new HashMap<>();
        try {
            Properties properties = clusterService.getBrokerConfig(brokerAddr);
            if (properties != null) {
                for (String key : properties.stringPropertyNames()) {
                    snapshot.put(key, properties.getProperty(key));
                }
            }
        } catch (Exception e) {
            logger.error("Failed to export broker config snapshot for {}", brokerAddr, e);
        }
        return snapshot;
    }
}
