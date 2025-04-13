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
package org.apache.rocketmq.dashboard.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class ClusterInfoService {

    @Resource
    private MQAdminExt mqAdminExt;

    @Value("${rocketmq.cluster.cache.expire:60000}")
    private long cacheExpireMs;


    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<ClusterInfo> cachedRef = new AtomicReference<>();


    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(this::refresh,
                0, cacheExpireMs / 2, TimeUnit.MILLISECONDS);
    }

    public ClusterInfo get() {
        ClusterInfo info = cachedRef.get();
        return info != null ? info : refresh();
    }

    public synchronized ClusterInfo refresh() {
        try {
            ClusterInfo fresh = mqAdminExt.examineBrokerClusterInfo();
            cachedRef.set(fresh);
            return fresh;
        } catch (Exception e) {
            log.warn("Refresh cluster info failed", e);
            ClusterInfo old = cachedRef.get();
            if (old != null) {
                return old;
            }
            throw new IllegalStateException("No cluster info available", e);
        }
    }
}
