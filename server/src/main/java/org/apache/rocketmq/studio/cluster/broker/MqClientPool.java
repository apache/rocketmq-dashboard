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

import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.studio.common.exception.BusinessException;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Long-lived pool of {@link DefaultMQPullConsumer} and {@link DefaultMQProducer} clients, keyed by
 * normalized NameServer address plus a non-secret authentication identity. Mirrors the lifecycle
 * rules of {@link MqAdminExtFactory}: clients are created lazily, reused across requests, and shut
 * down only on application shutdown or explicit endpoint release. Per-request client creation is
 * forbidden because each {@code start()} registers with NameServer/brokers and tears down
 * connections and threads again on {@code shutdown()}.
 */
@Slf4j
@Component
public class MqClientPool {

    private static final String PULL_CONSUMER_GROUP = "studio-pool-pull-consumer";
    private static final String PRODUCER_GROUP = "studio-pool-producer";
    private static final long PRODUCER_SEND_TIMEOUT_MILLIS = 5000L;

    private enum Kind { PULL_CONSUMER, PRODUCER }

    private record ClientKey(String namesrvAddr, String authenticationIdentity, Kind kind) {
    }

    private final Map<ClientKey, Object> cache = new ConcurrentHashMap<>();
    private final AtomicInteger instanceCounter = new AtomicInteger();
    private volatile boolean closed = false;

    @FunctionalInterface
    public interface ClientAction<C, T> {
        T apply(C client) throws Exception;
    }

    public <T> T withPullConsumer(String namesrvAddr, RPCHook rpcHook, String authenticationIdentity,
                                  ClientAction<DefaultMQPullConsumer, T> action) {
        return execute(new ClientKey(normalize(namesrvAddr), identity(authenticationIdentity), Kind.PULL_CONSUMER),
                rpcHook, this::createPullConsumer, action);
    }

    public <T> T withProducer(String namesrvAddr, RPCHook rpcHook, String authenticationIdentity,
                              ClientAction<DefaultMQProducer, T> action) {
        return execute(new ClientKey(normalize(namesrvAddr), identity(authenticationIdentity), Kind.PRODUCER),
                rpcHook, this::createProducer, action);
    }

    private <C, T> T execute(ClientKey cacheKey, RPCHook rpcHook,
                             ClientCreator<C> creator, ClientAction<C, T> action) {
        if (cacheKey.namesrvAddr().isEmpty()) {
            throw new BusinessException(400, "NameServer address is required");
        }
        if (closed) {
            throw new BusinessException(503, "RocketMQ client pool is shutting down");
        }
        @SuppressWarnings("unchecked")
        C client = (C) cache.computeIfAbsent(cacheKey, key -> {
            // Re-check under the cache lock so a request that passed the initial closed check
            // cannot create a fresh connection while the pool is shutting down.
            if (closed) {
                throw new BusinessException(503, "RocketMQ client pool is shutting down");
            }
            return creator.create(key.namesrvAddr(), rpcHook);
        });
        try {
            return action.apply(client);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("RocketMQ client action failed against namesrv {}: {}", cacheKey.namesrvAddr(), ex.getMessage());
            throw new BusinessException(502, "RocketMQ client call failed: " + rootMessage(ex));
        }
    }

    /** Stops and removes pooled clients for an endpoint that is no longer referenced by Studio. */
    public void release(String namesrvAddr) {
        String normalized = normalize(namesrvAddr);
        if (normalized.isEmpty()) {
            return;
        }
        cache.entrySet().removeIf(entry -> {
            if (!entry.getKey().namesrvAddr().equals(normalized)) {
                return false;
            }
            safeShutdown(entry.getValue());
            return true;
        });
        log.info("Released pooled RocketMQ clients for namesrv {}", normalized);
    }

    /** Stops one credential-scoped client without interrupting other identities on the endpoint. */
    public void release(String namesrvAddr, String authenticationIdentity) {
        String normalized = normalize(namesrvAddr);
        if (normalized.isEmpty()) {
            return;
        }
        for (Kind kind : Kind.values()) {
            ClientKey key = new ClientKey(normalized, identity(authenticationIdentity), kind);
            Object client = cache.remove(key);
            if (client != null) {
                safeShutdown(client);
            }
        }
        log.info("Released pooled RocketMQ clients for namesrv {} and identity {}", normalized,
                identity(authenticationIdentity));
    }

    @FunctionalInterface
    private interface ClientCreator<C> {
        C create(String namesrvAddr, RPCHook rpcHook);
    }

    private DefaultMQPullConsumer createPullConsumer(String namesrvAddr, RPCHook rpcHook) {
        DefaultMQPullConsumer consumer = newPullConsumer(rpcHook);
        consumer.setNamesrvAddr(namesrvAddr);
        consumer.setInstanceName(buildInstanceName(namesrvAddr));
        try {
            consumer.start();
            log.info("Started pooled RocketMQ pull consumer for namesrv {}", namesrvAddr);
            return consumer;
        } catch (Exception ex) {
            safeShutdown(consumer);
            throw new BusinessException(502,
                    "Failed to connect NameServer " + namesrvAddr + ": " + rootMessage(ex));
        }
    }

    private DefaultMQProducer createProducer(String namesrvAddr, RPCHook rpcHook) {
        DefaultMQProducer producer = newProducer(rpcHook);
        producer.setNamesrvAddr(namesrvAddr);
        producer.setInstanceName(buildInstanceName(namesrvAddr));
        producer.setSendMsgTimeout((int) PRODUCER_SEND_TIMEOUT_MILLIS);
        producer.setRetryTimesWhenSendFailed(2);
        // Clusters without a trace topic drop trace dispatch asynchronously, so enabling
        // it unconditionally lets Studio-sent messages produce traces wherever trace is on.
        producer.setEnableTrace(true);
        try {
            producer.start();
            log.info("Started pooled RocketMQ producer for namesrv {}", namesrvAddr);
            return producer;
        } catch (Exception ex) {
            safeShutdown(producer);
            throw new BusinessException(502,
                    "Failed to connect NameServer " + namesrvAddr + ": " + rootMessage(ex));
        }
    }

    private String buildInstanceName(String namesrvAddr) {
        return "rmq-studio-pool-" + Integer.toHexString(namesrvAddr.hashCode())
                + "-" + instanceCounter.incrementAndGet();
    }

    /** Creates a new (not-yet-started) pull consumer; extractable so tests can inject a stub. */
    protected DefaultMQPullConsumer newPullConsumer(RPCHook rpcHook) {
        return new DefaultMQPullConsumer(PULL_CONSUMER_GROUP, rpcHook);
    }

    /** Creates a new (not-yet-started) producer; extractable so tests can inject a stub. */
    protected DefaultMQProducer newProducer(RPCHook rpcHook) {
        return new DefaultMQProducer(PRODUCER_GROUP, rpcHook);
    }

    private static String normalize(String namesrvAddr) {
        if (namesrvAddr == null || namesrvAddr.isBlank()) {
            return "";
        }
        return MqAdminExtFactory.normalizeNamesrvAddr(namesrvAddr);
    }

    private static String identity(String authenticationIdentity) {
        return authenticationIdentity == null || authenticationIdentity.isBlank()
                ? "anonymous" : authenticationIdentity.trim();
    }

    private void safeShutdown(Object client) {
        try {
            if (client instanceof DefaultMQPullConsumer pullConsumer) {
                pullConsumer.shutdown();
            } else if (client instanceof DefaultMQProducer producer) {
                producer.shutdown();
            }
        } catch (Exception ex) {
            log.debug("Ignoring client shutdown error: {}", ex.getMessage());
        }
    }

    private String rootMessage(Throwable ex) {
        Throwable cause = ex;
        // An identity set bounds the walk: a direct self-cycle is the only case the old
        // cause.getCause() != cause check caught, a two-exception cycle still loops forever.
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (seen.add(cause) && cause.getCause() != null) {
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
        log.info("Shut down all pooled RocketMQ clients");
    }
}
