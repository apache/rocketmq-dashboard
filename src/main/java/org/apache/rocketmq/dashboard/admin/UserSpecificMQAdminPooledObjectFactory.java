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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.ClientConfig;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.MQAdminExt;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class UserSpecificMQAdminPooledObjectFactory implements PooledObjectFactory<MQAdminExt> {

    private final ClientConfig userSpecificClientConfig;
    private final RPCHook rpcHook;
    private final String userAk;
    private final AtomicLong instanceCreationCounter = new AtomicLong(0);

    public UserSpecificMQAdminPooledObjectFactory(ClientConfig baseClientConfig, String userAk, String userSk) {
        this.userSpecificClientConfig = baseClientConfig.cloneClientConfig();
        this.userSpecificClientConfig.setInstanceName("MQ_ADMIN_INSTANCE_" + userAk + "_" + UUID.randomUUID());

        if (StringUtils.isNotEmpty(userAk) && StringUtils.isNotEmpty(userSk)) {
            this.rpcHook = new AclClientRPCHook(new SessionCredentials(userAk, userSk));
        } else {
            this.rpcHook = null;
        }
        this.userAk = userAk;

        log.info("UserSpecificMQAdminPooledObjectFactory initialized for user: {}", userAk);
        log.debug("Factory ClientConfig for user {}: {}", userAk, userSpecificClientConfig);
    }

    @Override
    public PooledObject<MQAdminExt> makeObject() throws Exception {
        DefaultMQAdminExt mqAdminExt = new DefaultMQAdminExt(rpcHook);

        mqAdminExt.setAdminExtGroup("MQ_ADMIN_GROUP_FOR_" + userAk + "_" + instanceCreationCounter.getAndIncrement());

        mqAdminExt.start();
        log.info("Created new MQAdminExt instance ({}) for user {}", mqAdminExt, userAk);
        return new DefaultPooledObject<>(mqAdminExt);
    }

    @Override
    public void destroyObject(PooledObject<MQAdminExt> p) {
        MQAdminExt mqAdmin = p.getObject();
        if (mqAdmin != null) {
            try {
                mqAdmin.shutdown();
            } catch (Exception e) {
                log.warn("Failed to shut down MQAdminExt object ({}) for user {}: {}", p.getObject(), userAk, e.getMessage(), e);
            }
        }
        log.info("Destroyed MQAdminExt object ({}) for user {}", p.getObject(), userAk);
    }


    @Override
    public boolean validateObject(PooledObject<MQAdminExt> p) {
        MQAdminExt mqAdmin = p.getObject();
        if (mqAdmin == null) {
            log.warn("MQAdminExt object is null or not started for user {}: {}", userAk, mqAdmin);
            return false;
        }
        try {
            ClusterInfo clusterInfo = mqAdmin.examineBrokerClusterInfo();
            boolean isValid = clusterInfo != null && !clusterInfo.getBrokerAddrTable().isEmpty();
            if (!isValid) {
                log.warn("Validation failed for MQAdminExt object for user {}: ClusterInfo is invalid or empty. ClusterInfo = {}", userAk, clusterInfo);
            }
            return isValid;
        } catch (Exception e) {
            log.warn("Validation error for MQAdminExt object for user {}: {}", userAk, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void activateObject(PooledObject<MQAdminExt> p) {
        log.debug("Activating MQAdminExt object ({}) for user {}", p.getObject(), userAk);
    }

    @Override
    public void passivateObject(PooledObject<MQAdminExt> p) {
        log.debug("Passivating MQAdminExt object ({}) for user {}", p.getObject(), userAk);
    }
}
