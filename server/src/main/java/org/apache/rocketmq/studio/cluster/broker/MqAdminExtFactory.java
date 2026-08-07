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

import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.MQAdminExt;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Central lifecycle owner for real {@link DefaultMQAdminExt} connections.
 *
 * <p>This is the single real-network entry point shared by every live cluster/metadata provider.
 * Admin clients are created lazily, started once and cached per NameServer address so subsequent
 * calls reuse the established connection. All cached clients are shut down on context destruction.
 *
 * <p>The {@link RPCHook} parameter is reserved for the ACL / authentication work (AUTH-01); it is
 * currently always {@code null} but is threaded through so credentials can be injected later
 * without changing this contract.
 */
@Slf4j
@Component
public class MqAdminExtFactory {

    /** Default admin RPC timeout in milliseconds. */
    private static final long DEFAULT_TIMEOUT_MILLIS = 5000L;

    private final Map<String, DefaultMQAdminExt> cache = new ConcurrentHashMap<>();
    private final AtomicInteger instanceCounter = new AtomicInteger();
    private volatile boolean closed = false;

    /**
     * Runs an action against a started admin client bound to the given NameServer address.
     *
     * @param namesrvAddr NameServer address list, e.g. {@code host1:9876;host2:9876}
     * @param rpcHook     optional RPC hook for authentication, may be {@code null}
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
        String normalizedNamesrvAddr = normalizeNamesrvAddr(namesrvAddr);
        if (normalizedNamesrvAddr.isEmpty()) {
            throw new BusinessException(400, "NameServer address is required");
        }
        DefaultMQAdminExt admin = cache.computeIfAbsent(normalizedNamesrvAddr,
                addr -> createAndStart(addr, rpcHook));
        try {
            return action.apply(admin);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Admin action failed against namesrv {}: {}", namesrvAddr, ex.getMessage());
            throw new BusinessException(502, "RocketMQ admin call failed: " + rootMessage(ex));
        }
    }

    /**
     * Stops and removes the cached client for an endpoint that is no longer referenced by Studio.
     *
     * <p>Callers must ensure the endpoint is not still used by another configured instance.
     */
    public void release(String namesrvAddr) {
        if (namesrvAddr == null || namesrvAddr.isBlank()) {
            return;
        }
        String normalizedNamesrvAddr = normalizeNamesrvAddr(namesrvAddr);
        if (normalizedNamesrvAddr.isEmpty()) {
            return;
        }
        DefaultMQAdminExt admin = cache.remove(normalizedNamesrvAddr);
        if (admin != null) {
            safeShutdown(admin);
            log.info("Released RocketMQ admin client for namesrv {}", normalizedNamesrvAddr);
        }
    }

    private DefaultMQAdminExt createAndStart(String namesrvAddr, RPCHook rpcHook) {
        DefaultMQAdminExt admin = newAdmin(rpcHook);
        admin.setNamesrvAddr(namesrvAddr);
        admin.setInstanceName(buildInstanceName(namesrvAddr));
        try {
            admin.start();
            log.info("Started RocketMQ admin client for namesrv {}", namesrvAddr);
            return admin;
        } catch (Exception ex) {
            safeShutdown(admin);
            throw new BusinessException(502,
                    "Failed to connect NameServer " + namesrvAddr + ": " + rootMessage(ex));
        }
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

    public static String normalizeNamesrvAddr(String namesrvAddr) {
        return Arrays.stream(namesrvAddr.split("[;,]"))
                .map(String::trim)
                .filter(address -> !address.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.joining(";"));
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
