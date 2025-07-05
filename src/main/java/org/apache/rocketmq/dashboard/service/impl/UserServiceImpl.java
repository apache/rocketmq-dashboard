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

package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.auth.authentication.enums.UserType;
import org.apache.rocketmq.dashboard.admin.UserMQAdminPoolManager;
import org.apache.rocketmq.dashboard.model.User;
import org.apache.rocketmq.dashboard.service.UserService;
import org.apache.rocketmq.dashboard.service.strategy.UserContext;
import org.apache.rocketmq.remoting.protocol.body.UserInfo;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);


    @Autowired
    private UserContext userContext;

    @Autowired
    private UserMQAdminPoolManager userMQAdminPoolManager;


    @Override
    public User queryByName(String name) {
        UserInfo userInfo = userContext.queryByUsername(name);
        if (userInfo == null) {
            return null;
        }
        return new User(userInfo.getUsername(), userInfo.getPassword(), UserType.getByName(userInfo.getUserType()).getCode());
    }

    @Override
    public User queryByUsernameAndPassword(String username, String password) {
        User user = queryByName(username);
        if (user != null && !user.getPassword().equals(password)) {
            return null;
        }

        return user;
    }

    public MQAdminExt getMQAdminExtForUser(User user) throws Exception {
        if (user == null) {
            throw new IllegalArgumentException("User object cannot be null when requesting MQAdminExt.");
        }
        return userMQAdminPoolManager.borrowMQAdminExt(user.getName(), user.getPassword());
    }

    public void returnMQAdminExtForUser(User user, MQAdminExt mqAdminExt) {
        if (user == null || mqAdminExt == null) {
            log.warn("Attempted to return MQAdminExt with null user or mqAdminExt object.");
            return;
        }
        userMQAdminPoolManager.returnMQAdminExt(user.getName(), mqAdminExt);
    }

    public void onUserLogout(User user) {
        if (user != null) {
            userMQAdminPoolManager.shutdownUserPool(user.getName());
            log.info("User {} logged out, their MQAdminExt pool has been shut down.", user.getName());
        }
    }

}
