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

import com.google.common.base.Throwables;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.proxy.config.ProxyConfig;
import org.apache.rocketmq.remoting.RemotingClient;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.remoting.protocol.RemotingSerializable;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ProxyAdminImpl implements ProxyAdmin {
    @Autowired
    private GenericObjectPool<MQAdminExt> mqAdminExtPool;

    @Override
    public ProxyConfig examineProxyConfig(String addr) throws MQBrokerException {
        try {
            MQAdminInstance.createMQAdmin(mqAdminExtPool);
            RemotingClient remotingClient = MQAdminInstance.threadLocalRemotingClient();
            RemotingCommand request = RemotingCommand.createRequestCommand(514, null);
            RemotingCommand response = null;
            try {
                response = remotingClient.invokeSync(addr, request, 3000);
            } catch (Exception err) {
                Throwables.throwIfUnchecked(err);
                throw new RuntimeException(err);
            }
            switch (response.getCode()) {
                case ResponseCode.SUCCESS: {
                    ProxyConfig proxyConfig = RemotingSerializable.decode(response.getBody(), ProxyConfig.class);
                    log.info("addr=" + addr + ",proxyConfig=" + proxyConfig);
                    return proxyConfig;
                }
                default:
                    throw new MQBrokerException(response.getCode(), response.getRemark());
            }
        } finally {
            MQAdminInstance.returnMQAdmin(mqAdminExtPool);
        }
    }

}
