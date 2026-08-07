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

package org.apache.rocketmq.studio.ops;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class OpsService {

    private static final String OPS_SETTINGS_UNAVAILABLE =
            "Ops settings are not connected to the cluster admin configuration";

    private final Set<String> namesrvAddrs = new LinkedHashSet<>(List.of("127.0.0.1:9876"));
    private String currentNamesrv = "127.0.0.1:9876";
    private boolean useVIPChannel = true;
    private boolean useTLS;

    public synchronized OpsHomeVO getHomePage() {
        // Ops settings are not connected to a cluster admin configuration, so return an explicit
        // unavailable state instead of simulated values that would be mistaken for live data.
        return OpsHomeVO.builder()
                .namesvrAddrList(List.of())
                .currentNamesrv("")
                .useVIPChannel(false)
                .useTLS(false)
                .available(false)
                .build();
    }

    public synchronized void updateNameServer(String namesrvAddr) {
        normalizeNameServer(namesrvAddr);
        throw settingsUnavailable();
    }

    public synchronized void addNameServer(String namesrvAddr) {
        normalizeNameServer(namesrvAddr);
        throw settingsUnavailable();
    }

    public synchronized void deleteNameServer(String namesrvAddr) {
        normalizeNameServer(namesrvAddr);
        throw settingsUnavailable();
    }

    public synchronized void updateVipChannel(boolean enabled) {
        throw settingsUnavailable();
    }

    public synchronized void updateUseTLS(boolean enabled) {
        throw settingsUnavailable();
    }

    private String normalizeNameServer(String namesrvAddr) {
        if (namesrvAddr == null || namesrvAddr.trim().isEmpty()) {
            throw new BusinessException(400, "namesrvAddr is required");
        }
        return namesrvAddr.trim();
    }

    private BusinessException settingsUnavailable() {
        log.warn(OPS_SETTINGS_UNAVAILABLE);
        return new BusinessException(501, OPS_SETTINGS_UNAVAILABLE);
    }
}
