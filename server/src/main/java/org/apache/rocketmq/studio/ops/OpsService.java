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

import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.cluster.broker.OpsDefaultClient;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class OpsService {

    private static final String OPS_SETTINGS_UNAVAILABLE =
            "Ops settings are not connected to the cluster admin configuration";

    private final OpsRuntimeConnection runtimeConnection;
    private final OpsRuntimeProperties runtimeProperties;
    private final OperationAuditService auditService;
    private final OpsDefaultClient defaultClient;

    public OpsService(OpsRuntimeConnection runtimeConnection, OpsRuntimeProperties runtimeProperties,
                      OperationAuditService auditService, OpsDefaultClient defaultClient) {
        this.runtimeConnection = runtimeConnection;
        this.runtimeProperties = runtimeProperties;
        this.auditService = auditService;
        this.defaultClient = defaultClient;
    }

    public synchronized OpsHomeVO getHomePage() {
        if (!runtimeProperties.isEnabled()) {
            return unavailableHomePage(OPS_SETTINGS_UNAVAILABLE);
        }
        try {
            OpsConnectionSettings settings = runtimeConnection.current();
            if (settings.currentNamesrv().isEmpty()) {
                return unavailableHomePage("namesrvAddr is required");
            }
            return OpsHomeVO.builder()
                    .configurationAvailable(true)
                    .namesvrAddrList(settings.addresses())
                    .currentNamesrv(settings.currentNamesrv())
                    .useVIPChannel(settings.useVIPChannel())
                    .useTLS(settings.useTLS())
                    .build();
        } catch (BusinessException exception) {
            return unavailableHomePage(exception.getMessage());
        }
    }

    public synchronized void updateNameServer(String namesrvAddr) {
        ensureRuntimeEnabled();
        ensureAdmin();
        String normalized = normalizeNameServer(namesrvAddr);
        OpsConnectionSettings updated = runtimeConnection.update(settings -> {
            if (!settings.addresses().contains(normalized)) {
                throw new BusinessException(400, "namesrvAddr is not managed: " + normalized);
            }
            return new OpsConnectionSettings(settings.addresses(), normalized,
                    settings.useVIPChannel(), settings.useTLS());
        });
        defaultClient.releaseInactiveManagedDefaults(updated);
        audit("UPDATE_OPS_NAMESERVER", updated.currentNamesrv(),
                "currentNamesrv=" + updated.currentNamesrv());
    }

    public synchronized void addNameServer(String namesrvAddr) {
        ensureRuntimeEnabled();
        ensureAdmin();
        String normalized = normalizeNameServer(namesrvAddr);
        OpsConnectionSettings updated = runtimeConnection.update(settings -> {
            if (settings.addresses().contains(normalized)) {
                throw new BusinessException(400, "namesrvAddr already exists: " + normalized);
            }
            List<String> addresses = new ArrayList<>(settings.addresses());
            addresses.add(normalized);
            return new OpsConnectionSettings(addresses, settings.currentNamesrv(),
                    settings.useVIPChannel(), settings.useTLS());
        });
        defaultClient.releaseInactiveManagedDefaults(updated);
        audit("ADD_OPS_NAMESERVER", normalized, "namesrvAddr=" + normalized
                + ",total=" + updated.addresses().size());
    }

    public synchronized void deleteNameServer(String namesrvAddr) {
        ensureRuntimeEnabled();
        ensureAdmin();
        String normalized = normalizeNameServer(namesrvAddr);
        OpsConnectionSettings updated = runtimeConnection.update(settings -> {
            if (!settings.addresses().contains(normalized)) {
                throw new BusinessException(400, "namesrvAddr is not managed: " + normalized);
            }
            if (settings.addresses().size() <= 1) {
                throw new BusinessException(400, "Cannot delete the last NameServer address");
            }
            if (normalized.equals(settings.currentNamesrv())) {
                throw new BusinessException(400, "Cannot delete the selected NameServer address");
            }
            List<String> addresses = new ArrayList<>(settings.addresses());
            addresses.remove(normalized);
            return new OpsConnectionSettings(addresses, settings.currentNamesrv(),
                    settings.useVIPChannel(), settings.useTLS());
        });
        defaultClient.releaseInactiveManagedDefaults(updated);
        audit("DELETE_OPS_NAMESERVER", normalized, "namesrvAddr=" + normalized
                + ",total=" + updated.addresses().size());
    }

    public synchronized void updateVipChannel(boolean enabled) {
        ensureRuntimeEnabled();
        ensureAdmin();
        OpsConnectionSettings updated = runtimeConnection.update(settings ->
                new OpsConnectionSettings(settings.addresses(), requireNameServer(settings),
                        enabled, settings.useTLS()));
        defaultClient.releaseInactiveManagedDefaults(updated);
        audit("UPDATE_OPS_VIP_CHANNEL", "default", "useVIPChannel=" + updated.useVIPChannel());
    }

    public synchronized void updateUseTLS(boolean enabled) {
        ensureRuntimeEnabled();
        ensureAdmin();
        OpsConnectionSettings updated = runtimeConnection.update(settings ->
                new OpsConnectionSettings(settings.addresses(), requireNameServer(settings),
                        settings.useVIPChannel(), enabled));
        defaultClient.releaseInactiveManagedDefaults(updated);
        audit("UPDATE_OPS_TLS", "default", "useTLS=" + updated.useTLS());
    }

    private OpsHomeVO unavailableHomePage(String reason) {
        return OpsHomeVO.builder()
                .configurationAvailable(false)
                .unavailableReason(reason)
                .namesvrAddrList(List.of())
                .currentNamesrv("")
                .useVIPChannel(false)
                .useTLS(false)
                .build();
    }

    private String normalizeNameServer(String namesrvAddr) {
        if (namesrvAddr == null || namesrvAddr.trim().isEmpty()) {
            throw new BusinessException(400, "namesrvAddr is required");
        }
        try {
            return OpsConnectionSettings.normalizeSingleAddress(namesrvAddr);
        } catch (BusinessException exception) {
            if (exception.getMessage().contains("must not be blank")) {
                throw new BusinessException(400, "namesrvAddr is required");
            }
            throw exception;
        }
    }

    private String requireNameServer(OpsConnectionSettings settings) {
        if (settings.currentNamesrv().isEmpty()) {
            throw new BusinessException(400, "namesrvAddr is required before transport settings can be updated");
        }
        return settings.currentNamesrv();
    }

    private void ensureRuntimeEnabled() {
        if (!runtimeProperties.isEnabled()) {
            throw settingsUnavailable();
        }
    }

    private void ensureAdmin() {
        if (!AuthenticatedUserContext.currentUserIsAdmin()) {
            throw new BusinessException(403, "Ops runtime settings require an authenticated administrator");
        }
    }

    private BusinessException settingsUnavailable() {
        log.warn(OPS_SETTINGS_UNAVAILABLE);
        return new BusinessException(501, OPS_SETTINGS_UNAVAILABLE);
    }

    private void audit(String operation, String resourceName, String detail) {
        try {
            auditService.record(operation, "OPS_CONNECTION", resourceName, null, detail, "SUCCESS", null);
        } catch (RuntimeException exception) {
            log.warn("Failed to record Ops runtime settings audit: {}", exception.getMessage());
        }
    }
}
