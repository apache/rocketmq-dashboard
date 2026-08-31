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

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.util.CredentialUtils;
import org.apache.rocketmq.studio.persistence.entity.RmqAclRule;
import org.apache.rocketmq.studio.persistence.entity.RmqAclUser;
import org.apache.rocketmq.studio.persistence.mapper.RmqAclRuleMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqAclUserMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MySQL-backed ACL repository. User passwords are stored base64-encoded in
 * {@code rmq_acl_user.secret_key} and decoded when read; plain text is never persisted.
 */
@RequiredArgsConstructor
@Repository
public class MybatisPlusAclRepository implements AclRepository {

    private final RmqAclRuleMapper ruleMapper;
    private final RmqAclUserMapper userMapper;

    @Override
    public PageResult<AclRuleVO> findRulePage(String principal, String resource, String scope,
            String decision, String aclVersion, int page, int pageSize) {
        QueryWrapper<RmqAclRule> query = ruleQuery(principal, resource, scope, decision, aclVersion);
        IPage<RmqAclRule> mapperPage = ruleMapper.selectPage(new Page<>(page, pageSize), query);
        List<AclRuleVO> items = mapperPage.getRecords().stream()
                .map(MybatisPlusAclRepository::toRuleVO)
                .collect(Collectors.toList());
        return PageResult.of(items, mapperPage.getTotal(), (int) mapperPage.getCurrent(),
                (int) mapperPage.getSize());
    }

    @Override
    public AclRuleVO saveRule(AclRuleVO rule) {
        RmqAclRule entity = toRuleEntity(rule);
        if (entity.getId() != null && ruleMapper.selectById(entity.getId()) != null) {
            ruleMapper.updateById(entity);
        } else {
            ruleMapper.insert(entity);
            rule.setId(entity.getId());
        }
        return rule;
    }

    @Override
    public Optional<AclRuleVO> replaceRule(AclRuleVO rule) {
        RmqAclRule existing = ruleMapper.selectById(rule.getId());
        if (existing == null) {
            return Optional.empty();
        }
        RmqAclRule entity = toRuleEntity(rule);
        entity.setGmtCreate(existing.getGmtCreate());
        if (ruleMapper.updateById(entity) == 0) {
            return Optional.empty();
        }
        rule.setGmtCreate(existing.getGmtCreate());
        return Optional.of(rule);
    }

    @Override
    public boolean deleteRule(Long id) {
        return id != null && ruleMapper.deleteById(id) > 0;
    }

    @Override
    public List<AclUserVO> findUsers() {
        return userMapper.selectList(new QueryWrapper<RmqAclUser>().orderByAsc("id")).stream()
                .map(MybatisPlusAclRepository::toUserVO)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<AclUserVO> findUserPage(String keyword, int page, int pageSize) {
        String search = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        QueryWrapper<RmqAclUser> query = new QueryWrapper<RmqAclUser>()
                .and(search != null, w -> w
                        .like("username", search)
                        .or().like("access_key", search))
                .orderByDesc("gmt_create")
                .orderByDesc("id");
        IPage<RmqAclUser> mapperPage = userMapper.selectPage(new Page<>(page, pageSize), query);
        List<AclUserVO> items = mapperPage.getRecords().stream()
                .map(MybatisPlusAclRepository::toUserVO)
                .collect(Collectors.toList());
        return PageResult.of(items, mapperPage.getTotal(), (int) mapperPage.getCurrent(),
                (int) mapperPage.getSize());
    }

    @Override
    public Optional<AclUserVO> findUserById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        RmqAclUser entity = userMapper.selectById(id);
        return Optional.ofNullable(entity).map(MybatisPlusAclRepository::toUserVO);
    }

    @Override
    public AclUserVO saveUser(AclUserVO user) {
        RmqAclUser entity = toUserEntity(user);
        if (entity.getId() != null && userMapper.selectById(entity.getId()) != null) {
            userMapper.updateById(entity);
        } else {
            userMapper.insert(entity);
            user.setId(entity.getId());
        }
        return user;
    }

    @Override
    public Optional<AclUserVO> replaceUser(AclUserVO user) {
        RmqAclUser existing = userMapper.selectById(user.getId());
        if (existing == null) {
            return Optional.empty();
        }
        RmqAclUser entity = toUserEntity(user);
        entity.setGmtCreate(existing.getGmtCreate());
        if (userMapper.updateById(entity) == 0) {
            return Optional.empty();
        }
        user.setGmtCreate(existing.getGmtCreate());
        return Optional.of(user);
    }

    @Override
    public boolean deleteUser(Long id) {
        return id != null && userMapper.deleteById(id) > 0;
    }

    /**
     * Summarizes the ACL accounts provisioned in the dashboard store for a cluster. This is a
     * store-level view, not a live broker query: accounts are read from {@code rmq_acl_user} /
     * {@code rmq_acl_rule} and scoped to the cluster — an account belongs to the cluster when its
     * cluster binding is empty (globally provisioned) or explicitly lists the cluster id.
     */
    @Override
    public AclClusterConfigVO examineBrokerClusterAclConfig(String clusterId) {
        List<PlainAccessConfigVO> accounts = findUsers().stream()
                .filter(user -> appliesToCluster(user.getClusters(), clusterId))
                .map(this::toPlainAccessConfig)
                .collect(Collectors.toList());
        return AclClusterConfigVO.builder()
                .clusterId(clusterId)
                .aclEnabled(!accounts.isEmpty())
                .aclVersion("ACL 2.0")
                .globalWhiteRemoteAddresses(List.of())
                .accounts(accounts)
                .accountCount(accounts.size())
                .build();
    }

    private static boolean appliesToCluster(List<String> clusters, String clusterId) {
        return clusters == null || clusters.isEmpty() || clusters.contains(clusterId);
    }

    /**
     * Upserts the account identity and replaces its per-resource permissions atomically, so a
     * failure midway cannot leave the account with a partially replaced rule set.
     */
    @Override
    @Transactional
    public PlainAccessConfigVO createAndUpdatePlainAccessConfig(PlainAccessConfigVO config) {
        validatePermissionEntries(config.getTopicPerms(), "topicPerms");
        validatePermissionEntries(config.getGroupPerms(), "groupPerms");
        List<RmqAclUser> existingAccounts = userMapper.selectList(
                new QueryWrapper<RmqAclUser>().eq("access_key", config.getAccessKey()));
        if (existingAccounts.size() > 1) {
            throw new BusinessException(409, "Multiple plain access accounts use accessKey: "
                    + config.getAccessKey());
        }
        RmqAclUser existing = existingAccounts.isEmpty() ? null : existingAccounts.get(0);
        boolean secretProvided = StringUtils.hasText(config.getSecretKey());
        if (!secretProvided && existing == null) {
            throw new BusinessException(400, "secretKey is required for a new plain access account");
        }
        RmqAclUser entity = new RmqAclUser();
        if (existing != null) {
            entity.setId(existing.getId());
            entity.setGmtCreate(existing.getGmtCreate());
        } else {
            entity.setGmtCreate(LocalDateTime.now());
        }
        entity.setUsername(config.getAccessKey());
        entity.setAccessKey(config.getAccessKey());
        if (secretProvided) {
            entity.setSecretKey(CredentialUtils.encodeBase64(config.getSecretKey()));
        } else {
            // Blank secret on an existing account keeps the stored secret unchanged.
            entity.setSecretKey(existing.getSecretKey());
        }
        entity.setAdmin(config.isAdmin());
        entity.setClusters(null);
        entity.setWhiteRemoteAddress(normalizeWhiteRemoteAddress(config.getWhiteRemoteAddress()));
        entity.setGmtModified(LocalDateTime.now());
        if (existing != null) {
            userMapper.updateById(entity);
            if (entity.getWhiteRemoteAddress() == null) {
                // MyBatis-Plus omits null entity fields from updateById. Assign this column
                // explicitly so clearing the whitelist does not silently retain its old value.
                userMapper.update(null, new UpdateWrapper<RmqAclUser>()
                        .eq("id", entity.getId())
                        .set("white_remote_address", null));
            }
        } else {
            userMapper.insert(entity);
        }

        upsertPlainAccessRules(config);

        return PlainAccessConfigVO.builder()
                .accessKey(config.getAccessKey())
                // The secret is echoed only when it was just provided; otherwise it stays
                // hidden (read-back views always mask it).
                .secretKey(secretProvided ? config.getSecretKey() : null)
                .whiteRemoteAddress(entity.getWhiteRemoteAddress())
                .admin(config.isAdmin())
                .defaultTopicPerm(config.getDefaultTopicPerm())
                .defaultGroupPerm(config.getDefaultGroupPerm())
                .topicPerms(config.getTopicPerms() == null ? null : new ArrayList<>(config.getTopicPerms()))
                .groupPerms(config.getGroupPerms() == null ? null : new ArrayList<>(config.getGroupPerms()))
                .gmtCreate(entity.getGmtCreate())
                .build();
    }

    private static String normalizeWhiteRemoteAddress(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void upsertPlainAccessRules(PlainAccessConfigVO config) {
        ruleMapper.delete(new QueryWrapper<RmqAclRule>()
                .eq("principal", config.getAccessKey())
                .eq("acl_version", "2.0"));
        List<RmqAclRule> rules = new ArrayList<>();
        if (config.getDefaultTopicPerm() != null) {
            rules.add(plainRule(config.getAccessKey(), "*", "Cluster", config.getDefaultTopicPerm(),
                    "DEFAULT_TOPIC"));
        }
        if (config.getDefaultGroupPerm() != null) {
            rules.add(plainRule(config.getAccessKey(), "*", "Cluster", config.getDefaultGroupPerm(),
                    "DEFAULT_GROUP"));
        }
        if (config.getTopicPerms() != null) {
            for (String entry : config.getTopicPerms()) {
                String[] parts = splitPerm(entry);
                if (parts != null) {
                    rules.add(plainRule(config.getAccessKey(), parts[0], "Topic", parts[1], "LITERAL"));
                }
            }
        }
        if (config.getGroupPerms() != null) {
            for (String entry : config.getGroupPerms()) {
                String[] parts = splitPerm(entry);
                if (parts != null) {
                    rules.add(plainRule(config.getAccessKey(), parts[0], "Group", parts[1], "LITERAL"));
                }
            }
        }
        for (RmqAclRule rule : rules) {
            ruleMapper.insert(rule);
        }
    }

    private RmqAclRule plainRule(String principal, String resource, String resourceType,
                                 String actions, String resourcePattern) {
        RmqAclRule rule = new RmqAclRule();
        rule.setPrincipal(principal);
        rule.setResource(resource);
        rule.setResourceType(resourceType);
        rule.setResourcePattern(resourcePattern);
        rule.setActions(actions);
        rule.setDecision("ALLOW");
        rule.setScope("*");
        rule.setAclVersion("2.0");
        rule.setGmtCreate(LocalDateTime.now());
        rule.setGmtModified(LocalDateTime.now());
        return rule;
    }

    private PlainAccessConfigVO toPlainAccessConfig(AclUserVO user) {
        List<AclRuleVO> userRules = ruleMapper.selectList(ruleQuery(user.getAccessKey(), null, null, null, null))
                .stream()
                .map(MybatisPlusAclRepository::toRuleVO)
                .collect(Collectors.toList());
        List<String> topicPerms = new ArrayList<>();
        List<String> groupPerms = new ArrayList<>();
        String defaultTopicPerm = null;
        String defaultGroupPerm = null;
        for (AclRuleVO rule : userRules) {
            String actions = joinNormalizedCsv(rule.getActions());
            if ("Topic".equals(rule.getResourceType())) {
                topicPerms.add(rule.getResource() + "=" + actions);
            } else if ("Group".equals(rule.getResourceType())) {
                groupPerms.add(rule.getResource() + "=" + actions);
            } else if ("Cluster".equals(rule.getResourceType()) && "*".equals(rule.getResource())) {
                if ("DEFAULT_TOPIC".equals(rule.getResourcePattern())) {
                    defaultTopicPerm = actions;
                } else if ("DEFAULT_GROUP".equals(rule.getResourcePattern())) {
                    defaultGroupPerm = actions;
                }
            }
        }
        return PlainAccessConfigVO.builder()
                .accessKey(user.getAccessKey())
                // Read-back views never expose the plaintext secret; only the explicit
                // per-user credentials endpoint does.
                .secretKey(CredentialUtils.mask(user.getSecretKey()))
                .whiteRemoteAddress(user.getWhiteRemoteAddress())
                .admin(user.isAdmin())
                .defaultTopicPerm(defaultTopicPerm)
                .defaultGroupPerm(defaultGroupPerm)
                .topicPerms(topicPerms)
                .groupPerms(groupPerms)
                .gmtCreate(user.getGmtCreate())
                .build();
    }

    private static QueryWrapper<RmqAclRule> ruleQuery(String principal, String resource, String scope,
            String decision, String aclVersion) {
        return new QueryWrapper<RmqAclRule>()
                .like(StringUtils.hasText(principal), "principal", principal)
                .like(StringUtils.hasText(resource), "resource", resource)
                .eq(StringUtils.hasText(scope), "scope", scope)
                .eq(StringUtils.hasText(decision), "decision", decision)
                .eq(StringUtils.hasText(aclVersion), "acl_version", aclVersion)
                .orderByDesc("gmt_create")
                .orderByDesc("id");
    }

    private static String[] splitPerm(String entry) {
        if (entry == null) {
            return null;
        }
        int idx = entry.lastIndexOf('=');
        if (idx <= 0) {
            return null;
        }
        return new String[]{entry.substring(0, idx).trim(), entry.substring(idx + 1).trim()};
    }

    private static void validatePermissionEntries(List<String> entries, String field) {
        if (entries == null) {
            return;
        }
        for (int index = 0; index < entries.size(); index++) {
            String[] parts = splitPerm(entries.get(index));
            if (parts == null || parts[0].isBlank() || parts[1].isBlank()) {
                throw new BusinessException(400,
                        field + "[" + index + "] must use non-blank resource=permission format");
            }
        }
    }

    // ── Mapping ────────────────────────────────────────────────────

    private static AclRuleVO toRuleVO(RmqAclRule entity) {
        return AclRuleVO.builder()
                .id(entity.getId())
                .principal(entity.getPrincipal())
                .resource(entity.getResource())
                .resourceType(entity.getResourceType())
                .resourcePattern(entity.getResourcePattern())
                .actions(splitCsv(entity.getActions()))
                .decision(entity.getDecision())
                .scope(entity.getScope())
                .aclVersion(entity.getAclVersion())
                .gmtCreate(entity.getGmtCreate())
                .build();
    }

    private static RmqAclRule toRuleEntity(AclRuleVO rule) {
        RmqAclRule entity = new RmqAclRule();
        entity.setId(rule.getId());
        entity.setPrincipal(rule.getPrincipal());
        entity.setResource(rule.getResource());
        entity.setResourceType(rule.getResourceType());
        entity.setResourcePattern(rule.getResourcePattern());
        entity.setActions(joinNormalizedCsv(rule.getActions()));
        entity.setDecision(rule.getDecision());
        entity.setScope(rule.getScope());
        entity.setAclVersion(rule.getAclVersion());
        entity.setGmtCreate(rule.getGmtCreate());
        entity.setGmtModified(LocalDateTime.now());
        return entity;
    }

    private static AclUserVO toUserVO(RmqAclUser entity) {
        return AclUserVO.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .accessKey(entity.getAccessKey())
                .secretKey(CredentialUtils.decodeBase64(entity.getSecretKey()))
                .admin(Boolean.TRUE.equals(entity.getAdmin()))
                .clusters(splitCsv(entity.getClusters()))
                .whiteRemoteAddress(entity.getWhiteRemoteAddress())
                .gmtCreate(entity.getGmtCreate())
                .build();
    }

    private static RmqAclUser toUserEntity(AclUserVO user) {
        RmqAclUser entity = new RmqAclUser();
        entity.setId(user.getId());
        entity.setUsername(user.getUsername());
        entity.setAccessKey(user.getAccessKey());
        entity.setSecretKey(CredentialUtils.encodeBase64(user.getSecretKey()));
        entity.setAdmin(user.isAdmin());
        entity.setClusters(joinNormalizedCsv(user.getClusters()));
        entity.setGmtCreate(user.getGmtCreate());
        entity.setGmtModified(LocalDateTime.now());
        return entity;
    }

    private static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private static String joinNormalizedCsv(List<String> values) {
        if (values == null) {
            return null;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim());
            }
        }
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }
}
