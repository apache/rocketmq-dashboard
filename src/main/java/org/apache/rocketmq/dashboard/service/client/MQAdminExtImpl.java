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

import java.util.HashMap;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MQAdminExtImpl extends DefaultMQAdminExt implements MQAdminExt {
    private Logger logger = LoggerFactory.getLogger(MQAdminExtImpl.class);


    public MQAdminExtImpl() {
		super();
	}

	public MQAdminExtImpl(long timeoutMillis) {
		super(timeoutMillis);
	}

	public MQAdminExtImpl(RPCHook rpcHook, long timeoutMillis) {
		super(rpcHook, timeoutMillis);
	}

	public MQAdminExtImpl(RPCHook rpcHook) {
		super(rpcHook);
	}

	public MQAdminExtImpl(String adminExtGroup, long timeoutMillis) {
		super(adminExtGroup, timeoutMillis);
	}

	public MQAdminExtImpl(String adminExtGroup) {
		super(adminExtGroup);
	}
    
    public void createTopic(String key, String newTopic, int queueNum) throws MQClientException {
        super.createTopic(key, newTopic, queueNum, new HashMap<>());
    }

    public void createTopic(String key, String newTopic, int queueNum, int topicSysFlag)
        throws MQClientException {
        super.createTopic(key, newTopic, queueNum, topicSysFlag, new HashMap<>());
    }
}
