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

import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.service.ProxyService;
import org.apache.rocketmq.dashboard.service.client.ProxyAdmin;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ProxyServiceImpl implements ProxyService {
    @Resource
    protected ProxyAdmin proxyAdmin;
    @Resource
    private RMQConfigure configure;

    @Override
    public void addProxyAddrList(String proxyAddr) {
        List<String> proxyAddrs = configure.getProxyAddrs();
        if (proxyAddrs != null && !proxyAddrs.contains(proxyAddr)) {
            proxyAddrs.add(proxyAddr);
        }
        configure.setProxyAddrs(proxyAddrs);
    }

    @Override
    public void updateProxyAddrList(String proxyAddr) {
        configure.setProxyAddr(proxyAddr);
    }

    @Override
    public Map<String, Object> getProxyHomePage() {
        Map<String, Object> homePageInfoMap = Maps.newHashMap();
        homePageInfoMap.put("currentProxyAddr", configure.getProxyAddr());
        homePageInfoMap.put("proxyAddrList", configure.getProxyAddrs());
        return homePageInfoMap;
    }
}
