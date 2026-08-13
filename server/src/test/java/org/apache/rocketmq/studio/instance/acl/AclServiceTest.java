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

package org.apache.rocketmq.studio.instance.acl;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.model.Acl2PolicyContext;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AclServiceTest {

    @Mock
    private AclRepository aclRepository;

    @Mock
    private OperationAuditService operationAuditService;

    @Mock
    private InstanceRepository instanceRepository;

    @InjectMocks
    private AclService aclService;

    private AclUserVO existingUser;

    @BeforeEach
    void setUp() {
        existingUser = AclUserVO.builder()
                .id("user-1")
                .username("orders")
                .accessKey("access-key-123456")
                .secretKey("secret-key-987654")
                .admin(false)
                .clusters(List.of("cluster-a"))
                .build();
    }

    @Test
    void listRulesShouldReturnRulesFromRepository() {
        List<AclRuleVO> rules = List.of(
                AclRuleVO.builder().principal("user1").resource("topic-1").decision("ALLOW").build(),
                AclRuleVO.builder().principal("user2").resource("topic-2").decision("DENY").build()
        );
        when(aclRepository.findRules("cluster-1", "user1")).thenReturn(rules);

        List<AclRuleVO> result = aclService.listRules("cluster-1", "user1");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPrincipal()).isEqualTo("user1");
        verify(aclRepository).findRules("cluster-1", "user1");
    }

    @Test
    void capabilitiesShouldDescribeApacheRemoteReadSupport() {
        InstanceVO instance = InstanceVO.builder()
                .name("instance-1")
                .vendor(InstanceVendor.APACHE)
                .type(InstanceType.DIRECT)
                .build();
        instance.setId("instance-1");
        when(instanceRepository.findByIdentifier("instance-1")).thenReturn(Optional.of(instance));

        AclCapabilitiesVO capabilities = aclService.capabilities("instance-1");

        assertThat(capabilities.instanceId()).isEqualTo("instance-1");
        assertThat(capabilities.vendor()).isEqualTo(InstanceVendor.APACHE);
        assertThat(capabilities.instanceType()).isEqualTo(InstanceType.DIRECT);
        assertThat(capabilities.stateSource()).isEqualTo("APACHE_ACL2");
        assertThat(capabilities.remoteReadSupported()).isTrue();
        assertThat(capabilities.remoteWriteSupported()).isFalse();
    }

    @Test
    void capabilitiesShouldRejectUnknownInstance() {
        when(instanceRepository.findByIdentifier("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aclService.capabilities("missing"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Instance not found: missing");
    }

    @Test
    void listRulesShouldPassNullFilters() {
        when(aclRepository.findRules(null, null)).thenReturn(List.of());

        List<AclRuleVO> result = aclService.listRules(null, null);

        assertThat(result).isEmpty();
        verify(aclRepository).findRules(null, null);
    }

    @Test
    void createRuleShouldSetIdAndTimestamp() {
        AclRuleVO input = AclRuleVO.builder()
                .principal("user1")
                .resource("topic-1")
                .resourceType("TOPIC")
                .decision("ALLOW")
                .build();

        when(aclRepository.saveRule(any(AclRuleVO.class))).thenAnswer(inv -> inv.getArgument(0));

        AclRuleVO result = aclService.createRule(input);

        assertThat(result.getId()).isNotBlank();
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getPrincipal()).isEqualTo("user1");
        assertThat(result.getResource()).isEqualTo("topic-1");
        verify(aclRepository).saveRule(any(AclRuleVO.class));
        verify(operationAuditService).record(eq("CREATE_ACL_RULE"), eq("ACL_RULE"), eq(result.getId()), eq(null),
                eq("principal=user1"), eq("SUCCESS"), eq(null));
    }

    @Test
    void deleteRuleShouldDelegateToRepository() {
        when(aclRepository.deleteRule("rule-1")).thenReturn(true);
        aclService.deleteRule("rule-1");

        verify(aclRepository).deleteRule("rule-1");
        verify(operationAuditService).record(eq("DELETE_ACL_RULE"), eq("ACL_RULE"), eq("rule-1"), eq(null),
                eq(null), eq("SUCCESS"), eq(null));
    }

    @Test
    void updateRuleShouldRequireId() {
        AclRuleVO input = AclRuleVO.builder()
                .principal("user1")
                .resource("topic-1")
                .build();

        assertThatThrownBy(() -> aclService.updateRule(input))
                .hasMessage("ACL rule id is required");
    }

    @Test
    void updateRuleShouldReplaceExistingRule() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        AclRuleVO input = AclRuleVO.builder()
                .id("rule-1")
                .principal("user1")
                .resource("topic-1")
                .decision("DENY")
                .createdAt(createdAt)
                .build();

        when(aclRepository.replaceRule(input)).thenReturn(Optional.of(input));

        AclRuleVO result = aclService.updateRule(input);

        assertThat(result.getId()).isEqualTo("rule-1");
        assertThat(result.getCreatedAt()).isEqualTo(createdAt);
        assertThat(result.getDecision()).isEqualTo("DENY");
        verify(aclRepository).replaceRule(input);
        verify(aclRepository, never()).saveRule(any(AclRuleVO.class));
        verify(operationAuditService).record(eq("UPDATE_ACL_RULE"), eq("ACL_RULE"), eq("rule-1"), eq(null),
                eq("principal=user1"), eq("SUCCESS"), eq(null));
    }

    @Test
    void updateRuleShouldRejectUnknownIdInsteadOfCreatingRule() {
        when(aclRepository.replaceRule(any(AclRuleVO.class))).thenReturn(Optional.empty());
        when(aclRepository.findRules(null, null)).thenReturn(List.of());
        AclRuleVO update = AclRuleVO.builder()
                .id("missing-rule")
                .principal("orders")
                .resource("orders-topic")
                .decision("DENY")
                .build();

        assertThatThrownBy(() -> aclService.updateRule(update))
                .isInstanceOf(BusinessException.class)
                .hasMessage("ACL rule not found: missing-rule")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
        assertThat(aclService.listRules(null, null)).isEmpty();
        verify(aclRepository, never()).saveRule(any(AclRuleVO.class));
    }

    @Test
    void updateRuleShouldPreserveStoredCreationTimestamp() {
        java.util.concurrent.atomic.AtomicReference<AclRuleVO> stored = new java.util.concurrent.atomic.AtomicReference<>();
        when(aclRepository.saveRule(any(AclRuleVO.class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(aclRepository.replaceRule(any(AclRuleVO.class))).thenAnswer(invocation -> {
            AclRuleVO rule = invocation.getArgument(0);
            // Repository contract: keep the original creation timestamp on replace.
            return Optional.of(AclRuleVO.builder()
                    .id(rule.getId())
                    .principal(rule.getPrincipal())
                    .resource(rule.getResource())
                    .resourceType(rule.getResourceType())
                    .resourcePattern(rule.getResourcePattern())
                    .actions(rule.getActions())
                    .decision(rule.getDecision())
                    .scope(rule.getScope())
                    .aclVersion(rule.getAclVersion())
                    .createdAt(stored.get().getCreatedAt())
                    .build());
        });
        AclRuleVO created = aclService.createRule(AclRuleVO.builder()
                .principal("orders")
                .resource("orders-topic")
                .decision("ALLOW")
                .build());
        LocalDateTime originalCreatedAt = created.getCreatedAt();

        LocalDateTime clientCreatedAt = originalCreatedAt.plusDays(1);
        AclRuleVO update = AclRuleVO.builder()
                .id(created.getId())
                .principal("orders")
                .resource("orders-topic")
                .decision("DENY")
                .createdAt(clientCreatedAt)
                .build();

        AclRuleVO updated = aclService.updateRule(update);

        assertThat(updated.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(updated.getDecision()).isEqualTo("DENY");
        assertThat(update.getCreatedAt()).isEqualTo(clientCreatedAt);
    }

    @Test
    void listUsersShouldMaskCredentialsWithoutChangingStoredUsers() {
        List<AclUserVO> users = List.of(
                AclUserVO.builder()
                        .username("admin")
                        .accessKey("access-key-123456")
                        .secretKey("secret-key-987654")
                        .admin(true)
                        .build(),
                AclUserVO.builder()
                        .username("reader")
                        .accessKey("access-key-654321")
                        .secretKey("secret-key-456789")
                        .admin(false)
                        .build()
        );
        when(aclRepository.findUsers()).thenReturn(users);

        List<AclUserVO> result = aclService.listUsers();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUsername()).isEqualTo("admin");
        assertThat(result.get(0).getAccessKey()).isEqualTo("acce****3456");
        assertThat(result.get(0).getSecretKey()).isEqualTo("secr****7654");
        assertThat(users.get(0).getAccessKey()).isEqualTo("access-key-123456");
        assertThat(users.get(0).getSecretKey()).isEqualTo("secret-key-987654");
        verify(aclRepository).findUsers();
    }

    @ParameterizedTest
    @MethodSource("credentialMasks")
    void listUsersShouldMaskCredentialLengthBoundaries(String credential, String expected) {
        when(aclRepository.findUsers()).thenReturn(List.of(AclUserVO.builder()
                .username("boundary")
                .accessKey(credential)
                .secretKey(credential)
                .build()));

        AclUserVO result = aclService.listUsers().get(0);

        assertThat(result.getAccessKey()).isEqualTo(expected);
        assertThat(result.getSecretKey()).isEqualTo(expected);
    }

    @Test
    void listUsersShouldCopyClustersWhenMaskingCredentials() {
        List<String> clusters = new java.util.ArrayList<>(List.of("cluster-a"));
        when(aclRepository.findUsers()).thenReturn(List.of(AclUserVO.builder()
                .username("admin")
                .accessKey("access-key-123456")
                .secretKey("secret-key-987654")
                .clusters(clusters)
                .build()));

        AclUserVO result = aclService.listUsers().get(0);

        assertThat(result.getClusters()).containsExactly("cluster-a");
        assertThatThrownBy(() -> result.getClusters().add("cluster-b"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(clusters).containsExactly("cluster-a");
    }

    static Stream<Arguments> credentialMasks() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("", ""),
                Arguments.of("12345678", "****"),
                Arguments.of("123456789", "****"),
                Arguments.of("1234567890123456", "****"),
                Arguments.of("access-key-123456", "acce****3456")
        );
    }

    @Test
    void createUserShouldGenerateKeysAndSetTimestamp() {
        AclUserVO input = AclUserVO.builder()
                .username("newuser")
                .admin(false)
                .build();

        when(aclRepository.saveUser(any(AclUserVO.class))).thenAnswer(inv -> inv.getArgument(0));

        AclUserVO result = aclService.createUser(input);

        assertThat(result.getId()).isNotBlank();
        assertThat(result.getAccessKey()).isNotBlank();
        assertThat(result.getSecretKey()).isNotBlank();
        assertThat(result.getAccessKey()).doesNotContain("-");
        assertThat(result.getSecretKey()).doesNotContain("-");
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUsername()).isEqualTo("newuser");
        verify(aclRepository).saveUser(any(AclUserVO.class));
        verify(operationAuditService).record(eq("CREATE_ACL_USER"), eq("ACL_USER"), eq(result.getId()), eq(null),
                argThat(detail -> detail.equals("username=newuser, admin=false")
                        && !detail.contains(result.getAccessKey()) && !detail.contains(result.getSecretKey())),
                eq("SUCCESS"), eq(null));
    }

    @Test
    void deleteUserShouldDelegateToRepository() {
        when(aclRepository.deleteUser("user-1")).thenReturn(true);
        aclService.deleteUser("user-1");

        verify(aclRepository).deleteUser("user-1");
        verify(operationAuditService).record(eq("DELETE_ACL_USER"), eq("ACL_USER"), eq("user-1"), eq(null),
                eq(null), eq("SUCCESS"), eq(null));
    }

    @Test
    void deleteRuleShouldRejectUnknownRule() {
        when(aclRepository.deleteRule("missing")).thenReturn(false);

        assertThatThrownBy(() -> aclService.deleteRule("missing"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
    }

    @Test
    void deleteUserShouldRejectUnknownUser() {
        when(aclRepository.deleteUser("missing")).thenReturn(false);

        assertThatThrownBy(() -> aclService.deleteUser("missing"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
    }

    @Test
    void updateUserShouldRequireId() {
        UpdateAclUserDTO input = new UpdateAclUserDTO();
        input.setUsername("newuser");

        assertThatThrownBy(() -> aclService.updateUser(input))
                .hasMessage("ACL user id is required");
    }

    @Test
    void updateUserShouldSaveExistingUser() {
        UpdateAclUserDTO input = new UpdateAclUserDTO();
        input.setId("user-1");
        input.setUsername("newuser");
        input.setAdmin(true);

        ArgumentCaptor<AclUserVO> captor = ArgumentCaptor.forClass(AclUserVO.class);
        when(aclRepository.findUserById("user-1")).thenReturn(Optional.of(existingUser));
        when(aclRepository.saveUser(any(AclUserVO.class))).thenAnswer(inv -> inv.getArgument(0));

        AclUserVO result = aclService.updateUser(input);

        assertThat(result.getId()).isEqualTo("user-1");
        assertThat(result.getUsername()).isEqualTo("newuser");
        assertThat(result.getAccessKey()).isEqualTo("acce****3456");
        assertThat(result.getSecretKey()).isEqualTo("secr****7654");
        assertThat(result.isAdmin()).isTrue();
        verify(aclRepository).saveUser(captor.capture());
        assertThat(captor.getValue().getAccessKey()).isEqualTo("access-key-123456");
        assertThat(captor.getValue().getSecretKey()).isEqualTo("secret-key-987654");
        verify(operationAuditService).record(eq("UPDATE_ACL_USER"), eq("ACL_USER"), eq("user-1"), eq(null),
                eq("username=newuser, admin=true"), eq("SUCCESS"), eq(null));
    }

    @Test
    void updateUserShouldPreserveAdminWhenNotProvided() {
        AclUserVO adminUser = AclUserVO.builder()
                .id("user-1")
                .username("orders")
                .accessKey("access-key-123456")
                .secretKey("secret-key-987654")
                .admin(true)
                .clusters(List.of("cluster-a"))
                .build();
        UpdateAclUserDTO input = new UpdateAclUserDTO();
        input.setId("user-1");
        input.setUsername("renamed");
        // admin intentionally left null: the existing admin flag must survive the partial update.

        ArgumentCaptor<AclUserVO> captor = ArgumentCaptor.forClass(AclUserVO.class);
        when(aclRepository.findUserById("user-1")).thenReturn(Optional.of(adminUser));
        when(aclRepository.saveUser(any(AclUserVO.class))).thenAnswer(inv -> inv.getArgument(0));

        AclUserVO result = aclService.updateUser(input);

        assertThat(result.getUsername()).isEqualTo("renamed");
        assertThat(result.isAdmin()).isTrue();
        verify(aclRepository).saveUser(captor.capture());
        assertThat(captor.getValue().isAdmin()).isTrue();
    }

    @Test
    void updateUserShouldRejectBlankUsernameWithoutSaving() {
        UpdateAclUserDTO input = new UpdateAclUserDTO();
        input.setId("user-1");
        input.setUsername("   ");

        when(aclRepository.findUserById("user-1")).thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> aclService.updateUser(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("ACL username is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(aclRepository, never()).saveUser(any(AclUserVO.class));
    }

    @Test
    void updateUserShouldThrowWhenUserDoesNotExist() {
        UpdateAclUserDTO input = new UpdateAclUserDTO();
        input.setId("missing");
        input.setUsername("ghost");

        when(aclRepository.findUserById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aclService.updateUser(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("ACL user not found: missing")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
        verify(aclRepository, never()).saveUser(any(AclUserVO.class));
    }

    @Test
    void createRuleShouldRequirePrincipal() {
        AclRuleVO input = AclRuleVO.builder()
                .principal(" ")
                .resource("topic-1")
                .build();

        assertThatThrownBy(() -> aclService.createRule(input))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400))
                .hasMessage("ACL principal is required");
        verify(aclRepository, never()).saveRule(any(AclRuleVO.class));
    }

    @Test
    void createRuleShouldRequireResource() {
        AclRuleVO input = AclRuleVO.builder()
                .principal("user1")
                .resource(" ")
                .build();

        assertThatThrownBy(() -> aclService.createRule(input))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400))
                .hasMessage("ACL resource is required");
        verify(aclRepository, never()).saveRule(any(AclRuleVO.class));
    }

    @Test
    void createUserShouldRequireUsername() {
        AclUserVO input = AclUserVO.builder()
                .username(" ")
                .admin(false)
                .build();

        assertThatThrownBy(() -> aclService.createUser(input))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400))
                .hasMessage("ACL username is required");
        verify(aclRepository, never()).saveUser(any(AclUserVO.class));
    }

    @Test
    void createListUpdateShouldPreserveStoredCredentials() {
        java.util.concurrent.atomic.AtomicReference<AclUserVO> stored = new java.util.concurrent.atomic.AtomicReference<>();
        when(aclRepository.saveUser(any(AclUserVO.class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(aclRepository.findUserById(any())).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(aclRepository.findUsers()).thenAnswer(invocation -> List.of(stored.get()));

        AclUserVO created = aclService.createUser(AclUserVO.builder()
                .username("orders")
                .admin(false)
                .clusters(List.of("cluster-a"))
                .build());
        String accessKey = created.getAccessKey();
        String secretKey = created.getSecretKey();
        AclUserVO listed = aclService.listUsers().get(0);

        UpdateAclUserDTO update = new UpdateAclUserDTO();
        update.setId(listed.getId());
        update.setUsername("orders-admin");
        update.setAdmin(true);
        update.setClusters(listed.getClusters());
        AclUserVO updated = aclService.updateUser(update);

        assertThat(listed.getAccessKey()).isNotEqualTo(accessKey);
        assertThat(listed.getSecretKey()).isNotEqualTo(secretKey);
        assertThat(updated.getAccessKey()).isEqualTo(mask(accessKey));
        assertThat(updated.getSecretKey()).isEqualTo(mask(secretKey));
        AclUserVO storedUser = stored.get();
        assertThat(storedUser.getAccessKey()).isEqualTo(accessKey);
        assertThat(storedUser.getSecretKey()).isEqualTo(secretKey);
        assertThat(storedUser.getUsername()).isEqualTo("orders-admin");
        assertThat(storedUser.isAdmin()).isTrue();
    }

    // ── Plain access / cluster config inspection (PR-7) ──────────────

    @Test
    void examineBrokerClusterAclConfigShouldRequireClusterId() {
        assertThatThrownBy(() -> aclService.examineBrokerClusterAclConfig(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(aclRepository, never()).examineBrokerClusterAclConfig(any());
    }

    @Test
    void examineBrokerClusterAclConfigShouldDelegateToRepository() {
        AclClusterConfigVO expected = AclClusterConfigVO.builder()
                .clusterId("c1")
                .aclEnabled(true)
                .aclVersion("ACL 2.0")
                .accounts(List.of())
                .accountCount(0)
                .build();
        when(aclRepository.examineBrokerClusterAclConfig("c1")).thenReturn(expected);

        AclClusterConfigVO result = aclService.examineBrokerClusterAclConfig("c1");

        assertThat(result).isSameAs(expected);
        verify(aclRepository).examineBrokerClusterAclConfig("c1");
    }

    @Test
    void createAndUpdatePlainAccessConfigShouldRequireAccessKey() {
        PlainAccessConfigVO blank = PlainAccessConfigVO.builder().accessKey(" ").build();
        assertThatThrownBy(() -> aclService.createAndUpdatePlainAccessConfig(blank))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(aclRepository, never()).createAndUpdatePlainAccessConfig(any());
    }

    @Test
    void createAndUpdatePlainAccessConfigShouldDelegateToRepository() {
        PlainAccessConfigVO config = PlainAccessConfigVO.builder()
                .accessKey("ak-1")
                .secretKey("sk-1")
                .admin(false)
                .build();
        when(aclRepository.createAndUpdatePlainAccessConfig(config)).thenReturn(config);

        PlainAccessConfigVO result = aclService.createAndUpdatePlainAccessConfig(config);

        assertThat(result).isSameAs(config);
        verify(aclRepository).createAndUpdatePlainAccessConfig(config);
    }

    @Test
    void createAndUpdatePlainAccessConfigShouldAuditWithoutSensitiveValues() {
        PlainAccessConfigVO config = PlainAccessConfigVO.builder()
                .accessKey("ak-sensitive")
                .secretKey("secret-value")
                .whiteRemoteAddress("10.0.0.0/8")
                .admin(true)
                .build();
        when(aclRepository.createAndUpdatePlainAccessConfig(config)).thenReturn(config);

        aclService.createAndUpdatePlainAccessConfig(config);

        verify(operationAuditService).record(eq("UPSERT_PLAIN_ACCESS_CONFIG"), eq("ACL_USER"),
                eq("ak-sensitive"), eq(null),
                argThat(detail -> detail.equals("admin=true, whiteRemoteAddressConfigured=true")
                        && !detail.contains("secret-value") && !detail.contains("10.0.0.0/8")),
                eq("SUCCESS"), eq(null));
    }

    @Test
    void createAndUpdatePlainAccessConfigShouldNotFailWhenAuditRecordingFails() {
        PlainAccessConfigVO config = PlainAccessConfigVO.builder()
                .accessKey("ak-1")
                .admin(false)
                .build();
        when(aclRepository.createAndUpdatePlainAccessConfig(config)).thenReturn(config);
        doThrow(new IllegalStateException("audit unavailable")).when(operationAuditService)
                .record(any(), any(), any(), any(), any(), any(), any());

        assertThatCode(() -> aclService.createAndUpdatePlainAccessConfig(config))
                .doesNotThrowAnyException();

        verify(operationAuditService).record(eq("UPSERT_PLAIN_ACCESS_CONFIG"), eq("ACL_USER"),
                eq("ak-1"), eq(null),
                eq("admin=false, whiteRemoteAddressConfigured=false"),
                eq("SUCCESS"), eq(null));
    }

    private String mask(String credential) {
        return credential.substring(0, 4) + "****" + credential.substring(credential.length() - 4);
    }

    @Test
    void validateAcl2PolicyShouldAcceptValidPolicy() {
        Acl2PolicyContext policy = new Acl2PolicyContext();
        policy.setPolicyName("orders-policy");
        policy.setBoundType("Topic");
        policy.setRules(List.of(Acl2PolicyContext.AuthorizationRule.defaultAllowRule("orders-*")));
        policy.setWhiteSet(List.of("192.168.1.0/24", "10.0.0.1"));

        aclService.validateAcl2Policy(policy);
    }

    @Test
    void validateAcl2PolicyShouldIgnoreTheJvmDefaultLocaleWhenNormalizingBoundType() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            Acl2PolicyContext policy = new Acl2PolicyContext();
            policy.setPolicyName("orders-policy");
            policy.setBoundType("topic");
            policy.setRules(List.of(Acl2PolicyContext.AuthorizationRule.defaultAllowRule("orders-*")));

            aclService.validateAcl2Policy(policy);
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void validateAcl2PolicyShouldRejectNullPolicy() {
        assertThatThrownBy(() -> aclService.validateAcl2Policy(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }

    @Test
    void validateAcl2PolicyShouldRequirePolicyName() {
        Acl2PolicyContext policy = new Acl2PolicyContext();
        policy.setBoundType("Group");
        policy.setRules(List.of(Acl2PolicyContext.AuthorizationRule.defaultAllowRule("*")));

        assertThatThrownBy(() -> aclService.validateAcl2Policy(policy))
                .isInstanceOf(BusinessException.class)
                .hasMessage("ACL 2.0 policyName is required");
    }

    @Test
    void validateAcl2PolicyShouldRejectInvalidBoundType() {
        Acl2PolicyContext policy = new Acl2PolicyContext();
        policy.setPolicyName("p");
        policy.setBoundType("NONSENSE");
        policy.setRules(List.of(Acl2PolicyContext.AuthorizationRule.defaultAllowRule("*")));

        assertThatThrownBy(() -> aclService.validateAcl2Policy(policy))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("boundType must be one of");
    }

    @Test
    void validateAcl2PolicyShouldAcceptWildcardBoundType() {
        Acl2PolicyContext policy = new Acl2PolicyContext();
        policy.setPolicyName("p");
        policy.setBoundType("*");
        policy.setRules(List.of(Acl2PolicyContext.AuthorizationRule.defaultAllowRule("*")));

        aclService.validateAcl2Policy(policy);
    }

    @Test
    void validateAcl2PolicyShouldRequireRules() {
        Acl2PolicyContext policy = new Acl2PolicyContext();
        policy.setPolicyName("p");
        policy.setBoundType("Topic");

        assertThatThrownBy(() -> aclService.validateAcl2Policy(policy))
                .isInstanceOf(BusinessException.class)
                .hasMessage("ACL 2.0 policy rules are required");
    }

    @Test
    void validateAcl2PolicyShouldAcceptZeroDotZeroWildcardInWhiteSet() {
        Acl2PolicyContext policy = new Acl2PolicyContext();
        policy.setPolicyName("p");
        policy.setBoundType("Group");
        policy.setRules(List.of(Acl2PolicyContext.AuthorizationRule.defaultAllowRule("*")));
        policy.setWhiteSet(List.of("0.0.0.0"));

        aclService.validateAcl2Policy(policy);
    }

    @Test
    void validateAcl2PolicyShouldRejectMalformedWhiteSetEntry() {
        Acl2PolicyContext policy = new Acl2PolicyContext();
        policy.setPolicyName("p");
        policy.setBoundType("Group");
        policy.setRules(List.of(Acl2PolicyContext.AuthorizationRule.defaultAllowRule("*")));
        policy.setWhiteSet(List.of("192.168.1.0/24", "not-an-ip"));

        assertThatThrownBy(() -> aclService.validateAcl2Policy(policy))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("whiteSet entry is not a valid IP/CIDR range");
    }
}
