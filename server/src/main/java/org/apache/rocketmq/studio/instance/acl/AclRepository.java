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

import org.apache.rocketmq.studio.common.domain.PageResult;

import java.util.List;
import java.util.Optional;

public interface AclRepository {
    PageResult<AclRuleVO> findRulePage(String principal, String resource, String scope,
            String decision, String aclVersion, int page, int pageSize);

    AclRuleVO saveRule(AclRuleVO rule);

    /**
     * Replaces an existing rule atomically without creating a missing rule.
     * Implementations must preserve the original creation timestamp.
     */
    Optional<AclRuleVO> replaceRule(AclRuleVO rule);

    boolean deleteRule(Long id);

    List<AclUserVO> findUsers();

    PageResult<AclUserVO> findUserPage(String keyword, int page, int pageSize);

    Optional<AclUserVO> findUserById(Long id);

    AclUserVO saveUser(AclUserVO user);

    /**
     * Replaces an existing user atomically without creating a missing user.
     * Returns empty when the user no longer exists.
     */
    Optional<AclUserVO> replaceUser(AclUserVO user);

    boolean deleteUser(Long id);

    /**
     * Examines the effective ACL configuration of a broker cluster: the enabled
     * flag, ACL version, the global IP whitelist and the list of plain access
     * accounts provisioned for the cluster. Reads from the MySQL-backed
     * {@code rmq_acl_user} / {@code rmq_acl_rule} tables.
     */
    AclClusterConfigVO examineBrokerClusterAclConfig(String clusterId);

    /**
     * Creates a new plain access account or updates an existing one (keyed by
     * access key). Persists the account identity to {@code rmq_acl_user} and the
     * per-resource permissions to {@code rmq_acl_rule}.
     */
    PlainAccessConfigVO createAndUpdatePlainAccessConfig(PlainAccessConfigVO config);
}
