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
package com.rocketmq.studio.instance.acl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    public void deleteRule(String id) {
        rules.remove(id);
        log.debug("Deleted ACL rule id={}", id);
    }

    @Override
    public List<AclUserVO> findUsers() {
        return new ArrayList<>(users.values());
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
