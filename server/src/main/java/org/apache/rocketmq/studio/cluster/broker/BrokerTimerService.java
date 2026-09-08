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

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BrokerTimerService {
    private static final List<String> CONFIG_KEYS = List.of("timerWheelEnable", "timerStopEnqueue",
            "timerStopDequeue", "timerRocksDBEnable", "timerRocksDBStopScan", "recallMessageEnable",
            "timerPrecisionMs", "timerMaxDelaySec");
    private static final List<String> RUNTIME_KEYS = List.of("timerReadBehind", "timerOffsetBehind",
            "timerCongestNum", "timerEnqueueTps", "timerDequeueTps");
    private final RuntimeAdminClientResolver resolver;

    public BrokerTimerSnapshot inspect(String instanceId, String brokerName) {
        if (brokerName == null || brokerName.isBlank()) {
            throw new BusinessException(400, "brokerName is required");
        }
        String name = brokerName.trim();
        return resolver.execute(instanceId, admin -> {
            var cluster = admin.examineBrokerClusterInfo();
            var broker = cluster.getBrokerAddrTable().get(name);
            if (broker == null) {
                throw new BusinessException(404, "Broker is not registered: " + name);
            }
            // Timer progress belongs to the registered master, not an arbitrary reachable replica.
            String address = broker.getBrokerAddrs().get(MixAll.MASTER_ID);
            if (address == null || address.isBlank()) {
                throw new BusinessException(409, "Broker has no registered master: " + name);
            }
            var configuration = read(() -> {
                var properties = admin.getBrokerConfig(address);
                if (properties == null) {
                    throw new BusinessException(502, "Broker returned no configuration");
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (String key : CONFIG_KEYS) {
                    String value = properties.getProperty(key);
                    if (value != null) {
                        values.put(key, value);
                    }
                }
                return values;
            });
            var runtime = read(() -> {
                var stats = admin.fetchBrokerRuntimeStats(address);
                if (stats == null || stats.getTable() == null) {
                    throw new BusinessException(502, "Broker returned no runtime statistics");
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (String key : RUNTIME_KEYS) {
                    String value = stats.getTable().get(key);
                    if (value != null) {
                        values.put(key, value);
                    }
                }
                return values;
            });
            return new BrokerTimerSnapshot(name, address, System.currentTimeMillis(), configuration, runtime);
        });
    }

    private BrokerTimerSnapshot.Section read(Probe probe) throws InterruptedException {
        try {
            return new BrokerTimerSnapshot.Section(Map.copyOf(probe.read()), null);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (Exception failure) {
            return new BrokerTimerSnapshot.Section(Map.of(), failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage());
        }
    }

    @FunctionalInterface
    private interface Probe {
        Map<String, String> read() throws Exception;
    }
}
