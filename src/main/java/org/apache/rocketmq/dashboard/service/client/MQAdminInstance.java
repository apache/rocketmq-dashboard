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

import org.apache.rocketmq.client.impl.MQClientAPIImpl;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.remoting.RemotingClient;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExtImpl;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.joor.Reflect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MQAdminInstance {

    private static final Logger LOGGER = LoggerFactory.getLogger(MQAdminInstance.class);
    // ThreadLocal to store the MQAdminExt instance for the current thread.
    private static final ThreadLocal<MQAdminExt> MQ_ADMIN_EXT_THREAD_LOCAL = new ThreadLocal<>();

    /**
     * Retrieves the MQAdminExt instance associated with the current thread.
     *
     * @return The MQAdminExt instance.
     * @throws IllegalStateException if no MQAdminExt instance has been set for the current thread.
     */
    public static MQAdminExt threadLocalMQAdminExt() {
        MQAdminExt mqAdminExt = MQ_ADMIN_EXT_THREAD_LOCAL.get();
        if (mqAdminExt == null) {
            // This indicates a programming error: MQAdminExt was not set by the aspect.
            throw new IllegalStateException("MQAdminExt instance should be set by MQAdminAspect before it's accessed.");
        }
        return mqAdminExt;
    }

    /**
     * Retrieves the RemotingClient from the MQAdminExt instance in the current thread.
     * This method relies on reflection and the internal structure of RocketMQ classes.
     *
     * @return The RemotingClient instance.
     */
    public static RemotingClient threadLocalRemotingClient() { // Assuming RemotingClient is a type you have
        MQClientInstance mqClientInstance = threadLocalMqClientInstance();
        // Use jOOQ-Reflect to access private field "mQClientAPIImpl" from mqClientInstance
        MQClientAPIImpl mQClientAPIImpl = Reflect.on(mqClientInstance).get("mQClientAPIImpl");
        // Use jOOQ-Reflect to access private field "remotingClient" from mQClientAPIImpl
        return Reflect.on(mQClientAPIImpl).get("remotingClient");
    }

    /**
     * Retrieves the MQClientInstance from the MQAdminExt instance in the current thread.
     * This method relies on reflection and the internal structure of RocketMQ classes.
     *
     * @return The MQClientInstance instance.
     */
    public static MQClientInstance threadLocalMqClientInstance() {
        // Use jOOQ-Reflect to access private field "defaultMQAdminExtImpl" from threadLocalMQAdminExt()
        DefaultMQAdminExtImpl defaultMQAdminExtImpl = Reflect.on(MQAdminInstance.threadLocalMQAdminExt()).get("defaultMQAdminExtImpl");
        // Use jOOQ-Reflect to access private field "mqClientInstance" from defaultMQAdminExtImpl
        return Reflect.on(defaultMQAdminExtImpl).get("mqClientInstance");
    }

    /**
     * Sets the MQAdminExt instance for the current thread.
     * This method should be called by the aspect after borrowing an instance from the pool.
     *
     * @param mqAdminExt The MQAdminExt instance to set.
     */
    public static void setCurrentMQAdminExt(MQAdminExt mqAdminExt) {
        MQ_ADMIN_EXT_THREAD_LOCAL.set(mqAdminExt);
        LOGGER.debug("Set MQAdminExt instance for current thread: {}", mqAdminExt);
    }

    /**
     * Clears the MQAdminExt instance from the current thread.
     * This method should be called by the aspect after returning the instance to the pool.
     */
    public static void clearCurrentMQAdminExt() {
        MQ_ADMIN_EXT_THREAD_LOCAL.remove();
        LOGGER.debug("Cleared MQAdminExt instance from current thread.");
    }

}
