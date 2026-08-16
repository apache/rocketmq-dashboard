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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.util.CredentialUtils;
import org.apache.rocketmq.studio.persistence.entity.RmqAclRule;
import org.apache.rocketmq.studio.persistence.entity.RmqAclUser;
import org.apache.rocketmq.studio.persistence.mapper.RmqAclRuleMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqAclUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MybatisPlusAclRepositoryTest {

    @Mock
    private RmqAclRuleMapper ruleMapper;

    @Mock
    private RmqAclUserMapper userMapper;

    @InjectMocks
    private MybatisPlusAclRepository repository;

    @Test
    void replaceRuleShouldReturnEmptyWhenConcurrentDeleteWins() {
        RmqAclRule existing = new RmqAclRule();
        existing.setId("rule-a");
        existing.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(ruleMapper.selectById("rule-a")).thenReturn(existing);
        when(ruleMapper.updateById(any(RmqAclRule.class))).thenReturn(0);

        AclRuleVO replacement = AclRuleVO.builder()
                .id("rule-a")
                .principal("svc-a")
                .resource("orders")
                .build();

        assertThat(repository.replaceRule(replacement)).isEmpty();
    }

    @Test
    void upsertShouldAssignUniqueRuleIdPerPermission() {
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(userMapper.insert(any(RmqAclUser.class))).thenReturn(1);
        when(ruleMapper.delete(any(QueryWrapper.class))).thenReturn(0);
        when(ruleMapper.insert(any(RmqAclRule.class))).thenReturn(1);

        PlainAccessConfigVO config = PlainAccessConfigVO.builder()
                .accessKey("svc-x")
                .secretKey("secret-x")
                .defaultTopicPerm("DENY")
                .defaultGroupPerm("DENY")
                .topicPerms(List.of("order-*=PUB", "payment-*=SUB"))
                .groupPerms(List.of("cg-order=SUB", "cg-payment=SUB"))
                .build();

        repository.createAndUpdatePlainAccessConfig(config);

        ArgumentCaptor<RmqAclRule> captor = ArgumentCaptor.forClass(RmqAclRule.class);
        verify(ruleMapper, times(6)).insert(captor.capture());
        List<RmqAclRule> rules = captor.getAllValues();

        // Every permission gets a distinct primary key so no entry overwrites another.
        assertThat(rules).extracting(RmqAclRule::getId)
                .containsExactly("plain-svc-x-dt", "plain-svc-x-dg",
                        "plain-svc-x-t-0", "plain-svc-x-t-1",
                        "plain-svc-x-g-0", "plain-svc-x-g-1")
                .doesNotHaveDuplicates();
        assertThat(rules).extracting(RmqAclRule::getResource).containsExactly(
                "*", "*", "order-*", "payment-*", "cg-order", "cg-payment");
    }

    @Test
    void upsertShouldReplacePreviousRulesBeforeInsertingNewOnes() {
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(userMapper.insert(any(RmqAclUser.class))).thenReturn(1);
        when(ruleMapper.delete(any(QueryWrapper.class))).thenReturn(2);
        when(ruleMapper.insert(any(RmqAclRule.class))).thenReturn(1);

        PlainAccessConfigVO config = PlainAccessConfigVO.builder()
                .accessKey("svc-x")
                .secretKey("secret-x")
                .topicPerms(List.of("order-*=PUB"))
                .build();

        repository.createAndUpdatePlainAccessConfig(config);

        InOrder ordered = Mockito.inOrder(ruleMapper);
        ordered.verify(ruleMapper).delete(any(QueryWrapper.class));
        ordered.verify(ruleMapper).insert(any(RmqAclRule.class));
    }

    @Test
    void createShouldRejectBlankSecretForNewAccount() {
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        PlainAccessConfigVO config = PlainAccessConfigVO.builder()
                .accessKey("svc-new")
                .secretKey(" ")
                .build();

        assertThatThrownBy(() -> repository.createAndUpdatePlainAccessConfig(config))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(userMapper, never()).insert(any(RmqAclUser.class));
        verify(ruleMapper, never()).insert(any(RmqAclRule.class));
    }

    @Test
    void updateWithBlankSecretShouldKeepStoredSecret() {
        RmqAclUser existing = userEntity("plain-svc-x", "svc-x",
                CredentialUtils.encodeBase64("kept-secret-value"));
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(existing));
        when(userMapper.updateById(any(RmqAclUser.class))).thenReturn(1);
        when(ruleMapper.delete(any(QueryWrapper.class))).thenReturn(0);

        PlainAccessConfigVO config = PlainAccessConfigVO.builder()
                .accessKey("svc-x")
                .admin(true)
                .build();

        PlainAccessConfigVO result = repository.createAndUpdatePlainAccessConfig(config);

        ArgumentCaptor<RmqAclUser> captor = ArgumentCaptor.forClass(RmqAclUser.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getSecretKey())
                .isEqualTo(CredentialUtils.encodeBase64("kept-secret-value"));
        // The kept secret is not echoed back.
        assertThat(result.getSecretKey()).isNull();
        assertThat(result.isAdmin()).isTrue();
    }

    @Test
    void createShouldPersistWhiteRemoteAddressAndTrimBlanks() {
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(userMapper.insert(any(RmqAclUser.class))).thenReturn(1);
        when(ruleMapper.delete(any(QueryWrapper.class))).thenReturn(0);

        PlainAccessConfigVO config = PlainAccessConfigVO.builder()
                .accessKey("svc-x")
                .secretKey("secret-x")
                .whiteRemoteAddress("  10.0.1.0/24  ")
                .build();

        PlainAccessConfigVO result = repository.createAndUpdatePlainAccessConfig(config);

        ArgumentCaptor<RmqAclUser> captor = ArgumentCaptor.forClass(RmqAclUser.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getWhiteRemoteAddress()).isEqualTo("10.0.1.0/24");
        assertThat(result.getWhiteRemoteAddress()).isEqualTo("10.0.1.0/24");
    }

    @Test
    void createShouldStoreBlankWhiteRemoteAddressAsNull() {
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(userMapper.insert(any(RmqAclUser.class))).thenReturn(1);
        when(ruleMapper.delete(any(QueryWrapper.class))).thenReturn(0);

        PlainAccessConfigVO config = PlainAccessConfigVO.builder()
                .accessKey("svc-x")
                .secretKey("secret-x")
                .whiteRemoteAddress("   ")
                .build();

        repository.createAndUpdatePlainAccessConfig(config);

        ArgumentCaptor<RmqAclUser> captor = ArgumentCaptor.forClass(RmqAclUser.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getWhiteRemoteAddress()).isNull();
    }

    @Test
    void updateShouldExplicitlyClearBlankWhiteRemoteAddress() {
        RmqAclUser existing = userEntity("plain-svc-x", "svc-x",
                CredentialUtils.encodeBase64("kept-secret-value"));
        existing.setWhiteRemoteAddress("10.0.1.0/24");
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(existing));
        when(userMapper.updateById(any(RmqAclUser.class))).thenReturn(1);
        when(userMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        when(ruleMapper.delete(any(QueryWrapper.class))).thenReturn(0);

        PlainAccessConfigVO config = PlainAccessConfigVO.builder()
                .accessKey("svc-x")
                .whiteRemoteAddress("   ")
                .build();

        PlainAccessConfigVO result = repository.createAndUpdatePlainAccessConfig(config);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<UpdateWrapper> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(userMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet()).contains("white_remote_address");
        assertThat(captor.getValue().getParamNameValuePairs()).containsValue(null);
        assertThat(result.getWhiteRemoteAddress()).isNull();
    }

    @Test
    void examineShouldMaskAccountSecrets() {
        String plaintext = "supersecret-abcdef";
        RmqAclUser user = userEntity("plain-svc-x", "svc-x",
                CredentialUtils.encodeBase64(plaintext));
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(user));
        when(ruleMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        AclClusterConfigVO config = repository.examineBrokerClusterAclConfig("cluster-a");

        assertThat(config.getAccounts()).hasSize(1);
        PlainAccessConfigVO account = config.getAccounts().get(0);
        assertThat(account.getSecretKey()).doesNotContain(plaintext).contains("****");
        assertThat(config.getClusterId()).isEqualTo("cluster-a");
    }

    @Test
    void examineShouldScopeAccountsToRequestedCluster() {
        RmqAclUser boundHere = userEntity("plain-a", "svc-a",
                CredentialUtils.encodeBase64("secret-a-value"));
        boundHere.setClusters("cluster-a,cluster-b");
        RmqAclUser global = userEntity("plain-g", "svc-g",
                CredentialUtils.encodeBase64("secret-g-value"));
        RmqAclUser boundElsewhere = userEntity("plain-e", "svc-e",
                CredentialUtils.encodeBase64("secret-e-value"));
        boundElsewhere.setClusters("cluster-c");
        when(userMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(boundHere, global, boundElsewhere));
        when(ruleMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        AclClusterConfigVO config = repository.examineBrokerClusterAclConfig("cluster-a");

        // Global accounts (no cluster binding) appear for every cluster; accounts bound to
        // other clusters are excluded.
        assertThat(config.getAccounts()).extracting(PlainAccessConfigVO::getAccessKey)
                .containsExactly("svc-a", "svc-g");
        assertThat(config.getAccountCount()).isEqualTo(2);
        assertThat(config.isAclEnabled()).isTrue();
    }

    @Test
    void plainAccessUpsertShouldRejectDuplicateAccessKeys() {
        RmqAclUser first = userEntity("plain-first", "svc-duplicate", CredentialUtils.encodeBase64("secret-a"));
        RmqAclUser second = userEntity("plain-second", "svc-duplicate", CredentialUtils.encodeBase64("secret-b"));
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> repository.createAndUpdatePlainAccessConfig(PlainAccessConfigVO.builder()
                        .accessKey("svc-duplicate")
                        .secretKey("replacement")
                        .build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(409));

        verify(userMapper, never()).insert(any(RmqAclUser.class));
        verify(userMapper, never()).updateById(any(RmqAclUser.class));
    }

    @Test
    void saveRuleShouldNormalizeActionCollectionsBeforePersistence() {
        when(ruleMapper.selectById("rule-a")).thenReturn(null);
        when(ruleMapper.insert(any(RmqAclRule.class))).thenReturn(1);
        AclRuleVO rule = AclRuleVO.builder().id("rule-a").principal("svc-a").resource("orders")
                .actions(Arrays.asList(" PUB ", null, "", "SUB", "PUB"))
                .build();

        repository.saveRule(rule);

        ArgumentCaptor<RmqAclRule> captor = ArgumentCaptor.forClass(RmqAclRule.class);
        verify(ruleMapper).insert(captor.capture());
        assertThat(captor.getValue().getActions()).isEqualTo("PUB,SUB");
    }

    @Test
    void saveUserShouldNormalizeClusterCollectionsBeforePersistence() {
        when(userMapper.selectById("user-a")).thenReturn(null);
        when(userMapper.insert(any(RmqAclUser.class))).thenReturn(1);
        AclUserVO user = AclUserVO.builder().id("user-a").username("svc-a").accessKey("svc-a")
                .secretKey("secret").clusters(Arrays.asList(" cluster-a ", null, "", "cluster-b", "cluster-a"))
                .build();

        repository.saveUser(user);

        ArgumentCaptor<RmqAclUser> captor = ArgumentCaptor.forClass(RmqAclUser.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getClusters()).isEqualTo("cluster-a,cluster-b");
    }

    private static RmqAclUser userEntity(String id, String accessKey, String encodedSecret) {
        RmqAclUser entity = new RmqAclUser();
        entity.setId(id);
        entity.setUsername(accessKey);
        entity.setAccessKey(accessKey);
        entity.setSecretKey(encodedSecret);
        entity.setAdmin(false);
        entity.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return entity;
    }
}
