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
package org.apache.rocketmq.studio.ops.ai.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.rocketmq.studio.cluster.broker.MqAdminProperties;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceResolver;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.provider.credential.CloudCredentialRepository;
import org.apache.rocketmq.studio.provider.credential.CloudCredentialVO;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class McpAuthenticator {

    static final String ALGORITHM = "RMQ-HMAC-SHA256";
    static final String HEADER_CLUSTER = "X-RMQ-Cluster";
    static final String HEADER_TIMESTAMP = "X-RMQ-Timestamp";
    static final String AUTHENTICATION_FAILED_MESSAGE = "MCP authentication failed.";
    static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "^" + Pattern.quote(ALGORITHM)
                    + " Credential=([^,]+), Signature=([0-9a-f]{64})$");
    private final InstanceResolver instanceResolver;
    private final RuntimeAdminClientResolver adminClientResolver;
    private final CloudCredentialRepository cloudCredentialRepository;
    private final Clock clock;

    public McpAuthenticator(
            InstanceResolver instanceResolver,
            RuntimeAdminClientResolver adminClientResolver,
            CloudCredentialRepository cloudCredentialRepository) {
        this(instanceResolver, adminClientResolver, cloudCredentialRepository, Clock.systemUTC());
    }

    McpAuthenticator(
            InstanceResolver instanceResolver,
            RuntimeAdminClientResolver adminClientResolver,
            CloudCredentialRepository cloudCredentialRepository,
            Clock clock) {
        this.instanceResolver = instanceResolver;
        this.adminClientResolver = adminClientResolver;
        this.cloudCredentialRepository = cloudCredentialRepository;
        this.clock = clock;
    }

    public McpAuthentication authenticate(HttpServletRequest request) {
        Authorization authorization = authorization(request.getHeader(HttpHeaders.AUTHORIZATION));
        String cluster = requiredHeader(request, HEADER_CLUSTER);
        String timestamp = requiredHeader(request, HEADER_TIMESTAMP);
        verifyTimestamp(timestamp);
        InstanceVO instance = resolveInstance(cluster);
        Credential credential = resolveCredential(instance);
        verifySignature(request, authorization, credential, cluster, timestamp);
        return new McpAuthentication(cluster, authorization.accessKey());
    }

    private Authorization authorization(String value) {
        if (value == null || value.isBlank()) {
            throw new McpAuthenticationException("MCP authentication is required.");
        }
        Matcher matcher = AUTHORIZATION.matcher(value);
        if (!matcher.matches()) {
            throw authenticationFailed();
        }
        final String accessKey;
        try {
            accessKey = UriUtils.decode(matcher.group(1), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw authenticationFailed();
        }
        if (accessKey.isBlank() || !accessKey.equals(accessKey.trim())) {
            throw authenticationFailed();
        }
        return new Authorization(accessKey, HexFormat.of().parseHex(matcher.group(2)));
    }

    private String requiredHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null) {
            throw authenticationFailed();
        }
        if (value.isBlank() || !value.equals(value.trim())) {
            throw authenticationFailed();
        }
        return value;
    }

    private InstanceVO resolveInstance(String cluster) {
        try {
            return instanceResolver.findByName(cluster).orElseThrow(McpAuthenticator::authenticationFailed);
        } catch (BusinessException exception) {
            if (exception.getCode() == 422) {
                throw authenticationFailed(exception);
            }
            throw exception;
        }
    }

    private Credential resolveCredential(InstanceVO instance) {
        if (instance.getVendor() == null || instance.getVendor() == InstanceVendor.APACHE) {
            try {
                MqAdminProperties.Credential credential =
                        adminClientResolver.resolveCredential(instance);
                return new Credential(credential.getAccessKey().trim(), credential.getSecretKey().trim());
            } catch (BusinessException exception) {
                throw authenticationFailed(exception);
            }
        }
        if (instance.getCredentialId() == null) {
            throw authenticationFailed();
        }
        CloudCredentialVO credential = cloudCredentialRepository.findById(instance.getCredentialId())
                .orElseThrow(McpAuthenticator::authenticationFailed);

        if (!StringUtils.hasText(credential.getAccessKey())
                || !StringUtils.hasText(credential.getSecretKey())) {
            throw authenticationFailed();
        }
        return new Credential(credential.getAccessKey().trim(), credential.getSecretKey().trim());
    }

    private static void verifySignature(HttpServletRequest request, Authorization authorization, Credential credential,
                                 String cluster, String timestamp) {
        if (!authorization.accessKey().equals(credential.accessKey())) {
            throw authenticationFailed();
        }
        byte[] expected = hmac(credential.secretKey(),
                canonicalRequest(authorization.accessKey(), cluster, timestamp,
                        request.getMethod(), requestTarget(request)));
        if (!MessageDigest.isEqual(expected, authorization.signature())) {
            throw authenticationFailed();
        }
    }

    private void verifyTimestamp(String timestamp) {
        final long requestTime;
        try {
            requestTime = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            throw authenticationFailed(exception);
        }
        long now = clock.millis();
        long allowedSkew = MAX_CLOCK_SKEW.toMillis();
        if (requestTime < now - allowedSkew || requestTime > now + allowedSkew) {
            throw authenticationFailed();
        }
    }

    private static byte[] hmac(String secretKey, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    private static String requestTarget(HttpServletRequest request) {
        String query = request.getQueryString();
        return request.getRequestURI() + (query == null ? "" : "?" + query);
    }

    static String canonicalRequest(
            String accessKey,
            String cluster,
            String timestamp,
            String method,
            String requestTarget) {
        return String.join("\n",
                ALGORITHM,
                accessKey,
                cluster,
                timestamp,
                method,
                requestTarget);
    }

    private static McpAuthenticationException authenticationFailed() {
        return new McpAuthenticationException(AUTHENTICATION_FAILED_MESSAGE);
    }

    private static McpAuthenticationException authenticationFailed(Throwable cause) {
        return new McpAuthenticationException(AUTHENTICATION_FAILED_MESSAGE, cause);
    }

    private record Authorization(String accessKey, byte[] signature) {
    }

    private record Credential(String accessKey, String secretKey) {
    }
}
