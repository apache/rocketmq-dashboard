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
package org.apache.rocketmq.studio.rocketmq;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.exception.RemotingTimeoutException;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.studio.persistence.mapper.RmqGroupMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTopicMapper;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocketMQAdminClientImplTest {

    @Mock
    private DefaultMQAdminExt adminExt;

    @Mock
    private RocketMQProperties properties;

    @Mock
    private RmqTopicMapper topicMapper;

    @Mock
    private RmqGroupMapper groupMapper;

    @Mock
    private AuditService auditService;

    private RocketMQAdminClientImpl adminClient;

    @BeforeEach
    void setUp() {
        adminClient = new RocketMQAdminClientImpl(
                adminExt, properties, topicMapper, groupMapper, auditService);
    }

    @Test
    void getConsumerGroupReturnsOfflineDetailForConsumerNotOnline() throws Exception {
        when(adminExt.examineConsumerConnectionInfo("orders"))
                .thenThrow(new MQClientException(
                        ResponseCode.CONSUMER_NOT_ONLINE, "Not found the consumer group connection"));

        ConsumerGroupVO group = adminClient.getConsumerGroup("orders");

        assertThat(group.getId()).isEqualTo("orders");
        assertThat(group.getName()).isEqualTo("orders");
        assertThat(group.getOnlineInstances()).isZero();
    }

    @Test
    void getConsumerGroupSurfacesAdminTimeout() throws Exception {
        when(adminExt.examineConsumerConnectionInfo("orders"))
                .thenThrow(new RemotingTimeoutException("broker-0", 3_000));

        assertThatThrownBy(() -> adminClient.getConsumerGroup("orders"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getCode()).isEqualTo(502);
                    assertThat(businessException.getMessage()).contains("Failed to get consumer group");
                });
    }

    @Test
    void getConsumerGroupSurfacesBrokerFailures() throws Exception {
        when(adminExt.examineConsumerConnectionInfo("orders"))
                .thenThrow(new MQBrokerException(16, "ACL denied"));

        assertThatThrownBy(() -> adminClient.getConsumerGroup("orders"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getCode()).isEqualTo(502);
                    assertThat(businessException.getMessage()).contains("ACL denied");
                });
    }
}
