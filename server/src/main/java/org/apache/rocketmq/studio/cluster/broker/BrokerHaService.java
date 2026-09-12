/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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

import org.apache.rocketmq.remoting.protocol.body.HARuntimeInfo;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BrokerHaService {
    private final RuntimeAdminClientResolver resolver;

    public BrokerHaSnapshot inspect(String instanceId, String brokerName) {
        if (brokerName == null || brokerName.isBlank()) {
            throw new BusinessException(400, "brokerName is required");
        }
        String name = brokerName.trim();
        return resolver.execute(instanceId, admin -> {
            var cluster = admin.examineBrokerClusterInfo();
            var broker = cluster.getBrokerAddrTable().get(name);
            if (broker == null || broker.getBrokerAddrs().isEmpty()) {
                throw new BusinessException(404, "Broker has no registered replicas: " + name);
            }
            List<BrokerHaSnapshot.Node> nodes = new ArrayList<>();
            // Query only addresses advertised by this instance's NameServer.
            for (var entry : broker.getBrokerAddrs().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey()).toList()) {
                String id = entry.getKey().toString();
                String address = entry.getValue();
                try {
                    var info = admin.getBrokerHAStatus(address);
                    if (info == null) {
                        throw new BusinessException(502, "Broker returned no HA runtime data");
                    }
                    nodes.add(map(id, address, info));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw interrupted;
                } catch (Exception failure) {
                    nodes.add(new BrokerHaSnapshot.Node(id, address, failure.getMessage() == null
                            ? failure.getClass().getSimpleName() : failure.getMessage(),
                            null, null, null, List.of(), null));
                }
            }
            return new BrokerHaSnapshot(name, System.currentTimeMillis(),
                    nodes.stream().allMatch(node -> node.error() == null), List.copyOf(nodes));
        });
    }

    private BrokerHaSnapshot.Node map(String id, String address, HARuntimeInfo info) {
        if (info.isMaster()) {
            var connections = info.getHaConnectionInfo().stream().map(connection ->
                    new BrokerHaSnapshot.Connection(connection.getAddr(), connection.isInSync(),
                            Long.toString(connection.getSlaveAckOffset()), Long.toString(connection.getDiff()),
                            Long.toString(connection.getTransferFromWhere()),
                            Long.toString(connection.getTransferredByteInSecond()))).toList();
            return new BrokerHaSnapshot.Node(id, address, null, true,
                    Long.toString(info.getMasterCommitLogMaxOffset()), info.getInSyncSlaveNums(), connections, null);
        }
        var client = info.getHaClientRuntimeInfo();
        if (client == null) {
            throw new BusinessException(502, "Replica returned no HA client data");
        }
        var replica = new BrokerHaSnapshot.Replica(client.getMasterAddr(), Long.toString(client.getMaxOffset()),
                Long.toString(client.getMasterFlushOffset()), Long.toString(client.getTransferredByteInSecond()),
                client.getLastReadTimestamp(), client.getLastWriteTimestamp());
        return new BrokerHaSnapshot.Node(id, address, null, false, replica.maxOffset(), null, List.of(), replica);
    }
}
