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

package com.rocketmq.studio.ops;

import com.rocketmq.studio.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class OpsService {

    private final Set<String> namesrvAddrs = new LinkedHashSet<>(List.of("127.0.0.1:9876"));
    private String currentNamesrv = "127.0.0.1:9876";
    private boolean useVIPChannel = true;
    private boolean useTLS;

    public synchronized OpsHomeVO getHomePage() {
        return OpsHomeVO.builder()
                .namesvrAddrList(new ArrayList<>(namesrvAddrs))
                .currentNamesrv(currentNamesrv)
                .useVIPChannel(useVIPChannel)
                .useTLS(useTLS)
                .build();
    }

    public synchronized void updateNameServer(String namesrvAddr) {
        String normalized = normalizeNameServer(namesrvAddr);
        namesrvAddrs.add(normalized);
        currentNamesrv = normalized;
        log.info("Updated current NameServer address to {}", normalized);
    }

    public synchronized void addNameServer(String namesrvAddr) {
        String normalized = normalizeNameServer(namesrvAddr);
        namesrvAddrs.add(normalized);
        log.info("Added NameServer address {}", normalized);
    }

    public synchronized void updateVipChannel(boolean enabled) {
        useVIPChannel = enabled;
        log.info("Updated VIP channel setting to {}", enabled);
    }

    public synchronized void updateUseTLS(boolean enabled) {
        useTLS = enabled;
        log.info("Updated TLS setting to {}", enabled);
    }

    private String normalizeNameServer(String namesrvAddr) {
        if (namesrvAddr == null || namesrvAddr.trim().isEmpty()) {
            throw new BusinessException(400, "namesrvAddr is required");
        }
        return namesrvAddr.trim();
    }
}
