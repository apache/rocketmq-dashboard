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
package org.apache.rocketmq.studio.provider.tencent;

import com.tencentcloudapi.trocket.v20230308.models.CreateRoleRequest;
import com.tencentcloudapi.trocket.v20230308.models.DeleteRoleRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeRoleListRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeRoleListResponse;
import com.tencentcloudapi.trocket.v20230308.models.ModifyRoleRequest;
import com.tencentcloudapi.trocket.v20230308.models.RoleItem;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.instance.acl.AclRuleVO;
import org.apache.rocketmq.studio.instance.acl.AclUserVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps Tencent Cloud TDMQ RocketMQ 5.x role management (DescribeRoleList / CreateRole /
 * ModifyRole / DeleteRole) onto the dashboard's ACL user / rule models.
 *
 * <p>Per the product contract, a Tencent role maps to a single cluster-wide ACL rule whose
 * principal is the role name, resource is {@code *}, and actions are derived from
 * {@code PermRead} (SUB) / {@code PermWrite} (PUB). The resulting rule is version {@code 1.0}
 * and scoped to the whole cluster.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TencentAclService {

    static final int PAGE_SIZE = 100;
    static final int MAX_PAGES = 100;
    static final String ACL_VERSION = "1.0";
    static final String RESOURCE_TYPE = "Cluster";
    static final String RESOURCE = "*";
    static final String RESOURCE_PATTERN = "LITERAL";
    static final String SCOPE = "cluster";
    static final String DECISION = "ALLOW";

    private final TencentClientFactory clientFactory;
    private final InstanceRepository instanceRepository;

    public List<AclUserVO> listUsers(String instanceId) {
        Context context = resolve(instanceId);
        List<AclUserVO> users = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            DescribeRoleListRequest request = new DescribeRoleListRequest();
            request.setInstanceId(context.cloudInstanceId());
            request.setOffset((long) page * PAGE_SIZE);
            request.setLimit((long) PAGE_SIZE);
            DescribeRoleListResponse response = clientFactory.call(context.credentialId(),
                    context.regionId(), client -> client.DescribeRoleList(request));
            RoleItem[] data = response == null ? null : response.getData();
            if (data == null || data.length == 0) {
                break;
            }
            for (RoleItem role : data) {
                if (role != null) {
                    users.add(toUser(role, context.cloudInstanceId()));
                }
            }
            if (data.length < PAGE_SIZE) {
                break;
            }
        }
        return users;
    }

    public List<AclRuleVO> listRules(String instanceId, String principal) {
        Context context = resolve(instanceId);
        List<AclRuleVO> rules = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            DescribeRoleListRequest request = new DescribeRoleListRequest();
            request.setInstanceId(context.cloudInstanceId());
            request.setOffset((long) page * PAGE_SIZE);
            request.setLimit((long) PAGE_SIZE);
            DescribeRoleListResponse response = clientFactory.call(context.credentialId(),
                    context.regionId(), client -> client.DescribeRoleList(request));
            RoleItem[] data = response == null ? null : response.getData();
            if (data == null || data.length == 0) {
                break;
            }
            for (RoleItem role : data) {
                if (role == null) {
                    continue;
                }
                if (StringUtils.hasText(principal)
                        && !principal.equals(role.getRoleName())) {
                    continue;
                }
                rules.add(toRule(role));
            }
            if (data.length < PAGE_SIZE) {
                break;
            }
        }
        return rules;
    }

    public AclUserVO createUser(String instanceId, AclUserVO user) {
        Context context = resolve(instanceId);
        if (!StringUtils.hasText(user.getUsername())) {
            throw new BusinessException(400, "ACL username is required");
        }
        CreateRoleRequest request = new CreateRoleRequest();
        request.setInstanceId(context.cloudInstanceId());
        request.setRole(user.getUsername());
        request.setPermRead(user.getPermRead() == null || user.getPermRead());
        request.setPermWrite(user.getPermWrite() == null || user.getPermWrite());
        request.setRemark(user.getUsername());
        clientFactory.call(context.credentialId(), context.regionId(),
                client -> client.CreateRole(request));
        // The created role is not returned by the API; reconstruct from the known inputs.
        return AclUserVO.builder()
                .id(user.getUsername())
                .username(user.getUsername())
                .accessKey(null)
                .secretKey(null)
                .admin(false)
                .permRead(user.getPermRead() == null || user.getPermRead())
                .permWrite(user.getPermWrite() == null || user.getPermWrite())
                .clusters(context.cloudInstanceId() == null ? List.of() : List.of(context.cloudInstanceId()))
                .createdAt(LocalDateTime.now())
                .build();
    }

    public AclUserVO updateUser(String instanceId, AclUserVO user) {
        Context context = resolve(instanceId);
        String roleName = StringUtils.hasText(user.getId()) ? user.getId() : user.getUsername();
        if (!StringUtils.hasText(roleName)) {
            throw new BusinessException(400, "ACL user id is required");
        }
        RoleItem existing = user.getPermRead() == null || user.getPermWrite() == null
                ? findRole(context, roleName) : null;
        boolean permRead = user.getPermRead() == null
                ? Boolean.TRUE.equals(existing.getPermRead()) : user.getPermRead();
        boolean permWrite = user.getPermWrite() == null
                ? Boolean.TRUE.equals(existing.getPermWrite()) : user.getPermWrite();
        ModifyRoleRequest request = new ModifyRoleRequest();
        request.setInstanceId(context.cloudInstanceId());
        request.setRole(roleName);
        request.setPermRead(permRead);
        request.setPermWrite(permWrite);
        clientFactory.call(context.credentialId(), context.regionId(),
                client -> client.ModifyRole(request));
        return AclUserVO.builder()
                .id(roleName)
                .username(roleName)
                .accessKey(null)
                .secretKey(null)
                .admin(false)
                .permRead(permRead)
                .permWrite(permWrite)
                .clusters(context.cloudInstanceId() == null ? List.of() : List.of(context.cloudInstanceId()))
                .createdAt(user.getCreatedAt())
                .build();
    }

    private RoleItem findRole(Context context, String roleName) {
        for (int page = 0; page < MAX_PAGES; page++) {
            DescribeRoleListRequest request = new DescribeRoleListRequest();
            request.setInstanceId(context.cloudInstanceId());
            request.setOffset((long) page * PAGE_SIZE);
            request.setLimit((long) PAGE_SIZE);
            DescribeRoleListResponse response = clientFactory.call(context.credentialId(),
                    context.regionId(), client -> client.DescribeRoleList(request));
            RoleItem[] data = response == null ? null : response.getData();
            if (data == null || data.length == 0) {
                break;
            }
            for (RoleItem role : data) {
                if (role != null && roleName.equals(role.getRoleName())) {
                    return role;
                }
            }
            if (data.length < PAGE_SIZE) {
                break;
            }
        }
        throw new BusinessException(404, "ACL user not found: " + roleName);
    }

    public void deleteUser(String instanceId, String username) {
        Context context = resolve(instanceId);
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(400, "ACL username is required");
        }
        DeleteRoleRequest request = new DeleteRoleRequest();
        request.setInstanceId(context.cloudInstanceId());
        request.setRole(username);
        clientFactory.call(context.credentialId(), context.regionId(),
                client -> client.DeleteRole(request));
    }

    /**
     * Applies a cluster-wide rule to a Tencent role by translating the rule's actions into the
     * role's PermRead / PermWrite flags. The rule principal is the role name and the resource
     * must be {@code *}; other resource patterns are not representable on Tencent roles.
     */
    public AclRuleVO createRule(String instanceId, AclRuleVO rule) {
        Context context = resolve(instanceId);
        requireRulePrincipal(rule);
        boolean permRead = hasAction(rule, "SUB");
        boolean permWrite = hasAction(rule, "PUB");
        ModifyRoleRequest request = new ModifyRoleRequest();
        request.setInstanceId(context.cloudInstanceId());
        request.setRole(rule.getPrincipal());
        request.setPermRead(permRead);
        request.setPermWrite(permWrite);
        clientFactory.call(context.credentialId(), context.regionId(),
                client -> client.ModifyRole(request));
        return toRule(rule.getPrincipal(), permRead, permWrite);
    }

    public AclRuleVO updateRule(String instanceId, AclRuleVO rule) {
        return createRule(instanceId, rule);
    }

    public void deleteRule(String instanceId, String principal) {
        Context context = resolve(instanceId);
        if (!StringUtils.hasText(principal)) {
            throw new BusinessException(400, "ACL principal is required");
        }
        DeleteRoleRequest request = new DeleteRoleRequest();
        request.setInstanceId(context.cloudInstanceId());
        request.setRole(principal);
        clientFactory.call(context.credentialId(), context.regionId(),
                client -> client.DeleteRole(request));
    }

    private static void requireRulePrincipal(AclRuleVO rule) {
        if (rule == null || !StringUtils.hasText(rule.getPrincipal())) {
            throw new BusinessException(400, "ACL principal is required");
        }
    }

    private static boolean hasAction(AclRuleVO rule, String action) {
        return rule.getActions() != null && rule.getActions().stream()
                .anyMatch(a -> action.equalsIgnoreCase(a));
    }

    private static AclRuleVO toRule(String principal, boolean permRead, boolean permWrite) {
        List<String> actions = new ArrayList<>();
        if (permWrite) {
            actions.add("PUB");
        }
        if (permRead) {
            actions.add("SUB");
        }
        return AclRuleVO.builder()
                .id(principal)
                .principal(principal)
                .resource(RESOURCE)
                .resourceType(RESOURCE_TYPE)
                .resourcePattern(RESOURCE_PATTERN)
                .actions(actions)
                .decision(DECISION)
                .scope(SCOPE)
                .aclVersion(ACL_VERSION)
                .build();
    }

    /**
     * Returns the plain-text credentials of a Tencent role. The role's AccessKey/SecretKey are
     * available from DescribeRoleList, so re-fetch and match by role name.
     */
    public AclUserVO getUserCredentials(String instanceId, String username) {
        return listUsers(instanceId).stream()
                .filter(user -> user.getUsername().equals(username))
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "ACL user not found: " + username));
    }

    private Context resolve(String instanceId) {
        if (!StringUtils.hasText(instanceId)) {
            throw new BusinessException(400, "instanceId is required");
        }
        InstanceVO instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new BusinessException(404, "Instance not found: " + instanceId));
        if (!StringUtils.hasText(instance.getCloudInstanceId()) || !StringUtils.hasText(instance.getRegionId())
                || !StringUtils.hasText(instance.getCredentialId())) {
            throw new BusinessException(400, "Instance " + instanceId + " is missing Tencent Cloud binding");
        }
        return new Context(instance.getCloudInstanceId(), instance.getRegionId(), instance.getCredentialId());
    }

    private static AclUserVO toUser(RoleItem role, String cloudInstanceId) {
        return AclUserVO.builder()
                .id(role.getRoleName())
                .username(role.getRoleName())
                .accessKey(role.getAccessKey())
                .secretKey(role.getSecretKey())
                .admin(false)
                .permRead(role.getPermRead())
                .permWrite(role.getPermWrite())
                .clusters(cloudInstanceId == null ? List.of() : List.of(cloudInstanceId))
                .whiteRemoteAddress(null)
                .createdAt(toLocalDateTime(role.getCreatedTime()))
                .build();
    }

    private static AclRuleVO toRule(RoleItem role) {
        List<String> actions = new ArrayList<>();
        if (Boolean.TRUE.equals(role.getPermWrite())) {
            actions.add("PUB");
        }
        if (Boolean.TRUE.equals(role.getPermRead())) {
            actions.add("SUB");
        }
        return AclRuleVO.builder()
                .id(role.getRoleName())
                .principal(role.getRoleName())
                .resource(RESOURCE)
                .resourceType(RESOURCE_TYPE)
                .resourcePattern(RESOURCE_PATTERN)
                .actions(actions)
                .decision(DECISION)
                .scope(SCOPE)
                .aclVersion(ACL_VERSION)
                .createdAt(toLocalDateTime(role.getCreatedTime()))
                .build();
    }

    private static LocalDateTime toLocalDateTime(Long epoch) {
        if (epoch == null || epoch <= 0L) {
            return null;
        }
        long epochMillis = epoch >= 10_000_000_000L ? epoch : epoch * 1000L;
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private record Context(String cloudInstanceId, String regionId, String credentialId) {
    }
}
