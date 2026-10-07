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

import org.apache.rocketmq.studio.cluster.broker.MqAdminProperties;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceResolver;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.provider.credential.CloudCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the {@code rmq-hmac-sha256} verification of {@link McpAuthenticator}: what a signed request
 * must carry, what the signature covers, how wide the clock-skew window is, and which failures
 * collapse into the single opaque 401 versus which must propagate so the caller can answer 500.
 */
class McpAuthenticatorTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final String INSTANCE = "instance-a";
    private static final String ACCESS_KEY = "ak-rmq-studio";
    private static final String SECRET_KEY = "sk-secret-material";

    private final InstanceResolver instanceResolver = mock(InstanceResolver.class);
    private final RuntimeAdminClientResolver adminClientResolver = mock(RuntimeAdminClientResolver.class);
    private final CloudCredentialRepository cloudCredentialRepository = mock(CloudCredentialRepository.class);
    private final McpAuthenticator authenticator = new McpAuthenticator(
            instanceResolver, adminClientResolver, cloudCredentialRepository,
            Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));

    private final InstanceVO instance = InstanceVO.builder()
            .name(INSTANCE).vendor(InstanceVendor.APACHE).build();

    @BeforeEach
    void setUp() {
        when(instanceResolver.findByName(INSTANCE)).thenReturn(Optional.of(instance));
        MqAdminProperties.Credential credential = new MqAdminProperties.Credential();
        credential.setAccessKey(ACCESS_KEY);
        credential.setSecretKey(SECRET_KEY);
        when(adminClientResolver.resolveCredential(instance)).thenReturn(credential);
    }

    private static String signature(String secretKey, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String authorization(String accessKey, String secretKey, long timestamp,
                                 String method, String requestTarget) {
        String canonical = McpAuthenticator.canonicalRequest(
                accessKey, INSTANCE, String.valueOf(timestamp), method, requestTarget);
        return McpAuthenticator.ALGORITHM + " Credential=" + accessKey
                + ", Signature=" + signature(secretKey, canonical);
    }

    private MockHttpServletRequest signedRequest(long timestamp, String method, String requestTarget,
                                                String query, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(requestTarget);
        if (query != null) {
            request.setQueryString(query);
        }
        request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        request.addHeader(McpAuthenticator.HEADER_INSTANCE, INSTANCE);
        request.addHeader(McpAuthenticator.HEADER_TIMESTAMP, String.valueOf(timestamp));
        return request;
    }

    private String validAuthorization(long timestamp, String method, String targetWithQuery) {
        return authorization(ACCESS_KEY, SECRET_KEY, timestamp, method, targetWithQuery);
    }

    @Test
    void verifiesAValidlySignedRequest() {
        MockHttpServletRequest request = signedRequest(NOW, "POST", "/api/mcp/tools/call",
                null, validAuthorization(NOW, "POST", "/api/mcp/tools/call"));

        McpAuthentication authentication = authenticator.authenticate(request);

        assertThat(authentication.instanceId()).isEqualTo(INSTANCE);
        assertThat(authentication.principal()).isEqualTo(ACCESS_KEY);
    }

    /**
     * The query string is signed material: a request whose query differs from the one the signer
     * used must be rejected, or an attacker could append parameters to a captured request.
     */
    @Test
    void theQueryStringIsPartOfTheSignedMaterial() {
        MockHttpServletRequest correctlySigned = signedRequest(NOW, "POST", "/api/mcp/tools/call",
                "tool=list", validAuthorization(NOW, "POST", "/api/mcp/tools/call?tool=list"));
        assertThat(authenticator.authenticate(correctlySigned).principal()).isEqualTo(ACCESS_KEY);

        MockHttpServletRequest tamperedQuery = signedRequest(NOW, "POST", "/api/mcp/tools/call",
                "tool=delete", validAuthorization(NOW, "POST", "/api/mcp/tools/call?tool=list"));
        assertThatThrownBy(() -> authenticator.authenticate(tamperedQuery))
                .isInstanceOf(McpAuthenticationException.class)
                .hasMessage(McpAuthenticator.AUTHENTICATION_FAILED_MESSAGE);
    }

    @Test
    void rejectsAMissingAuthorizationHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/mcp/tools/call");
        request.addHeader(McpAuthenticator.HEADER_INSTANCE, INSTANCE);
        request.addHeader(McpAuthenticator.HEADER_TIMESTAMP, String.valueOf(NOW));

        assertThatThrownBy(() -> authenticator.authenticate(request))
                .isInstanceOf(McpAuthenticationException.class)
                .hasMessage("MCP authentication is required.");
    }

    @Test
    void rejectsAMalformedAuthorizationHeader() {
        MockHttpServletRequest request = signedRequest(NOW, "POST", "/api/mcp/tools/call",
                null, "Bearer something-else");

        assertThatThrownBy(() -> authenticator.authenticate(request))
                .isInstanceOf(McpAuthenticationException.class)
                .hasMessage(McpAuthenticator.AUTHENTICATION_FAILED_MESSAGE);
    }

    @Test
    void rejectsARequestSignedWithTheWrongSecret() {
        MockHttpServletRequest request = signedRequest(NOW, "POST", "/api/mcp/tools/call",
                null, authorization(ACCESS_KEY, "sk-of-another-instance", NOW, "POST", "/api/mcp/tools/call"));

        assertThatThrownBy(() -> authenticator.authenticate(request))
                .isInstanceOf(McpAuthenticationException.class)
                .hasMessage(McpAuthenticator.AUTHENTICATION_FAILED_MESSAGE);
    }

    @Test
    void rejectsARequestSignedWithTheWrongAccessKey() {
        MockHttpServletRequest request = signedRequest(NOW, "POST", "/api/mcp/tools/call",
                null, authorization("ak-of-another-instance", SECRET_KEY, NOW, "POST", "/api/mcp/tools/call"));

        assertThatThrownBy(() -> authenticator.authenticate(request))
                .isInstanceOf(McpAuthenticationException.class)
                .hasMessage(McpAuthenticator.AUTHENTICATION_FAILED_MESSAGE);
    }

    /**
     * The window is the documented five minutes and inclusive at the edge: a request exactly five
     * minutes old is still accepted, one older is not. The edge value is what a signer's retry
     * loop depends on. (Pinned as a literal on purpose: deriving it from the constant would let a
     * silent window change pass unnoticed.)
     */
    @Test
    void theClockSkewWindowIsInclusiveAtExactlyFiveMinutes() {
        long edge = NOW - Duration.ofMinutes(5).toMillis();
        MockHttpServletRequest atTheEdge = signedRequest(edge, "POST", "/api/mcp/tools/call",
                null, validAuthorization(edge, "POST", "/api/mcp/tools/call"));
        assertThat(authenticator.authenticate(atTheEdge).principal()).isEqualTo(ACCESS_KEY);

        long pastTheEdge = edge - 1;
        MockHttpServletRequest oneMillisTooOld = signedRequest(pastTheEdge, "POST", "/api/mcp/tools/call",
                null, validAuthorization(pastTheEdge, "POST", "/api/mcp/tools/call"));
        assertThatThrownBy(() -> authenticator.authenticate(oneMillisTooOld))
                .isInstanceOf(McpAuthenticationException.class)
                .hasMessage(McpAuthenticator.AUTHENTICATION_FAILED_MESSAGE);
    }

    @Test
    void rejectsARequestWithoutTheInstanceIdHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/mcp/tools/call");
        request.addHeader(HttpHeaders.AUTHORIZATION, validAuthorization(NOW, "POST", "/api/mcp/tools/call"));
        request.addHeader(McpAuthenticator.HEADER_TIMESTAMP, String.valueOf(NOW));

        assertThatThrownBy(() -> authenticator.authenticate(request))
                .isInstanceOf(McpAuthenticationException.class)
                .hasMessage(McpAuthenticator.AUTHENTICATION_FAILED_MESSAGE);
    }

    /**
     * An unknown instance is an authentication failure, not a 404: the MCP entry point must not
     * confirm which instance names exist.
     */
    @Test
    void rejectsAnUnknownInstanceAsAnAuthenticationFailure() {
        when(instanceResolver.findByName("instance-unknown")).thenReturn(Optional.empty());
        MockHttpServletRequest request = signedRequest(NOW, "POST", "/api/mcp/tools/call",
                null, validAuthorization(NOW, "POST", "/api/mcp/tools/call"));
        request.removeHeader(McpAuthenticator.HEADER_INSTANCE);
        request.addHeader(McpAuthenticator.HEADER_INSTANCE, "instance-unknown");

        assertThatThrownBy(() -> authenticator.authenticate(request))
                .isInstanceOf(McpAuthenticationException.class)
                .hasMessage(McpAuthenticator.AUTHENTICATION_FAILED_MESSAGE);
    }

    /**
     * Every "this instance has no usable credential" outcome collapses into the same opaque
     * failure as a bad signature: leaking which part of the credential chain is missing would be
     * an oracle.
     */
    @Test
    void collapsesAConfigurationProblemIntoTheOpaqueFailure() {
        when(adminClientResolver.resolveCredential(instance))
                .thenThrow(new BusinessException(422, "Admin credential is not configured"));

        MockHttpServletRequest request = signedRequest(NOW, "POST", "/api/mcp/tools/call",
                null, validAuthorization(NOW, "POST", "/api/mcp/tools/call"));

        assertThatThrownBy(() -> authenticator.authenticate(request))
                .isInstanceOf(McpAuthenticationException.class)
                .hasMessage(McpAuthenticator.AUTHENTICATION_FAILED_MESSAGE);
    }

    /**
     * A database outage inside the credential chain is NOT the caller's fault and must stay
     * distinguishable from a bad credential, so the filter can answer 500 instead of a 401.
     */
    @Test
    void letsANonConfigurationFailurePropagateUntouched() {
        when(adminClientResolver.resolveCredential(instance))
                .thenThrow(new IllegalStateException("credential repository is down"));

        MockHttpServletRequest request = signedRequest(NOW, "POST", "/api/mcp/tools/call",
                null, validAuthorization(NOW, "POST", "/api/mcp/tools/call"));

        assertThatThrownBy(() -> authenticator.authenticate(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("credential repository is down");
    }
}
