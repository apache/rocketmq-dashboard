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
package org.apache.rocketmq.studio.cluster.broker;

import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.rocketmq.RocketMQProperties;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.MQAdminExt;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central lifecycle owner for real {@link DefaultMQAdminExt} connections.
 *
 * <p>This is the single real-network entry point shared by every live cluster/metadata provider.
 * Admin clients are created lazily, started once and cached per NameServer address so subsequent
 * calls reuse the established connection. All cached clients are shut down on context destruction.
 *
 * <p>AUTH-01: when the caller passes no explicit {@link RPCHook} and ACL credentials are
 * configured ({@code studio.rocketmq.acl.access-key/secret-key}), the factory attaches an
 * {@link AclClientRPCHook} automatically, so every admin client authenticates against
 * ACL 2.0 enabled clusters.
 */
@Slf4j
@Component
public class MqAdminExtFactory {

    /** Default admin RPC timeout in milliseconds. */
    private static final long DEFAULT_TIMEOUT_MILLIS = 5000L;

    private final Map<String, DefaultMQAdminExt> cache = new ConcurrentHashMap<>();
    private final AtomicInteger instanceCounter = new AtomicInteger();
    private final RocketMQProperties properties;
    private volatile boolean closed = false;

    public MqAdminExtFactory(RocketMQProperties properties) {
        this.properties = properties;
    }

    /**
     * Runs an action against a started admin client bound to the given NameServer address.
     *
     * @param namesrvAddr NameServer address list, e.g. {@code host1:9876;host2:9876}
     * @param rpcHook     optional RPC hook for authentication; when {@code null} the configured
     *                    ACL credentials are applied automatically (AUTH-01)
     * @param action      the admin interaction to execute
     * @param <T>         result type
     * @return the action result
     * @throws BusinessException if the connection cannot be established or the action fails
     */
    public <T> T execute(String namesrvAddr, RPCHook rpcHook, AdminAction<T> action) {
        if (namesrvAddr == null || namesrvAddr.isBlank()) {
            throw new BusinessException(400, "NameServer address is required");
        }
        if (closed) {
            throw new BusinessException(503, "Admin factory is shutting down");
        }
        RPCHook effectiveHook = rpcHook != null ? rpcHook : buildConfiguredAclHook();
        DefaultMQAdminExt admin = cache.computeIfAbsent(namesrvAddr.trim(),
                addr -> createAndStart(addr, effectiveHook));
        try {
            return action.apply(admin);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Admin action failed against namesrv {}: {}", namesrvAddr, ex.getMessage());
            throw new BusinessException(502, "RocketMQ admin call failed: " + rootMessage(ex));
        }
    }

    private DefaultMQAdminExt createAndStart(String namesrvAddr, RPCHook rpcHook) {
        DefaultMQAdminExt admin = newAdmin(rpcHook);
        admin.setNamesrvAddr(namesrvAddr);
        admin.setInstanceName(buildInstanceName(namesrvAddr));
        try {
            admin.start();
            log.info("Started RocketMQ admin client for namesrv {}{}", namesrvAddr,
                    rpcHook != null ? " (ACL hook attached)" : "");
            return admin;
        } catch (Exception ex) {
            safeShutdown(admin);
            throw new BusinessException(502,
                    "Failed to connect NameServer " + namesrvAddr + ": " + rootMessage(ex));
        }
    }

    /**
     * Builds the ACL hook from {@code studio.rocketmq.acl.*} credentials, or {@code null}
     * when credentials are not configured (open clusters).
     */
    RPCHook buildConfiguredAclHook() {
        RocketMQProperties.Acl acl = properties == null ? null : properties.getAcl();
        if (acl == null || !acl.isEnabled()) {
            return null;
        }
        return new AclClientRPCHook(new SessionCredentials(acl.getAccessKey(), acl.getSecretKey()));
    }

    /**
     * Creates a new (not-yet-started) admin client. Extracted so tests can inject a stub without a
     * live cluster.
     */
    protected DefaultMQAdminExt newAdmin(RPCHook rpcHook) {
        return new DefaultMQAdminExt(rpcHook, DEFAULT_TIMEOUT_MILLIS);
    }

    private String buildInstanceName(String namesrvAddr) {
        return "rmq-studio-" + Integer.toHexString(namesrvAddr.hashCode())
                + "-" + instanceCounter.incrementAndGet();
    }

    private void safeShutdown(MQAdminExt admin) {
        try {
            admin.shutdown();
        } catch (Exception ex) {
            log.debug("Ignoring admin shutdown error: {}", ex.getMessage());
        }
    }

    private String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName() : message;
    }

    @PreDestroy
    public void shutdown() {
        closed = true;
        cache.values().forEach(this::safeShutdown);
        cache.clear();
        log.info("Shut down all RocketMQ admin clients");
    }

    /** Callback executed against a live admin client. */
    @FunctionalInterface
    public interface AdminAction<T> {
        T apply(MQAdminExt admin) throws Exception;
    }
}
