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

import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.studio.ops.OpsConnectionSettings;
import org.apache.rocketmq.studio.ops.OpsRuntimeConnection;
import org.apache.rocketmq.studio.ops.OpsRuntimeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Applies Ops-managed settings only to Studio's default, non-instance client paths. */
@Component
@RequiredArgsConstructor
public class OpsDefaultClient {

    private final OpsRuntimeConnection runtimeConnection;
    private final OpsRuntimeProperties runtimeProperties;
    private final MqAdminExtFactory adminFactory;
    private final MqClientPool clientPool;

    public String namesrvAddr(String externalDefault) {
        return runtimeProperties.isEnabled()
                ? runtimeConnection.current().currentNamesrv() : externalDefault;
    }

    public <T> T execute(String externalDefault, RPCHook hook, String identity,
                         MqAdminExtFactory.AdminAction<T> action) {
        if (runtimeProperties.isEnabled()) {
            OpsConnectionSettings settings = runtimeConnection.current();
            return adminFactory.executeDefault(settings, hook, identity, action);
        }
        return adminFactory.execute(externalDefault, hook, identity, action);
    }

    public <T> T withProducer(String externalDefault,
                              MqClientPool.ClientAction<DefaultMQProducer, T> action) {
        if (runtimeProperties.isEnabled()) {
            return clientPool.withProducerDefault(runtimeConnection.current(), null, "anonymous", action);
        }
        return clientPool.withProducer(externalDefault, null, null, action);
    }

    public <T> T withPullConsumer(String externalDefault,
                                  MqClientPool.ClientAction<DefaultMQPullConsumer, T> action) {
        if (runtimeProperties.isEnabled()) {
            return clientPool.withPullConsumerDefault(runtimeConnection.current(), null, "anonymous", action);
        }
        return clientPool.withPullConsumer(externalDefault, null, null, action);
    }
}
