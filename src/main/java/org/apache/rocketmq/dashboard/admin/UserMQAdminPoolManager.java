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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.rocketmq.client.ClientConfig;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Slf4j
public class UserMQAdminPoolManager {


    private final ConcurrentMap<String/* userAk */, GenericObjectPool<MQAdminExt>> userPools = new ConcurrentHashMap<>();

    private final ClientConfig baseClientConfig;

    @Autowired
    public UserMQAdminPoolManager(RMQConfigure rmqConfigure) {
        this.baseClientConfig = new ClientConfig();
        this.baseClientConfig.setNamesrvAddr(rmqConfigure.getNamesrvAddr());
        this.baseClientConfig.setClientCallbackExecutorThreads(rmqConfigure.getClientCallbackExecutorThreads());
        this.baseClientConfig.setVipChannelEnabled(Boolean.parseBoolean(rmqConfigure.getIsVIPChannel()));
        this.baseClientConfig.setUseTLS(rmqConfigure.isUseTLS());
        log.info("UserMQAdminPoolManager initialized with baseClientConfig for NameServer: {}", rmqConfigure.getNamesrvAddr());
    }


    public MQAdminExt borrowMQAdminExt(String userAk, String userSk) throws Exception {
        GenericObjectPool<MQAdminExt> userPool = userPools.get(userAk);

        if (userPool == null) {
            log.info("Creating new MQAdminExt pool for user: {}", userAk);
            GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
            poolConfig.setMaxTotal(1);
            poolConfig.setMaxIdle(1);
            poolConfig.setMinIdle(0);
            poolConfig.setTestWhileIdle(true);
            poolConfig.setTimeBetweenEvictionRunsMillis(20000);
            poolConfig.setMaxWaitMillis(10000);

            UserSpecificMQAdminPooledObjectFactory factory =
                    new UserSpecificMQAdminPooledObjectFactory(baseClientConfig, userAk, userSk);

            GenericObjectPool<MQAdminExt> newUserPool = new GenericObjectPool<>(factory, poolConfig);

            GenericObjectPool<MQAdminExt> existingPool = userPools.putIfAbsent(userAk, newUserPool);
            if (existingPool != null) {
                log.warn("Another thread concurrently created MQAdminExt pool for user {}. Shutting down redundant pool.", userAk);
                newUserPool.close();
                userPool = existingPool;
            } else {
                userPool = newUserPool;
                log.info("Successfully created and registered MQAdminExt pool for user: {}", userAk);
            }
        }

        return userPool.borrowObject();
    }

    public void returnMQAdminExt(String userAk, MQAdminExt mqAdminExt) {
        GenericObjectPool<MQAdminExt> userPool = userPools.get(userAk);
        if (userPool != null) {
            try {
                userPool.returnObject(mqAdminExt);
                log.debug("Returned MQAdminExt object ({}) to pool for user: {}", mqAdminExt, userAk);
            } catch (Exception e) {
                log.error("Failed to return MQAdminExt object ({}) for user {}: {}", mqAdminExt, userAk, e.getMessage(), e);
                if (mqAdminExt != null) {
                    try {
                        mqAdminExt.shutdown();
                    } catch (Exception se) {
                        log.warn("Error shutting down MQAdminExt after failed return: {}", se.getMessage());
                    }
                }
            }
        } else {
            log.warn("Attempted to return MQAdminExt for non-existent user pool: {}. Shutting down the object directly.", userAk);
            if (mqAdminExt != null) {
                try {
                    mqAdminExt.shutdown();
                } catch (Exception se) {
                    log.warn("Error shutting down MQAdminExt for non-existent pool: {}", se.getMessage());
                }
            }
        }
    }

    public void shutdownUserPool(String userAk) {
        GenericObjectPool<MQAdminExt> userPool = userPools.remove(userAk);
        if (userPool != null) {
            userPool.close();
            log.info("Shutdown and removed MQAdminExt pool for user: {}", userAk);
        } else {
            log.warn("Attempted to shut down non-existent user pool: {}", userAk);
        }
    }

    @PreDestroy
    public void shutdownAllPools() {
        log.info("Shutting down all MQAdminExt user pools...");
        userPools.forEach((userAk, pool) -> {
            pool.close();
            log.info("Shutdown MQAdminExt pool for user: {}", userAk);
        });
        userPools.clear();
        log.info("All MQAdminExt user pools have been shut down.");
    }
}
