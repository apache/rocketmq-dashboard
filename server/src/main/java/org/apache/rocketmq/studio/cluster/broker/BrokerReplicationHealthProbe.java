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
package org.apache.rocketmq.studio.cluster.broker;

import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.broker.BrokerHaReportVO.BrokerHaPairVO;
import org.apache.rocketmq.studio.cluster.broker.BrokerHaReportVO.SlaveReplicationStatusVO;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class BrokerReplicationHealthProbe {

    public static final long MINOR_LAG_THRESHOLD_BYTES = 1024L * 1024L; // 1 MiB
    public static final long CRITICAL_LAG_THRESHOLD_BYTES = 64L * 1024L * 1024L; // 64 MiB

    public BrokerHaReportVO probeClusterHaStatus(String clusterId, ClusterInfo clusterInfo, MQAdminExt admin) {
        BrokerHaReportVO report = BrokerHaReportVO.builder()
                .clusterId(clusterId)
                .probeTimestamp(LocalDateTime.now())
                .allReplicasHealthy(true)
                .brokerPairs(new ArrayList<>())
                .haWarnings(new ArrayList<>())
                .build();

        if (clusterInfo == null || clusterInfo.getBrokerAddrTable() == null || clusterInfo.getBrokerAddrTable().isEmpty()) {
            report.setAllReplicasHealthy(false);
            report.getHaWarnings().add("No broker cluster topology available for cluster: " + clusterId);
            return report;
        }

        Map<String, BrokerData> brokerAddrTable = clusterInfo.getBrokerAddrTable();
        int totalMasters = 0;
        int totalSlaves = 0;
        boolean overallHealthy = true;

        for (Map.Entry<String, BrokerData> entry : brokerAddrTable.entrySet()) {
            String brokerName = entry.getKey();
            BrokerData brokerData = entry.getValue();
            HashMap<Long, String> addrs = brokerData.getBrokerAddrs();

            if (addrs == null || addrs.isEmpty()) {
                report.getHaWarnings().add("Broker [" + brokerName + "] has no registered addresses.");
                continue;
            }

            String masterAddr = addrs.get(MixAll.MASTER_ID);
            long masterMaxOffset = 0L;
            boolean masterOnline = false;

            if (masterAddr != null) {
                totalMasters++;
                try {
                    masterMaxOffset = admin.maxOffset(masterAddr);
                    masterOnline = true;
                } catch (Exception e) {
                    masterOnline = false;
                    overallHealthy = false;
                    report.getHaWarnings().add(String.format("Master broker [%s] at %s is unreachable: %s",
                            brokerName, masterAddr, e.getMessage()));
                }
            } else {
                overallHealthy = false;
                report.getHaWarnings().add(String.format("Broker [%s] has no active Master (brokerId=0).", brokerName));
            }

            BrokerHaPairVO pairVO = BrokerHaPairVO.builder()
                    .brokerName(brokerName)
                    .masterAddress(masterAddr)
                    .masterMaxOffset(masterMaxOffset)
                    .masterOnline(masterOnline)
                    .slaves(new ArrayList<>())
                    .build();

            for (Map.Entry<Long, String> addrEntry : addrs.entrySet()) {
                long brokerId = addrEntry.getKey();
                if (brokerId == MixAll.MASTER_ID) {
                    continue; // Skip master
                }

                totalSlaves++;
                String slaveAddr = addrEntry.getValue();
                long slaveMaxOffset = 0L;
                boolean slaveOnline = false;
                String healthStatus;
                String message;
                long lagBytes = 0L;
                boolean inSync = false;

                try {
                    slaveMaxOffset = admin.maxOffset(slaveAddr);
                    slaveOnline = true;
                    if (masterOnline) {
                        lagBytes = Math.max(0, masterMaxOffset - slaveMaxOffset);
                        if (lagBytes == 0) {
                            healthStatus = "IN_SYNC";
                            inSync = true;
                            message = "Slave is fully synchronized with Master.";
                        } else if (lagBytes <= MINOR_LAG_THRESHOLD_BYTES) {
                            healthStatus = "MINOR_LAG";
                            inSync = true;
                            message = String.format("Minor replication lag of %d bytes (< 1 MiB).", lagBytes);
                        } else if (lagBytes <= CRITICAL_LAG_THRESHOLD_BYTES) {
                            healthStatus = "CRITICAL_LAG";
                            inSync = false;
                            overallHealthy = false;
                            message = String.format("Elevated replication lag of %d bytes.", lagBytes);
                        } else {
                            healthStatus = "CRITICAL_LAG";
                            inSync = false;
                            overallHealthy = false;
                            message = String.format("Severe replication lag of %d bytes (> 64 MiB). Risk of failover data loss.", lagBytes);
                        }
                    } else {
                        healthStatus = "OFFLINE";
                        message = "Master is offline; unable to verify replication delta.";
                    }
                } catch (Exception ex) {
                    slaveOnline = false;
                    inSync = false;
                    overallHealthy = false;
                    healthStatus = "OFFLINE";
                    message = "Slave is unreachable: " + ex.getMessage();
                }

                pairVO.getSlaves().add(SlaveReplicationStatusVO.builder()
                        .brokerId(brokerId)
                        .slaveAddress(slaveAddr)
                        .slaveMaxOffset(slaveMaxOffset)
                        .replicationLagBytes(lagBytes)
                        .inSync(inSync)
                        .healthStatus(healthStatus)
                        .message(message)
                        .build());
            }

            if (pairVO.getSlaves().isEmpty()) {
                report.getHaWarnings().add(String.format("Broker [%s] has no configured Slave replicas (Single-Point of Failure).", brokerName));
            }

            report.getBrokerPairs().add(pairVO);
        }

        report.setTotalMasterBrokers(totalMasters);
        report.setTotalSlaveBrokers(totalSlaves);
        report.setAllReplicasHealthy(overallHealthy);

        if (overallHealthy && totalSlaves > 0) {
            report.getHaWarnings().add("All master-slave broker pairs are in healthy replication synchronization.");
        }

        return report;
    }
}
