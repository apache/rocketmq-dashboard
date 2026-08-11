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

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Throwables;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.model.ConsumerMonitorConfig;
import org.apache.rocketmq.dashboard.service.MonitorService;
import org.apache.rocketmq.dashboard.util.JsonUtil;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class MonitorServiceImpl implements MonitorService {


    @Resource
    private RMQConfigure configure;

    private Map<String, ConsumerMonitorConfig> configMap = new ConcurrentHashMap<>();

    @Override
    public boolean createOrUpdateConsumerMonitor(String name, ConsumerMonitorConfig config) {
        configMap.put(name, config);// todo if write map success but write file fail
        writeToFile(getConsumerMonitorConfigDataPath(), configMap);
        return true;
    }

    @Override
    public Map<String, ConsumerMonitorConfig> queryConsumerMonitorConfig() {
        return configMap;
    }

    @Override
    public ConsumerMonitorConfig queryConsumerMonitorConfigByGroupName(String consumeGroupName) {
        return configMap.get(consumeGroupName);
    }

    @Override
    public boolean deleteConsumerMonitor(String consumeGroupName) {
        configMap.remove(consumeGroupName);
        writeToFile(getConsumerMonitorConfigDataPath(), configMap);
        return true;
    }

    //rocketmq.console.data.path/monitor/consumerMonitorConfig.json
    private String getConsumerMonitorConfigDataPath() {
        return configure.getRocketMqDashboardDataPath() + File.separatorChar + "monitor" + File.separatorChar + "consumerMonitorConfig.json";
    }

    private String getConsumerMonitorConfigDataPathBackUp() {
        return getConsumerMonitorConfigDataPath() + ".bak";
    }

    private void writeToFile(String path, Object data) {
        writeDataJsonToFile(path, JsonUtil.obj2String(data));
    }

    private void writeDataJsonToFile(String path, String dataStr) {
        try {
            MixAll.string2File(dataStr, path);
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    @PostConstruct
    private void loadData() {
        Map<String, ConsumerMonitorConfig> loadedConfig = loadConfig(getConsumerMonitorConfigDataPath());
        if (loadedConfig == null) {
            loadedConfig = loadConfig(getConsumerMonitorConfigDataPathBackUp());
        }
        configMap = loadedConfig == null ? new ConcurrentHashMap<>() : loadedConfig;
    }

    private Map<String, ConsumerMonitorConfig> loadConfig(String path) {
        try {
            String content = MixAll.file2String(path);
            if (content == null) {
                return null;
            }
            Map<String, ConsumerMonitorConfig> result = JsonUtil.string2Obj(content,
                    new TypeReference<ConcurrentHashMap<String, ConsumerMonitorConfig>>() {
                    });
            if (result == null) {
                log.warn("Failed to parse consumer monitor config file: {}", path);
            }
            return result;
        } catch (IOException e) {
            log.warn("Failed to read consumer monitor config file: {}", path, e);
            return null;
        }
    }
}
