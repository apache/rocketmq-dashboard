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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class InMemoryAclRepository implements AclRepository {

    private final Map<String, AclRuleVO> rules = new ConcurrentHashMap<>();
    private final Map<String, AclUserVO> users = new ConcurrentHashMap<>();

    @Override
    public List<AclRuleVO> findRules(String clusterId, String principal) {
        return rules.values().stream()
                .filter(r -> clusterId == null || clusterId.equals(r.getScope()))
                .filter(r -> principal == null || principal.equals(r.getPrincipal()))
                .collect(Collectors.toList());
    }

    @Override
    public AclRuleVO saveRule(AclRuleVO rule) {
        rules.put(rule.getId(), rule);
        log.debug("Saved ACL rule id={}", rule.getId());
        return rule;
    }

    @Override
    public Optional<AclRuleVO> replaceRule(AclRuleVO rule) {
        String id = rule.getId();
        AclRuleVO replacedRule = rules.computeIfPresent(id,
                (key, existingRule) -> copyRule(rule, key, existingRule.getCreatedAt()));
        log.debug("Replaced ACL rule id={}, replaced={}", id, replacedRule != null);
        return Optional.ofNullable(replacedRule);
    }

    private AclRuleVO copyRule(AclRuleVO rule, String id, LocalDateTime createdAt) {
        return AclRuleVO.builder()
                .id(id)
                .principal(rule.getPrincipal())
                .resource(rule.getResource())
                .resourceType(rule.getResourceType())
                .resourcePattern(rule.getResourcePattern())
                .actions(rule.getActions() == null ? null : new ArrayList<>(rule.getActions()))
                .decision(rule.getDecision())
                .scope(rule.getScope())
                .aclVersion(rule.getAclVersion())
                .createdAt(createdAt)
                .build();
    }

    @Override
    public void deleteRule(String id) {
        rules.remove(id);
        log.debug("Deleted ACL rule id={}", id);
    }

    @Override
    public List<AclUserVO> findUsers() {
        return new ArrayList<>(users.values());
    }

    @Override
    public Optional<AclUserVO> findUserById(String id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public AclUserVO saveUser(AclUserVO user) {
        users.put(user.getId(), user);
        log.debug("Saved ACL user id={}", user.getId());
        return user;
    }

    @Override
    public void deleteUser(String id) {
        users.remove(id);
        log.debug("Deleted ACL user id={}", id);
    }
}
