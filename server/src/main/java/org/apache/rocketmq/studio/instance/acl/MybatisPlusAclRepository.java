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
import org.apache.rocketmq.studio.persistence.entity.RmqAclRule;
import org.apache.rocketmq.studio.persistence.entity.RmqAclUser;
import org.apache.rocketmq.studio.persistence.mapper.RmqAclRuleMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqAclUserMapper;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MySQL-backed ACL repository. User passwords are stored base64-encoded in
 * {@code rmq_acl_user.secret_key} and decoded when read; plain text is never persisted.
 */
@Repository
public class MybatisPlusAclRepository implements AclRepository {

    private final RmqAclRuleMapper ruleMapper;
    private final RmqAclUserMapper userMapper;

    public MybatisPlusAclRepository(RmqAclRuleMapper ruleMapper, RmqAclUserMapper userMapper) {
        this.ruleMapper = ruleMapper;
        this.userMapper = userMapper;
    }

    @Override
    public List<AclRuleVO> findRules(String clusterId, String principal) {
        QueryWrapper<RmqAclRule> query = new QueryWrapper<RmqAclRule>()
                .eq(clusterId != null && !clusterId.isBlank(), "scope", clusterId)
                .eq(principal != null && !principal.isBlank(), "principal", principal)
                .orderByAsc("id");
        return ruleMapper.selectList(query).stream()
                .map(MybatisPlusAclRepository::toRuleVO)
                .collect(Collectors.toList());
    }

    @Override
    public AclRuleVO saveRule(AclRuleVO rule) {
        RmqAclRule entity = toRuleEntity(rule);
        if (ruleMapper.selectById(entity.getId()) != null) {
            ruleMapper.updateById(entity);
        } else {
            ruleMapper.insert(entity);
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
        entity.setCreatedAt(existing.getCreatedAt());
        ruleMapper.updateById(entity);
        rule.setCreatedAt(existing.getCreatedAt());
        return Optional.of(rule);
    }

    @Override
    public void deleteRule(String id) {
        ruleMapper.deleteById(id);
    }

    @Override
    public List<AclUserVO> findUsers() {
        return userMapper.selectList(new QueryWrapper<RmqAclUser>().orderByAsc("id")).stream()
                .map(MybatisPlusAclRepository::toUserVO)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<AclUserVO> findUserById(String id) {
        RmqAclUser entity = userMapper.selectById(id);
        return Optional.ofNullable(entity).map(MybatisPlusAclRepository::toUserVO);
    }

    @Override
    public AclUserVO saveUser(AclUserVO user) {
        RmqAclUser entity = toUserEntity(user);
        if (userMapper.selectById(entity.getId()) != null) {
            userMapper.updateById(entity);
        } else {
            userMapper.insert(entity);
        }
        return user;
    }

    @Override
    public void deleteUser(String id) {
        userMapper.deleteById(id);
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
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private static RmqAclRule toRuleEntity(AclRuleVO rule) {
        RmqAclRule entity = new RmqAclRule();
        entity.setId(rule.getId());
        entity.setPrincipal(rule.getPrincipal());
        entity.setResource(rule.getResource());
        entity.setResourceType(rule.getResourceType());
        entity.setResourcePattern(rule.getResourcePattern());
        entity.setActions(rule.getActions() == null ? null : String.join(",", rule.getActions()));
        entity.setDecision(rule.getDecision());
        entity.setScope(rule.getScope());
        entity.setAclVersion(rule.getAclVersion());
        entity.setCreatedAt(rule.getCreatedAt());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private static AclUserVO toUserVO(RmqAclUser entity) {
        return AclUserVO.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .accessKey(entity.getAccessKey())
                .secretKey(decodeBase64(entity.getSecretKey()))
                .admin(Boolean.TRUE.equals(entity.getAdmin()))
                .clusters(splitCsv(entity.getClusters()))
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private static RmqAclUser toUserEntity(AclUserVO user) {
        RmqAclUser entity = new RmqAclUser();
        entity.setId(user.getId());
        entity.setUsername(user.getUsername());
        entity.setAccessKey(user.getAccessKey());
        entity.setSecretKey(encodeBase64(user.getSecretKey()));
        entity.setAdmin(user.isAdmin());
        entity.setClusters(user.getClusters() == null ? null : String.join(",", user.getClusters()));
        entity.setCreatedAt(user.getCreatedAt());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.toList());
    }

    private static String encodeBase64(String plainText) {
        if (plainText == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(plainText.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeBase64(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            return new String(Base64.getDecoder().decode(stored), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            // tolerate legacy values that were stored without encoding
            return stored;
        }
    }
}
