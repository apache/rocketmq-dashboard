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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.rocketmq.dashboard.admin;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.rocketmq.common.protocol.body.ClusterInfo;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MQAdminPooledObjectFactory implements PooledObjectFactory<MQAdminExt> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private MQAdminFactory mqAdminFactory;

    @Override
    public PooledObject<MQAdminExt> makeObject() throws Exception {
        DefaultPooledObject<MQAdminExt> pooledObject = new DefaultPooledObject<>(
            mqAdminFactory.getInstance());
        return pooledObject;
    }

    @Override
    public void destroyObject(PooledObject<MQAdminExt> p) {
        MQAdminExt mqAdmin = p.getObject();
        if (mqAdmin != null) {
            try {
                mqAdmin.shutdown();
            } catch (Exception e) {
                logger.warn("MQAdminExt shutdown err", e);
            }
        }
        logger.info("destroy object {}", p.getObject());
    }

    @Override
    public boolean validateObject(PooledObject<MQAdminExt> p) {
        MQAdminExt mqAdmin = p.getObject();
        ClusterInfo clusterInfo = null;
        try {
            clusterInfo = mqAdmin.examineBrokerClusterInfo();
        } catch (Exception e) {
            logger.warn("validate object {} err", p.getObject(), e);
        }
        if (clusterInfo == null) {
            return false;
        }
        if (clusterInfo.getBrokerAddrTable() == null) {
            return false;
        }
        if (clusterInfo.getBrokerAddrTable().size() <= 0) {
            return false;
        }
        return true;
    }

    @Override
    public void activateObject(PooledObject<MQAdminExt> p) {

    }

    @Override
    public void passivateObject(PooledObject<MQAdminExt> p) {
    }

    public void setMqAdminFactory(MQAdminFactory mqAdminFactory) {
        this.mqAdminFactory = mqAdminFactory;
    }
}