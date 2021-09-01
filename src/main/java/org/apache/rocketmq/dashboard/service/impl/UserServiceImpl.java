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

import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.exception.ServiceException;
import org.apache.rocketmq.dashboard.model.User;
import org.apache.rocketmq.dashboard.service.UserService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;
import java.io.FileReader;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserServiceImpl implements UserService, InitializingBean {
    @Resource
    private RMQConfigure configure;

    private FileBasedUserInfoStore fileBasedUserInfoStore;

    @Override
    public User queryByName(String name) {
        return fileBasedUserInfoStore.queryByName(name);
    }

    @Override
    public User queryByUsernameAndPassword(String username, String password) {
        return fileBasedUserInfoStore.queryByUsernameAndPassword(username, password);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (configure.isLoginRequired()) {
            fileBasedUserInfoStore = new FileBasedUserInfoStore(configure);
        }
    }

    public static class FileBasedUserInfoStore extends AbstractFileStore {
        private static final String FILE_NAME = "users.properties";

        private static Map<String, User> userMap = new ConcurrentHashMap<>();

        public FileBasedUserInfoStore(RMQConfigure configure) {
            super(configure, FILE_NAME);
        }

        @Override
        public void load(InputStream inputStream) {
            Properties prop = new Properties();
            try {
                if (inputStream == null) {
                    prop.load(new FileReader(filePath));
                } else {
                    prop.load(inputStream);
                }
            } catch (Exception e) {
                log.error("load user.properties failed", e);
                throw new ServiceException(0, String.format("Failed to load loginUserInfo property file: %s", filePath));
            }

            Map<String, User> loadUserMap = new HashMap<>();
            String[] arrs;
            int role;
            for (String key : prop.stringPropertyNames()) {
                String v = prop.getProperty(key);
                if (v == null)
                    continue;
                arrs = v.split(",", 2);
                if (arrs.length == 0) {
                    continue;
                } else if (arrs.length == 1) {
                    role = 0;
                } else {
                    role = Integer.parseInt(arrs[1].trim());
                }

                loadUserMap.put(key, new User(key, arrs[0].trim(), role));
            }

            userMap.clear();
            userMap.putAll(loadUserMap);
        }

        public User queryByName(String name) {
            return userMap.get(name);
        }

        public User queryByUsernameAndPassword(@NotNull String username, @NotNull String password) {
            User user = queryByName(username);
            if (user != null && password.equals(user.getPassword())) {
                return user.cloneOne();
            }
            return null;
        }
    }
}
