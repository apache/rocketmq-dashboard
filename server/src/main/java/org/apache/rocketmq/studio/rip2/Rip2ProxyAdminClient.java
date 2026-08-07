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
package org.apache.rocketmq.studio.rip2;

import apache.rocketmq.v2.ClientInstance;
import apache.rocketmq.v2.Code;
import apache.rocketmq.v2.ListClientsRequest;
import apache.rocketmq.v2.ListClientsResponse;
import apache.rocketmq.v2.ProxyAdminServiceGrpc;
import apache.rocketmq.v2.ProxyScope;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.cluster.client.ClientConnectionVO;
import org.apache.rocketmq.studio.common.domain.enums.ClientLanguage;
import org.apache.rocketmq.studio.common.domain.enums.ClientType;
import org.apache.rocketmq.studio.common.domain.enums.Protocol;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * RIP-2 integration client (CLIENT-01 end state).
 *
 * <p>gRPC clients attached to a 5.0 proxy are invisible to broker-side admin commands
 * (they only exist in the proxy's own channel manager). This client calls the proxy's
 * dedicated {@code ProxyAdminService} (rocketmq-proto 2.3.0, generated from the
 * rocketmq-apis {@code admin.proto} contract) so the dashboard can see them.
 *
 * <p>Queries use {@code PROXY_SCOPE_ALL_PROXIES}: the serving proxy fans out to its
 * configured peers and returns the deduplicated cluster-wide view, so a single admin
 * endpoint yields every gRPC client in the cluster (each result carries its origin
 * {@code proxy_endpoint} for traceability).
 *
 * <p>On ACL-enabled clusters every request is signed with the ACL 2.0 metadata scheme
 * ({@code MQv2-HMAC-SHA1 Credential=<user>/<dateTime>,Signature=<hex>}), identical to
 * the data plane; credentials come from {@code studio.rip2.username/password} and the
 * account only needs read-only admin grants (e.g. {@code cluster:proxy.admin.client}
 * Get,List) — least privilege.
 */
@Slf4j
@Component
public class Rip2ProxyAdminClient {

    private static final long DEADLINE_SECONDS = 15L;
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> DATE_TIME =
            Metadata.Key.of("x-mq-date-time", Metadata.ASCII_STRING_MARSHALLER);

    private final String username;
    private final String password;
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    public Rip2ProxyAdminClient(@Value("${studio.rip2.username:}") String username,
                                @Value("${studio.rip2.password:}") String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * Lists gRPC clients visible to the proxy cluster.
     *
     * @param adminEndpoint proxy admin gRPC endpoint (host:port of the ProxyAdminService)
     * @param type          optional filter: Producer / Consumer (case-insensitive), null for all
     */
    public List<ClientConnectionVO> listClients(String adminEndpoint, String type) {
        if (!StringUtils.hasText(adminEndpoint)) {
            throw new BusinessException(400, "PROXY instance endpoint (proxy admin address) is required");
        }
        ClientType typeFilter = parseType(type);
        try {
            ProxyAdminServiceGrpc.ProxyAdminServiceBlockingStub stub =
                    ProxyAdminServiceGrpc.newBlockingStub(channel(adminEndpoint.trim()))
                            .withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS);
            if (StringUtils.hasText(username)) {
                stub = stub.withInterceptors(signingInterceptor(username, password));
            }
            ListClientsResponse response = stub.listClients(ListClientsRequest.newBuilder()
                    .setScope(ProxyScope.PROXY_SCOPE_ALL_PROXIES)
                    .build());
            if (response.getStatus().getCode() != Code.OK) {
                throw new BusinessException(502, "ProxyAdminService.ListClients returned "
                        + response.getStatus().getCode() + ": " + response.getStatus().getMessage());
            }
            List<ClientConnectionVO> result = new ArrayList<>();
            for (ClientInstance instance : response.getClientsList()) {
                ClientConnectionVO vo = toConnectionVO(instance);
                if (typeFilter == null || vo.getType() == typeFilter) {
                    result.add(vo);
                }
            }
            log.info("RIP-2 ListClients via {} returned {} clients (scope=ALL_PROXIES)",
                    adminEndpoint, result.size());
            return result;
        } catch (BusinessException ex) {
            throw ex;
        } catch (io.grpc.StatusRuntimeException ex) {
            throw new BusinessException(mapGrpcStatus(ex), "RIP-2 ProxyAdminService call failed: "
                    + ex.getStatus().getCode() + " " + ex.getStatus().getDescription());
        } catch (Exception ex) {
            throw new BusinessException(502, "RIP-2 ProxyAdminService call failed: " + ex.getMessage());
        }
    }

    private ManagedChannel channel(String endpoint) {
        return channels.computeIfAbsent(endpoint, target ->
                NettyChannelBuilder.forTarget(target).usePlaintext().build());
    }

    private ClientConnectionVO toConnectionVO(ClientInstance instance) {
        ClientConnectionVO vo = new ClientConnectionVO();
        vo.setClientId(instance.getClientId());
        vo.setType(isProducer(instance) ? ClientType.Producer : ClientType.Consumer);
        vo.setProtocol(Protocol.gRPC);
        vo.setAddress(instance.getAccessPoint());
        vo.setVersion(instance.getClientVersion());
        vo.setLanguage(mapLanguage(instance.getLanguage().name()));
        vo.setPartial(false);
        // D3 traceability: which proxy node the client is attached to.
        vo.setClusterName(instance.getProxyEndpoint());
        if (!instance.getGroupsList().isEmpty()) {
            vo.setGroupOrTopic(String.join(",", instance.getGroupsList()));
        } else if (!instance.getTopicsList().isEmpty()) {
            vo.setGroupOrTopic(String.join(",", instance.getTopicsList()));
        }
        if (instance.hasConnectTime() && instance.getConnectTime().getSeconds() > 0) {
            vo.setConnectedAt(LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(instance.getConnectTime().getSeconds()), ZoneId.systemDefault()));
        }
        return vo;
    }

    private boolean isProducer(ClientInstance instance) {
        return instance.getRole().name().contains("PRODUCER");
    }

    private ClientType parseType(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        try {
            return ClientType.valueOf(type.trim().substring(0, 1).toUpperCase(Locale.ROOT)
                    + type.trim().substring(1).toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private ClientLanguage mapLanguage(String language) {
        if (!StringUtils.hasText(language) || "LANGUAGE_UNSPECIFIED".equals(language)) {
            return null;
        }
        return switch (language) {
            case "JAVA" -> ClientLanguage.Java;
            case "GOLANG" -> ClientLanguage.Go;
            case "PYTHON" -> ClientLanguage.Python;
            case "RUST" -> ClientLanguage.Rust;
            case "CPP" -> ClientLanguage.Cpp;
            case "DOT_NET", "CSHARP" -> ClientLanguage.CSharp;
            case "NODE_JS" -> ClientLanguage.NodeJS;
            case "PHP" -> ClientLanguage.PHP;
            default -> null;
        };
    }

    private int mapGrpcStatus(io.grpc.StatusRuntimeException ex) {
        return switch (ex.getStatus().getCode()) {
            case UNAUTHENTICATED -> 401;
            case PERMISSION_DENIED -> 403;
            case NOT_FOUND -> 404;
            case INVALID_ARGUMENT -> 400;
            default -> 502;
        };
    }

    /**
     * ACL 2.0 request signing — mirrors the proxy/data-plane scheme: the receiver decodes the
     * hex signature back to the raw HMAC-SHA1 bytes, so transmit hex(raw) keyed by the password
     * over the x-mq-date-time bytes.
     */
    private ClientInterceptor signingInterceptor(String user, String pass) {
        return new ClientInterceptor() {
            @Override
            public <R, W> ClientCall<R, W> interceptCall(MethodDescriptor<R, W> method,
                    CallOptions callOptions, Channel next) {
                return new ForwardingClientCall.SimpleForwardingClientCall<R, W>(
                        next.newCall(method, callOptions)) {
                    @Override
                    public void start(Listener<W> responseListener, Metadata headers) {
                        String dateTime = DATE_TIME_FORMAT.format(Instant.now());
                        headers.put(AUTHORIZATION,
                                "MQv2-HMAC-SHA1 Credential=" + user + "/" + dateTime
                                        + ",Signature=" + sign(dateTime, pass));
                        headers.put(DATE_TIME, dateTime);
                        super.start(responseListener, headers);
                    }
                };
            }
        };
    }

    static String sign(String dateTime, String password) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(password.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] raw = mac.doFinal(dateTime.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign RIP-2 admin request", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        channels.values().forEach(channel -> {
            try {
                channel.shutdown();
                if (!channel.awaitTermination(3, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
        });
        channels.clear();
    }
}
