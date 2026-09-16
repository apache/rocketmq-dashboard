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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class ColdReadService {
    private static final String ADAPTIVE_SUFFIX = "||adaptive";
    private final RuntimeAdminClientResolver resolver;
    private final ObjectMapper mapper;
    private final OperationAuditService audit;

    public ColdReadSnapshot inspect(String instanceId, String brokerName) {
        return execute(instanceId, admin -> {
            String address = address(admin, brokerName);
            var info = parse(admin.getColdDataFlowCtrInfo(address));
            var config = admin.getBrokerConfig(address);
            if (config == null) {
                throw new BusinessException(502, "Broker returned no configuration");
            }
            var runtime = info.path("runtimeTable");
            var thresholds = info.path("configTable");
            TreeSet<String> names = new TreeSet<>();
            runtime.fieldNames().forEachRemaining(names::add);
            thresholds.fieldNames().forEachRemaining(key -> names.add(key.endsWith(ADAPTIVE_SUFFIX)
                    ? key.substring(0, key.length() - ADAPTIVE_SUFFIX.length()) : key));
            List<ColdReadSnapshot.Group> groups = names.stream().map(name -> new ColdReadSnapshot.Group(name,
                    number(thresholds.path(name)), number(thresholds.path(name + ADAPTIVE_SUFFIX)),
                    number(runtime.path(name).path("coldAcc")),
                    number(runtime.path(name).path("lastColdReadTimeMills")))).toList();
            return new ColdReadSnapshot(brokerName.trim(), address, System.currentTimeMillis(),
                    config.getProperty("coldDataFlowControlEnable"), config.getProperty("coldCtrStrategyEnable"),
                    number(info.path("cgColdReadThreshold")), number(info.path("globalColdReadThreshold")),
                    number(info.path("globalAcc")), groups);
        });
    }

    public ColdReadReceipt change(ColdReadCommand command) {
        String threshold = validate(command);
        String detail = "broker=" + command.brokerName() + ", group=" + command.group()
                + ", action=" + command.action() + ", threshold=" + threshold;
        try {
            var receipt = execute(command.instanceId(), admin -> {
                String address = address(admin, command.brokerName());
                if (command.action() == ColdReadCommand.Action.SET) {
                    Properties properties = new Properties();
                    properties.setProperty(command.group(), threshold);
                    admin.updateColdDataFlowCtrGroupConfig(address, properties);
                } else {
                    admin.removeColdDataFlowCtrGroupConfig(address, command.group());
                }
                // The broker can acknowledge malformed entries without applying them; read back this one key.
                try {
                    var actual = number(parse(admin.getColdDataFlowCtrInfo(address)).path("configTable")
                            .path(command.group()));
                    boolean verified = Objects.equals(threshold, actual);
                    return new ColdReadReceipt(address, command.group(), command.action(), threshold, verified,
                            verified ? null : "Broker acknowledged the command but read-back differs; refresh before retrying.");
                } catch (Exception verificationFailure) {
                    if (verificationFailure instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    return new ColdReadReceipt(address, command.group(), command.action(), threshold, false,
                            "Broker acknowledged the command; read-back is unavailable. Refresh before retrying.");
                }
            });
            audit.record("CHANGE_COLD_READ_LIMIT", "BROKER", command.brokerName(), command.instanceId(),
                    detail + ", address=" + receipt.address() + ", verified=" + receipt.verified(),
                    "SUCCESS", null);
            return receipt;
        } catch (RuntimeException failure) {
            audit.record("CHANGE_COLD_READ_LIMIT", "BROKER", command.brokerName(), command.instanceId(),
                    detail, "FAILED", failure.getMessage());
            throw failure;
        }
    }

    private <T> T execute(String instanceId, MqAdminExtFactory.AdminAction<T> action) {
        return resolver.execute(instanceId, admin -> {
            try {
                return action.apply(admin);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            }
        });
    }

    private String validate(ColdReadCommand command) {
        if (command.group() == null || !command.group().matches("[%a-zA-Z0-9_|-]{1,255}")
                || command.group().endsWith(ADAPTIVE_SUFFIX)) {
            throw new BusinessException(400, "A consumer group name without the reserved adaptive suffix is required");
        }
        if (command.action() == null) {
            throw new BusinessException(400, "action is required");
        }
        if (command.action() == ColdReadCommand.Action.REMOVE) {
            if (command.threshold() != null) {
                throw new BusinessException(400, "REMOVE must not include a threshold");
            }
            return null;
        }
        try {
            if (command.threshold() == null || !command.threshold().matches("[1-9][0-9]{0,18}")) {
                throw new NumberFormatException();
            }
            return Long.toString(Long.parseLong(command.threshold()));
        } catch (NumberFormatException invalid) {
            throw new BusinessException(400, "threshold must be a positive 64-bit integer in bytes");
        }
    }

    private String address(MQAdminExt admin, String brokerName) throws Exception {
        if (brokerName == null || brokerName.isBlank()) {
            throw new BusinessException(400, "brokerName is required");
        }
        var broker = admin.examineBrokerClusterInfo().getBrokerAddrTable().get(brokerName.trim());
        if (broker == null) {
            throw new BusinessException(404, "Broker is not registered: " + brokerName);
        }
        String address = broker.getBrokerAddrs().get(0L);
        if (address == null || address.isBlank()) {
            throw new BusinessException(409, "Broker has no registered master: " + brokerName);
        }
        return address;
    }

    private JsonNode parse(String json) throws Exception {
        if (json == null || json.isBlank()) {
            throw new BusinessException(502, "Broker returned no cold-read data");
        }
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new BusinessException(502, "Broker returned invalid cold-read JSON");
        }
        if (!root.path("configTable").isObject() || !root.path("runtimeTable").isObject()) {
            throw new BusinessException(502, "Broker returned unsupported cold-read tables");
        }
        return root;
    }

    private String number(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new BusinessException(502, "Broker returned an invalid cold-read counter");
        }
        return value.asText();
    }
}
