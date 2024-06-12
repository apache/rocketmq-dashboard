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
package org.apache.rocketmq.dashboard.service.client;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.remoting.RemotingClient;
import org.apache.rocketmq.remoting.exception.RemotingConnectException;
import org.apache.rocketmq.remoting.exception.RemotingSendRequestException;
import org.apache.rocketmq.remoting.exception.RemotingTimeoutException;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.header.GetConsumerConnectionListRequestHeader;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static org.apache.rocketmq.remoting.protocol.RequestCode.GET_CONSUMER_CONNECTION_LIST;

@Slf4j
@Service
public class ProxyAdminImpl implements ProxyAdmin {
    @Autowired
    private GenericObjectPool<MQAdminExt> mqAdminExtPool;

    @Override
    public ConsumerConnection examineConsumerConnectionInfo(String addr, String consumerGroup) throws RemotingConnectException, RemotingSendRequestException, RemotingTimeoutException, InterruptedException, MQBrokerException {
        try {
            MQAdminInstance.createMQAdmin(mqAdminExtPool);
            RemotingClient remotingClient = MQAdminInstance.threadLocalRemotingClient();
            GetConsumerConnectionListRequestHeader requestHeader = new GetConsumerConnectionListRequestHeader();
            requestHeader.setConsumerGroup(consumerGroup);
            RemotingCommand request = RemotingCommand.createRequestCommand(GET_CONSUMER_CONNECTION_LIST, requestHeader);
            RemotingCommand response = remotingClient.invokeSync(addr, request, 3000);
            switch (response.getCode()) {
                case 0:
                    return ConsumerConnection.decode(response.getBody(), ConsumerConnection.class);
                default:
                    throw new MQBrokerException(response.getCode(), response.getRemark(), addr);
            }
        } finally {
            MQAdminInstance.returnMQAdmin(mqAdminExtPool);
        }
    }
}
