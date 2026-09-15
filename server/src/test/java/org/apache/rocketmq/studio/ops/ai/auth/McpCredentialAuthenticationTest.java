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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.MqAdminProperties;
import org.apache.rocketmq.studio.cluster.broker.MqClientPool;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceResolver;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.provider.apache.RocketMQDefaultClusterResolver;
import org.apache.rocketmq.studio.provider.apache.RocketMQProperties;
import org.apache.rocketmq.studio.provider.credential.CloudCredentialRepository;
import org.apache.rocketmq.studio.provider.credential.CloudCredentialVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class McpCredentialAuthenticationTest {

    private static final List<String> ENTRY_POINTS = List.of("/api/mcp", "/api/mcp/tools/call");
    private static final String CLUSTER = "instance-test";
    private static final String CLOUD_ACCESS_KEY = "cloud-ak";
    private static final String CLOUD_SECRET_KEY = "cloud-test-secret";
    private static final String ADMIN_ACCESS_KEY = "admin-ak";
    private static final String ADMIN_SECRET_KEY = "admin-test-secret";

    private InstanceResolver instanceResolver;
    private RuntimeAdminClientResolver adminResolver;
    private CloudCredentialRepository cloudCredentialRepository;
    private InstanceVO instance;
    private CloudCredentialVO cloudCredential;
    private MqAdminProperties.Credential adminCredential;
    private McpAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        instanceResolver = mock(InstanceResolver.class);
        adminResolver = mock(RuntimeAdminClientResolver.class);
        cloudCredentialRepository = mock(CloudCredentialRepository.class);

        instance = InstanceVO.builder().name(CLUSTER).vendor(InstanceVendor.ALIYUN)
                .credentialId(7L).adminCredentialRef("admin").build();
        when(instanceResolver.findByName(CLUSTER)).thenReturn(Optional.of(instance));

        cloudCredential = new CloudCredentialVO();
        cloudCredential.setVendor(InstanceVendor.ALIYUN);
        cloudCredential.setAccessKey(CLOUD_ACCESS_KEY);
        cloudCredential.setSecretKey(CLOUD_SECRET_KEY);
        when(cloudCredentialRepository.findById(7L)).thenReturn(Optional.of(cloudCredential));

        adminCredential = new MqAdminProperties.Credential();
        adminCredential.setAccessKey(ADMIN_ACCESS_KEY);
        adminCredential.setSecretKey(ADMIN_SECRET_KEY);
        when(adminResolver.resolveCredential(instance)).thenReturn(adminCredential);

        filter = new McpAuthenticationFilter(new McpAuthenticator(
                instanceResolver, adminResolver, cloudCredentialRepository), new ObjectMapper());
    }

    @AfterEach
    void clearUserContext() {
        AuthenticatedUserContext.clear();
    }

    @ParameterizedTest
    @EnumSource(InstanceVendor.class)
    void authenticatesResolvedInstanceCredentialsAtBothEntryPoints(InstanceVendor vendor) throws Exception {
        instance.setVendor(vendor);
        cloudCredential.setVendor(vendor);
        boolean apache = vendor == InstanceVendor.APACHE;
        String accessKey = apache ? ADMIN_ACCESS_KEY : CLOUD_ACCESS_KEY;
        String secretKey = apache ? ADMIN_SECRET_KEY : CLOUD_SECRET_KEY;

        for (String path : ENTRY_POINTS) {
            MockHttpServletRequest request = signedRequest(path, CLUSTER, accessKey, secretKey);
            byte[] body = "body remains available".getBytes(StandardCharsets.UTF_8);
            request.setContent(body);
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean invoked = new AtomicBoolean();

            filter.doFilter(request, response, (verifiedRequest, verifiedResponse) -> {
                invoked.set(true);
                assertThat(verifiedRequest.getAttribute(McpAuthentication.ATTRIBUTE))
                        .isEqualTo(new McpAuthentication(CLUSTER, accessKey));
                assertThat(AuthenticatedUserContext.currentUsernameOrSystem()).isEqualTo(accessKey);
                assertThat(AuthenticatedUserContext.currentUserId()).isNull();
                assertThat(AuthenticatedUserContext.currentUserIsAdminOrSystem()).isFalse();
                assertThat(verifiedRequest.getInputStream().readAllBytes()).isEqualTo(body);
            });

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(invoked).isTrue();
            assertIdentityCleared();
        }
        verify(instanceResolver, times(ENTRY_POINTS.size())).findByName(CLUSTER);
        verifyNoMoreInteractions(instanceResolver);
        if (apache) {
            verify(adminResolver, times(ENTRY_POINTS.size())).resolveCredential(instance);
            verifyNoMoreInteractions(adminResolver);
            verifyNoInteractions(cloudCredentialRepository);
        } else {
            verify(cloudCredentialRepository, times(ENTRY_POINTS.size())).findById(7L);
            verifyNoMoreInteractions(cloudCredentialRepository);
            verifyNoInteractions(adminResolver);
        }
    }

    @Test
    void clearsIdentityAndPropagatesDownstreamFailure() throws Exception {
        ServletException failure = new ServletException("Downstream execution failed");

        for (String path : ENTRY_POINTS) {
            MockHttpServletRequest request = signedRequest(path, CLUSTER, CLOUD_ACCESS_KEY, CLOUD_SECRET_KEY);
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertThatThrownBy(() -> filter.doFilter(request, response, (verifiedRequest, verifiedResponse) -> {
                assertThat(AuthenticatedUserContext.currentUsernameOrSystem()).isEqualTo(CLOUD_ACCESS_KEY);
                assertThat(AuthenticatedUserContext.currentUserId()).isNull();
                assertThat(AuthenticatedUserContext.currentUserIsAdminOrSystem()).isFalse();
                throw failure;
            })).isSameAs(failure);

            assertIdentityCleared();
            assertThat(response.getContentAsByteArray()).isEmpty();
        }
    }

    @Test
    void rejectsUnknownTargetBeforeReadingCredentials() throws Exception {
        when(instanceResolver.findByName(CLUSTER)).thenReturn(Optional.empty());

        assertRejectedAtBothEntryPoints(401);
        verifyNoInteractions(adminResolver, cloudCredentialRepository);
    }

    @Test
    void rejectsMissingCloudCredentialReference() throws Exception {
        instance.setCredentialId(null);

        assertRejectedAtBothEntryPoints(401);
        verifyNoInteractions(adminResolver, cloudCredentialRepository);
    }

    @Test
    void rejectsMissingCloudCredential() throws Exception {
        when(cloudCredentialRepository.findById(7L)).thenReturn(Optional.empty());

        assertRejectedAtBothEntryPoints(401);
    }

    @ParameterizedTest
    @ValueSource(ints = {422, 503})
    void rejectsApacheCredentialBusinessFailures(int code) throws Exception {
        instance.setVendor(InstanceVendor.APACHE);
        when(adminResolver.resolveCredential(instance))
                .thenThrow(new BusinessException(code, "Admin credential unavailable"));

        assertRejectedAtBothEntryPoints(401);
    }

    @Test
    void rejectsCredentialConfigurationFailureDuringTargetResolution() throws Exception {
        when(instanceResolver.findByName(CLUSTER))
                .thenThrow(new BusinessException(422, "Admin credential is not configured"));

        assertRejectedAtBothEntryPoints(401);
        verifyNoInteractions(adminResolver, cloudCredentialRepository);
    }

    @Test
    void preservesTargetResolutionFailureAsInternalError() throws Exception {
        when(instanceResolver.findByName(CLUSTER))
                .thenThrow(new BusinessException(503, "NameServer unavailable"));

        assertRejectedAtBothEntryPoints(500);
        verifyNoInteractions(adminResolver, cloudCredentialRepository);
    }

    @Test
    void preservesCloudCredentialRepositoryFailureAsInternalError() throws Exception {
        when(cloudCredentialRepository.findById(7L))
                .thenThrow(new DataAccessResourceFailureException("Credential database unavailable"));

        assertRejectedAtBothEntryPoints(500);
    }

    @Test
    void preservesUnexpectedAdminResolverFailureAsInternalError() throws Exception {
        instance.setVendor(InstanceVendor.APACHE);
        when(adminResolver.resolveCredential(instance))
                .thenThrow(new DataAccessResourceFailureException("Credential resolver unavailable"));

        assertRejectedAtBothEntryPoints(500);
    }

    @ParameterizedTest
    @CsvSource({
        "wrong-access-key, cloud-test-secret",
        "cloud-ak, wrong-secret"
    })
    void rejectsInvalidCredentials(String accessKey, String secretKey) throws Exception {
        assertRejectedAtBothEntryPoints(401, CLUSTER, accessKey, secretKey);
    }

    @Nested
    class TargetResolutionIntegration {
        private static final String CONFIGURED_CLUSTER = "DefaultCluster";

        private final InstanceRepository repository = mock(InstanceRepository.class);
        private final MqAdminExtFactory factory = mock(MqAdminExtFactory.class);

        @BeforeEach
        void useRealResolvers() {
            RocketMQProperties properties = new RocketMQProperties();
            properties.setNamesrvAddr("configured:9876");
            MqAdminProperties credentials = new MqAdminProperties();
            credentials.getCredentials().put("admin", adminCredential);
            when(factory.execute(eq("configured:9876"), any(), eq("admin"), any()))
                    .thenReturn(List.of(CONFIGURED_CLUSTER));

            RocketMQDefaultClusterResolver configured = new RocketMQDefaultClusterResolver(properties, credentials, factory);
            InstanceResolver resolver = new InstanceResolver(repository, configured);
            RuntimeAdminClientResolver runtime = new RuntimeAdminClientResolver(
                    resolver, factory, credentials, mock(MqClientPool.class));
            filter = new McpAuthenticationFilter(new McpAuthenticator(
                    resolver, runtime, cloudCredentialRepository), new ObjectMapper());
        }

        @Test
        void authenticatesConfiguredTargetWithoutDatabaseRecordAtBothEntryPoints() throws Exception {
            for (String path : ENTRY_POINTS) {
                MockHttpServletRequest request = signedRequest(
                        path, CONFIGURED_CLUSTER, ADMIN_ACCESS_KEY, ADMIN_SECRET_KEY);
                MockHttpServletResponse response = new MockHttpServletResponse();
                AtomicBoolean invoked = new AtomicBoolean();

                filter.doFilter(request, response, (verifiedRequest, verifiedResponse) -> {
                    invoked.set(true);
                    assertThat(verifiedRequest.getAttribute(McpAuthentication.ATTRIBUTE))
                            .isEqualTo(new McpAuthentication(CONFIGURED_CLUSTER, ADMIN_ACCESS_KEY));
                });

                assertThat(response.getStatus()).isEqualTo(200);
                assertThat(invoked).isTrue();
                assertIdentityCleared();
            }
            verify(repository, times(ENTRY_POINTS.size())).findByName(CONFIGURED_CLUSTER);
            verify(factory, times(ENTRY_POINTS.size())).execute(eq("configured:9876"), any(), eq("admin"), any());
            verifyNoMoreInteractions(repository, factory);
            verifyNoInteractions(cloudCredentialRepository);
        }

        @ParameterizedTest
        @EnumSource(InstanceVendor.class)
        void registeredTargetWithMissingCredentialsNeverFallsBackToConfiguration(InstanceVendor vendor)
                throws Exception {
            instance.setName(CONFIGURED_CLUSTER);
            instance.setVendor(vendor);
            instance.setAdminCredentialRef("missing");
            instance.setCredentialId(null);
            when(repository.findByName(CONFIGURED_CLUSTER)).thenReturn(Optional.of(instance));

            assertRejectedAtBothEntryPoints(401, CONFIGURED_CLUSTER, ADMIN_ACCESS_KEY, ADMIN_SECRET_KEY);
            verify(repository, times(ENTRY_POINTS.size())).findByName(CONFIGURED_CLUSTER);
            verifyNoMoreInteractions(repository);
            verifyNoInteractions(factory, cloudCredentialRepository);
        }
    }

    private void assertRejectedAtBothEntryPoints(int status) throws Exception {
        boolean apache = instance.getVendor() == InstanceVendor.APACHE;
        assertRejectedAtBothEntryPoints(status, CLUSTER,
                apache ? ADMIN_ACCESS_KEY : CLOUD_ACCESS_KEY,
                apache ? ADMIN_SECRET_KEY : CLOUD_SECRET_KEY);
    }

    private void assertRejectedAtBothEntryPoints(int status, String cluster, String accessKey, String secretKey)
            throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> expectedError = status == 401
                ? Map.of("code", "UNAUTHENTICATED",
                        "message", "MCP authentication failed.",
                        "hint", "Configure valid MCP credentials and retry the MCP request.")
                : Map.of("code", "INTERNAL_ERROR",
                        "message", "MCP authentication failed unexpectedly.",
                        "hint", "Retry once; if the failure persists, contact an administrator.");
        for (String path : ENTRY_POINTS) {
            MockHttpServletRequest request = signedRequest(path, cluster, accessKey, secretKey);
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean invoked = new AtomicBoolean();

            filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

            assertThat(response.getStatus()).as(path).isEqualTo(status);
            assertThat(objectMapper.readTree(response.getContentAsByteArray()))
                    .isEqualTo(objectMapper.valueToTree(expectedError));
            assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
                    .isEqualTo(status == 401 ? McpAuthenticator.ALGORITHM : null);
            assertThat(request.getAttribute(McpAuthentication.ATTRIBUTE)).isNull();
            assertThat(invoked).isFalse();
            assertIdentityCleared();
        }
    }

    private static void assertIdentityCleared() {
        assertThat(AuthenticatedUserContext.currentUsernameOrSystem()).isEqualTo(AuthenticatedUserContext.SYSTEM_ACTOR);
        assertThat(AuthenticatedUserContext.currentUserId()).isNull();
        assertThat(AuthenticatedUserContext.currentUserIsAdminOrSystem()).isTrue();
    }

    private MockHttpServletRequest signedRequest(String path, String cluster, String accessKey, String secretKey)
            throws Exception {
        String timestamp = Long.toString(System.currentTimeMillis());
        String canonical = McpAuthenticator.canonicalRequest(accessKey, cluster, timestamp, "POST", path);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader(HttpHeaders.AUTHORIZATION,
                McpAuthenticator.ALGORITHM + " Credential=" + accessKey + ", Signature=" + signature);
        request.addHeader(McpAuthenticator.HEADER_CLUSTER, cluster);
        request.addHeader(McpAuthenticator.HEADER_TIMESTAMP, timestamp);
        return request;
    }
}
