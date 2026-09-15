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

    /** Freezes the endpoint and transport values for one default-client operation. */
    public Selection select(String externalDefault) {
        OpsConnectionSettings settings = runtimeProperties.isEnabled()
                ? runtimeConnection.current() : null;
        return new Selection(externalDefault, settings);
    }

    public final class Selection {
        private final String externalDefault;
        private final OpsConnectionSettings settings;

        private Selection(String externalDefault, OpsConnectionSettings settings) {
            this.externalDefault = externalDefault;
            this.settings = settings;
        }

        public String namesrvAddr() {
            return settings == null ? externalDefault : settings.currentNamesrv();
        }

        public <T> T execute(RPCHook hook, String identity, MqAdminExtFactory.AdminAction<T> action) {
            return settings == null
                    ? adminFactory.execute(externalDefault, hook, identity, action)
                    : adminFactory.executeDefault(settings, hook, identity, action);
        }

        public <T> T withProducer(MqClientPool.ClientAction<DefaultMQProducer, T> action) {
            return settings == null
                    ? clientPool.withProducer(externalDefault, null, null, action)
                    : clientPool.withProducerDefault(settings, null, "anonymous", action);
        }

        public <T> T withPullConsumer(MqClientPool.ClientAction<DefaultMQPullConsumer, T> action) {
            return settings == null
                    ? clientPool.withPullConsumer(externalDefault, null, null, action)
                    : clientPool.withPullConsumerDefault(settings, null, "anonymous", action);
        }
    }

    public String namesrvAddr(String externalDefault) {
        return select(externalDefault).namesrvAddr();
    }

    public <T> T execute(String externalDefault, RPCHook hook, String identity,
                         MqAdminExtFactory.AdminAction<T> action) {
        return select(externalDefault).execute(hook, identity, action);
    }

    public <T> T withProducer(String externalDefault,
                              MqClientPool.ClientAction<DefaultMQProducer, T> action) {
        return select(externalDefault).withProducer(action);
    }

    public <T> T withPullConsumer(String externalDefault,
                                  MqClientPool.ClientAction<DefaultMQPullConsumer, T> action) {
        return select(externalDefault).withPullConsumer(action);
    }
}
