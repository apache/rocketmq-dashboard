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

package org.apache.rocketmq.dashboard.admin;

import org.apache.commons.pool2.PooledObject;
import org.apache.rocketmq.client.ClientConfig;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Assert;
import org.junit.Test;

public class UserSpecificMQAdminPooledObjectFactoryTest {

    @Test
    public void testMakeObjectAppliesUserSpecificClientConfig() throws Exception {
        ClientConfig baseClientConfig = new ClientConfig();
        baseClientConfig.setNamesrvAddr("127.0.0.1:19876");
        baseClientConfig.setClientCallbackExecutorThreads(7);
        baseClientConfig.setVipChannelEnabled(false);
        baseClientConfig.setUseTLS(true);
        UserSpecificMQAdminPooledObjectFactory factory =
                new UserSpecificMQAdminPooledObjectFactory(baseClientConfig, "test-user", "test-secret");

        PooledObject<MQAdminExt> pooledObject = factory.makeObject();
        try {
            DefaultMQAdminExt mqAdminExt = (DefaultMQAdminExt) pooledObject.getObject();
            Assert.assertEquals(baseClientConfig.getNamesrvAddr(), mqAdminExt.getNamesrvAddr());
            Assert.assertEquals(baseClientConfig.getClientCallbackExecutorThreads(),
                    mqAdminExt.getClientCallbackExecutorThreads());
            Assert.assertEquals(baseClientConfig.isVipChannelEnabled(), mqAdminExt.isVipChannelEnabled());
            Assert.assertEquals(baseClientConfig.isUseTLS(), mqAdminExt.isUseTLS());
            Assert.assertTrue(mqAdminExt.getInstanceName().startsWith("MQ_ADMIN_INSTANCE_test-user_"));
        } finally {
            factory.destroyObject(pooledObject);
        }
    }
}
