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

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqAdminExtObjectPool {

    @Autowired
    private RMQConfigure rmqConfigure;

    @Bean
    public GenericObjectPool<MQAdminExt> mqAdminExtPool() {
        GenericObjectPoolConfig genericObjectPoolConfig = new GenericObjectPoolConfig();
        genericObjectPoolConfig.setTestWhileIdle(true);
        genericObjectPoolConfig.setMaxWaitMillis(10000);
        genericObjectPoolConfig.setTimeBetweenEvictionRunsMillis(20000);
        MQAdminPooledObjectFactory mqAdminPooledObjectFactory = new MQAdminPooledObjectFactory();
        MQAdminFactory mqAdminFactory = new MQAdminFactory(rmqConfigure);
        mqAdminPooledObjectFactory.setMqAdminFactory(mqAdminFactory);
        GenericObjectPool<MQAdminExt> genericObjectPool = new GenericObjectPool<MQAdminExt>(
            mqAdminPooledObjectFactory,
            genericObjectPoolConfig);
        return genericObjectPool;
    }
}
