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

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MQAdminFactory {
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private RMQConfigure rmqConfigure;

    public MQAdminFactory(RMQConfigure rmqConfigure) {
        this.rmqConfigure = rmqConfigure;
    }

    public MQAdminExt getInstance() throws Exception {
        DefaultMQAdminExt mqAdminExt = null;
        RPCHook rpcHook = null;
        boolean isEnableAcl = !StringUtils.isEmpty(rmqConfigure.getAccessKey())
            && !StringUtils.isEmpty(rmqConfigure.getSecretKey());
        if (isEnableAcl) {
            SessionCredentials credentials = new SessionCredentials(rmqConfigure.getAccessKey(),
                rmqConfigure.getSecretKey());
            rpcHook = new AclClientRPCHook(credentials);
        }
        mqAdminExt = new DefaultMQAdminExt(rpcHook);
        mqAdminExt.setVipChannelEnabled(false);
        mqAdminExt.setInstanceName(Long.toString(System.currentTimeMillis()));
        mqAdminExt.start();
        logger.info("create MQAdmin instance {} success.", mqAdminExt);
        return mqAdminExt;
    }
}
