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
package org.apache.rocketmq.studio.instance.message;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageId;
import org.apache.rocketmq.common.topic.TopicValidator;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.provider.apache.BrokerTopologyGuards;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TransactionRecoveryService {
    private static final String SOURCE_TOPIC = TopicValidator.RMQ_SYS_TRANS_CHECK_MAX_TIME_TOPIC;
    private final RuntimeAdminClientResolver resolver;
    private final OperationAuditService audit;

    public TransactionRecoveryPreview preview(TransactionRecoveryCommand command) {
        String id = normalize(command.offsetMessageId());
        return execute(command.instanceId(), admin -> inspect(admin, id));
    }

    public TransactionRecoveryPreview recover(TransactionRecoveryCommand command) {
        String id = normalize(command.offsetMessageId());
        try {
            var result = execute(command.instanceId(), admin -> {
                // Re-read on submission: a browser preview never authorizes a different source message.
                var inspected = inspect(admin, id);
                if (!admin.resumeCheckHalfMessage(SOURCE_TOPIC, id)) {
                    throw new BusinessException(502, "Broker did not accept transaction check recovery");
                }
                return inspected;
            });
            audit.record("RECOVER_TRANSACTION_CHECK", "MESSAGE", id, command.instanceId(),
                    "topic=" + result.originalTopic() + ", producerGroup=" + result.producerGroup()
                            + ", broker=" + result.brokerAddress(), "SUCCESS", null);
            return result;
        } catch (RuntimeException failure) {
            audit.record("RECOVER_TRANSACTION_CHECK", "MESSAGE", id, command.instanceId(),
                    "sourceTopic=" + SOURCE_TOPIC, "FAILED", failure.getMessage());
            throw failure;
        }
    }

    private TransactionRecoveryPreview inspect(MQAdminExt admin, String id) throws Exception {
        MessageId decoded = MessageDecoder.decodeMessageId(id);
        if (decoded.getOffset() < 0) {
            throw new BusinessException(400, "Physical message offset must be non-negative");
        }
        String address = BrokerTopologyGuards.validatedBrokerAddr(admin, id, decoded);
        if (address == null) {
            throw new BusinessException(400, "Message broker is outside the selected instance");
        }
        boolean master = admin.examineBrokerClusterInfo().getBrokerAddrTable().values().stream()
                .anyMatch(broker -> address.equals(broker.getBrokerAddrs().get(0L)));
        if (!master) {
            throw new BusinessException(409, "Recovery requires the original message broker to be a registered master");
        }
        var message = admin.viewMessage(SOURCE_TOPIC, id);
        if (message == null) {
            throw new BusinessException(404, "Discarded transaction message was not found");
        }
        if (!SOURCE_TOPIC.equals(message.getTopic())) {
            throw new BusinessException(400, "Only messages in " + SOURCE_TOPIC + " can be recovered");
        }
        if (!decoded.getAddress().equals(message.getStoreHost()) || decoded.getOffset() != message.getCommitLogOffset()) {
            throw new BusinessException(409, "Returned message does not match the requested physical location");
        }
        String topic = message.getProperty(MessageConst.PROPERTY_REAL_TOPIC);
        String group = message.getProperty(MessageConst.PROPERTY_PRODUCER_GROUP);
        if (topic == null || topic.isBlank() || group == null || group.isBlank()) {
            throw new BusinessException(422, "Discarded message is missing its original topic or producer group");
        }
        return new TransactionRecoveryPreview(id, address, topic, group,
                message.getProperty(MessageConst.PROPERTY_TRANSACTION_CHECK_TIMES),
                message.getTransactionId(), message.getStoreTimestamp());
    }

    private String normalize(String id) {
        if (id == null || !id.matches("(?i)([0-9a-f]{32}|[0-9a-f]{56})")) {
            throw new BusinessException(400, "A 32- or 56-character physical offset message ID is required");
        }
        return id.toUpperCase(Locale.ROOT);
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
}
