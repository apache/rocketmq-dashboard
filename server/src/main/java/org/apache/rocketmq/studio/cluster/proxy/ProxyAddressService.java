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

package org.apache.rocketmq.studio.cluster.proxy;

import org.apache.commons.validator.routines.InetAddressValidator;
import org.apache.rocketmq.studio.audit.OperationAuditConstants.Operation;
import org.apache.rocketmq.studio.audit.OperationAuditConstants.ResourceType;
import org.apache.rocketmq.studio.audit.OperationAuditConstants.Result;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.broker.ClusterService;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.util.NoRedirectClientHttpRequestFactory;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class ProxyAddressService {

    private static final Pattern PROXY_ADDR_PATTERN =
            Pattern.compile("^(\\[[0-9a-fA-F:.]+]|[A-Za-z0-9._-]+):(\\d{1,5})$");
    private static final InetAddressValidator INET_ADDRESS_VALIDATOR = InetAddressValidator.getInstance();
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    private static final String RELOAD_PATH = "/admin/reloadConfig";

    /** Default connect timeout for a single topology probe, in milliseconds. */
    private static final int HEALTH_PROBE_TIMEOUT_MILLIS = 2_000;

    /**
     * Overall time budget for one topology build. Serial probing would cost up to
     * {@code 2 x HEALTH_PROBE_TIMEOUT_MILLIS} per DOWN node, so with many unreachable
     * nodes the endpoint could exceed the frontend's request timeout. Probing runs in
     * parallel and, once the budget elapses, unfinished probes are reported as
     * unreachable (DOWN/PARTIAL) instead of failing the whole endpoint.
     */
    private static final long TOPOLOGY_TOTAL_TIMEOUT_MILLIS = 10_000L;

    /** Bounded pool for the I/O-bound TCP probes; probes queue beyond this share the budget. */
    private static final int PROBE_EXECUTOR_THREADS = 8;

    private final ClusterService clusterService;
    private final Set<String> proxyAddrs = new LinkedHashSet<>(List.of("127.0.0.1:8081"));
    private String currentProxyAddr = "127.0.0.1:8081";
    private final RestTemplate restTemplate;
    private final ProxyHealthProbe healthProbe;
    private final ExecutorService probeExecutor;
    private final long topologyTotalTimeoutMillis;
    private final OperationAuditService operationAuditService;

    @Autowired
    public ProxyAddressService(ClusterService clusterService, ProxyHealthProbe healthProbe,
                               OperationAuditService operationAuditService) {
        this(clusterService, healthProbe, operationAuditService, newRestTemplate(), defaultProbeExecutor(),
                TOPOLOGY_TOTAL_TIMEOUT_MILLIS);
    }

    ProxyAddressService(ClusterService clusterService, ProxyHealthProbe healthProbe) {
        this(clusterService, healthProbe, null, newRestTemplate(), defaultProbeExecutor(),
                TOPOLOGY_TOTAL_TIMEOUT_MILLIS);
    }

    ProxyAddressService(ClusterService clusterService, ProxyHealthProbe healthProbe, RestTemplate restTemplate) {
        this(clusterService, healthProbe, null, restTemplate, defaultProbeExecutor(), TOPOLOGY_TOTAL_TIMEOUT_MILLIS);
    }

    ProxyAddressService(ClusterService clusterService, ProxyHealthProbe healthProbe, RestTemplate restTemplate,
                        OperationAuditService operationAuditService) {
        this(clusterService, healthProbe, operationAuditService, restTemplate, defaultProbeExecutor(),
                TOPOLOGY_TOTAL_TIMEOUT_MILLIS);
    }

    ProxyAddressService(ClusterService clusterService, ProxyHealthProbe healthProbe,
                        ExecutorService probeExecutor, long topologyTotalTimeoutMillis) {
        this(clusterService, healthProbe, null, newRestTemplate(), probeExecutor, topologyTotalTimeoutMillis);
    }

    ProxyAddressService(ClusterService clusterService, ProxyHealthProbe healthProbe,
                        OperationAuditService operationAuditService, RestTemplate restTemplate,
                        ExecutorService probeExecutor, long topologyTotalTimeoutMillis) {
        this.clusterService = clusterService;
        this.healthProbe = healthProbe;
        this.restTemplate = restTemplate;
        this.probeExecutor = probeExecutor;
        this.topologyTotalTimeoutMillis = topologyTotalTimeoutMillis;
        this.operationAuditService = operationAuditService;
    }

    private static RestTemplate newRestTemplate() {
        NoRedirectClientHttpRequestFactory factory = new NoRedirectClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return new RestTemplate(factory);
    }

    private static ExecutorService defaultProbeExecutor() {
        AtomicInteger threadIndex = new AtomicInteger();
        return Executors.newFixedThreadPool(PROBE_EXECUTOR_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "proxy-health-probe-" + threadIndex.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    public void shutdownProbeExecutor() {
        probeExecutor.shutdownNow();
    }

    public synchronized ProxyHomeVO getHomePage() {
        return ProxyHomeVO.builder()
                .proxyAddrList(new ArrayList<>(proxyAddrs))
                .currentProxyAddr(currentProxyAddr)
                .build();
    }

    /**
     * Builds the proxy topology/health view: every registered proxy address is probed over
     * TCP on its gRPC port (and the derived remoting port) so the console can show live
     * UP/PARTIAL/DOWN status instead of an address list with no runtime signal.
     *
     * <p>All probes run in parallel on a bounded executor and the whole view is capped by
     * {@link #topologyTotalTimeoutMillis}: probes that do not finish in time are reported as
     * unreachable, so a few DOWN nodes can never stall the endpoint past its budget.
     */
    public List<ProxyTopologyVO> buildTopology() {
        List<String> addrs;
        synchronized (this) {
            addrs = new ArrayList<>(proxyAddrs);
        }
        List<ProbeTask> tasks = new ArrayList<>();
        for (String addr : addrs) {
            Matcher matcher = PROXY_ADDR_PATTERN.matcher(addr);
            if (!matcher.matches()) {
                log.warn("Skipping malformed proxy address in topology: {}", addr);
                continue;
            }
            String host = stripIpv6Brackets(matcher.group(1));
            int grpcPort = Integer.parseInt(matcher.group(2));
            Integer remotingPort = deriveRemotingPort(grpcPort);
            tasks.add(new ProbeTask(addr, grpcPort, remotingPort,
                    probeAsync(host, grpcPort),
                    remotingPort != null ? probeAsync(host, remotingPort) : null));
        }
        awaitProbes(tasks);
        return tasks.stream().map(this::toTopologyVO).toList();
    }

    private ProxyTopologyVO toTopologyVO(ProbeTask task) {
        ProxyHealthProbe.ProbeResult grpc = awaitProbe(task.grpcProbe());
        ProxyHealthProbe.ProbeResult remoting = task.remotingProbe() != null
                ? awaitProbe(task.remotingProbe())
                : ProxyHealthProbe.ProbeResult.unreachable();
        boolean grpcReachable = grpc.reachable();
        boolean remotingReachable = task.remotingPort() != null && remoting.reachable();
        String status = grpcReachable ? "UP"
                : (remotingReachable ? "PARTIAL" : "DOWN");
        return ProxyTopologyVO.builder()
                .proxyAddr(task.proxyAddr())
                .status(status)
                .grpcPort(task.grpcPort())
                .remotingPort(task.remotingPort())
                .grpcReachable(grpcReachable)
                .remotingReachable(remotingReachable)
                .latencyMs(grpcReachable ? grpc.latencyMs() : -1L)
                .build();
    }

    private CompletableFuture<ProxyHealthProbe.ProbeResult> probeAsync(String host, int port) {
        try {
            return CompletableFuture.supplyAsync(
                    () -> healthProbe.probe(host, port, HEALTH_PROBE_TIMEOUT_MILLIS), probeExecutor);
        } catch (RejectedExecutionException ex) {
            return CompletableFuture.completedFuture(ProxyHealthProbe.ProbeResult.unreachable());
        }
    }

    private void awaitProbes(List<ProbeTask> tasks) {
        if (tasks.isEmpty()) {
            return;
        }
        CompletableFuture<?>[] futures = tasks.stream()
                .flatMap(task -> task.remotingProbe() == null
                        ? Stream.of(task.grpcProbe())
                        : Stream.of(task.grpcProbe(), task.remotingProbe()))
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(futures).get(topologyTotalTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            log.warn("Proxy topology probing exceeded the {} ms budget; unfinished probes are"
                    + " reported as unreachable", topologyTotalTimeoutMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Proxy topology probing was interrupted");
        } catch (ExecutionException ex) {
            log.warn("Proxy topology probe failed: {}", ex.getMessage());
        }
    }

    /**
     * Reads a finished probe outcome; a probe that is still running (budget exceeded), failed,
     * or was rejected degrades to unreachable instead of propagating an error.
     */
    private ProxyHealthProbe.ProbeResult awaitProbe(CompletableFuture<ProxyHealthProbe.ProbeResult> future) {
        if (future.isDone() && !future.isCompletedExceptionally()) {
            return future.join();
        }
        future.cancel(true);
        return ProxyHealthProbe.ProbeResult.unreachable();
    }

    /**
     * {@code InetSocketAddress} treats a bracketed IPv6 literal as a hostname, so probing
     * would always fail with an unknown-host error and report healthy IPv6 proxies as DOWN.
     */
    private static String stripIpv6Brackets(String host) {
        return host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;
    }

    /** In-flight probe pair for one registered proxy address. */
    private record ProbeTask(String proxyAddr, int grpcPort, Integer remotingPort,
                             CompletableFuture<ProxyHealthProbe.ProbeResult> grpcProbe,
                             CompletableFuture<ProxyHealthProbe.ProbeResult> remotingProbe) {
    }

    /**
     * Derives the counterpart port using the RocketMQ 5.0 default layout (remoting
     * {@code 8080} / gRPC {@code 8081}). Non-standard ports yield {@code null} because the
     * pairing cannot be assumed for custom port mappings.
     */
    private Integer deriveRemotingPort(int grpcPort) {
        if (grpcPort == 8081) {
            return 8080;
        }
        return grpcPort == 8080 ? 8081 : null;
    }

    public synchronized void addProxyAddr(String newProxyAddr) {
        String normalized = normalizeProxyAddr(newProxyAddr, "newProxyAddr");
        boolean added = proxyAddrs.add(normalized);
        if (currentProxyAddr == null || currentProxyAddr.isBlank()) {
            currentProxyAddr = normalized;
        }
        if (added) {
            recordAudit(Operation.ADD_PROXY_ADDRESS, ResourceType.PROXY, normalized, null);
        }
        log.info("Added Proxy address {}", normalized);
    }

    public synchronized void removeProxyAddr(String proxyAddr) {
        String normalized = normalizeProxyAddr(proxyAddr, "proxyAddr");
        if (!proxyAddrs.remove(normalized)) {
            recordAudit(Operation.REMOVE_PROXY_ADDRESS, ResourceType.PROXY, normalized, null,
                    Result.FAILED, "Proxy address not found");
            throw new BusinessException(404, "Proxy address not found: " + normalized);
        }
        if (normalized.equals(currentProxyAddr)) {
            currentProxyAddr = proxyAddrs.stream().findFirst().orElse("");
        }
        recordAudit(Operation.REMOVE_PROXY_ADDRESS, ResourceType.PROXY, normalized, null);
        log.info("Removed Proxy address {}", normalized);
    }

    /**
     * Trigger a configuration hot-reload for the proxy at the given address.
     * POSTs to {@code http://<addr>/admin/reloadConfig}. Throws {@link BusinessException}
     * on transport or protocol failure so the caller receives a structured error response.
     */
    public void reloadConfig(String clusterId, String addr) {
        String normalizedClusterId = normalizeClusterId(clusterId);
        String normalized = normalizeProxyAddr(addr, "addr");
        clusterService.requireProxy(normalizedClusterId, normalized);
        String url = "http://" + normalized + RELOAD_PATH;
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
            HttpStatusCode status = response.getStatusCode();
            if (!status.is2xxSuccessful()) {
                throw new BusinessException(502, "Proxy returned " + status);
            }
            recordAudit(Operation.RELOAD_PROXY_CONFIG, ResourceType.PROXY, normalized, normalizedClusterId);
            log.info("Proxy {} accepted config reload", normalized);
        } catch (HttpStatusCodeException ex) {
            recordAudit(Operation.RELOAD_PROXY_CONFIG, ResourceType.PROXY, normalized, normalizedClusterId,
                    Result.FAILED, "Proxy returned " + ex.getStatusCode());
            throw new BusinessException(502, "Proxy returned " + ex.getStatusCode());
        } catch (ResourceAccessException ex) {
            log.warn("Unable to reach proxy {} for config reload: {}", normalized, ex.getMessage());
            recordAudit(Operation.RELOAD_PROXY_CONFIG, ResourceType.PROXY, normalized, normalizedClusterId,
                    Result.FAILED, "Unable to reach proxy");
            throw new BusinessException(502, "Unable to reach proxy: " + ex.getMessage());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Proxy config reload via {} failed: {}", url, ex.getMessage());
            recordAudit(Operation.RELOAD_PROXY_CONFIG, ResourceType.PROXY, normalized, normalizedClusterId,
                    Result.FAILED, "Config reload failed");
            throw new BusinessException(500, "Config reload failed: " + ex.getMessage());
        }
    }

    private String normalizeClusterId(String clusterId) {
        if (clusterId == null || clusterId.trim().isEmpty()) {
            throw new BusinessException(400, "clusterId is required");
        }
        return clusterId.trim();
    }

    private String normalizeProxyAddr(String proxyAddr, String fieldName) {
        if (proxyAddr == null || proxyAddr.trim().isEmpty()) {
            throw new BusinessException(400, fieldName + " is required");
        }
        String normalized = proxyAddr.trim();
        Matcher matcher = PROXY_ADDR_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            throw new BusinessException(400, fieldName + " must be in host:port or [ipv6]:port format");
        }
        String host = matcher.group(1);
        if (host.startsWith("[")
                && !INET_ADDRESS_VALIDATOR.isValidInet6Address(host.substring(1, host.length() - 1))) {
            throw new BusinessException(400, fieldName + " contains a malformed IPv6 address");
        }
        int port = Integer.parseInt(matcher.group(2));
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new BusinessException(400, fieldName + " port must be between 1 and 65535");
        }
        return normalized;
    }

    private void recordAudit(String operation, String resourceType, String resourceName, String clusterId) {
        recordAudit(operation, resourceType, resourceName, clusterId, Result.SUCCESS, null);
    }

    private void recordAudit(String operation, String resourceType, String resourceName, String clusterId,
                             String result, String errorMessage) {
        if (operationAuditService == null) {
            return;
        }
        try {
            operationAuditService.record(operation, resourceType, resourceName, clusterId, null,
                    result, errorMessage);
        } catch (Exception auditFailure) {
            log.warn("Failed to record audit operation={} resource={}: {}", operation, resourceName,
                    auditFailure.getMessage());
        }
    }
}
