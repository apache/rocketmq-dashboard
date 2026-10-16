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

import org.springframework.util.StringUtils;

import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.remoting.protocol.body.AclInfo;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.UserInfo;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.util.Pagination;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.util.CredentialUtils;
import org.apache.rocketmq.studio.common.util.EntityIds;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.broker.BrokerVO;
import org.apache.rocketmq.studio.cluster.broker.ClusterProvider;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.model.Acl2PolicyContext;
import org.apache.rocketmq.studio.instance.InstanceResolver;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.provider.tencent.TencentAclService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class AclService {

    private static final SecureRandom CREDENTIAL_RANDOM = new SecureRandom();
    private static final int DEFAULT_RULE_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    /** Broker ACL 2.0 subjects are prefixed with {@code User:}; the store uses {@code user:}. */
    private static final String SUBJECT_PREFIX = "User:";
    private static final String USER_TYPE_SUPER = "Super";
    private static final String USER_TYPE_NORMAL = "Normal";
    private static final String USER_STATUS_NORMAL = "Normal";
    private static final String POLICY_TYPE_CUSTOM = "Custom";
    private static final int MAX_RULES_PER_ACCOUNT = 200;

    /**
     * Minimum broker version that supports ACL 2.0 (the RocketMQ {@code auth} module with
     * {@code authenticationEnabled}/{@code authorizationEnabled}). Below this threshold the ACL
     * tools return an upgrade hint instead of failing silently.
     */
    private static final int[] MIN_ACL2_BROKER_VERSION = {5, 3, 0};

    private final AclRepository aclRepository;
    private final OperationAuditService operationAuditService;
    private final InstanceResolver instanceResolver;
    private final TencentAclService tencentAclService;
    private final ClusterProvider clusterProvider;
    private final RuntimeAdminClientResolver runtimeAdminClientResolver;

    public AclCapabilitiesVO capabilities(String instanceId) {
        if (!StringUtils.hasText(instanceId)) {
            throw new BusinessException(400, "instanceId is required");
        }
        InstanceVO instance = instanceResolver.findByIdentifier(instanceId)
                .orElseThrow(() -> new BusinessException(404, "Instance not found: " + instanceId));
        if (instance.getVendor() == InstanceVendor.TENCENT) {
            return new AclCapabilitiesVO(instance.getId(), instance.getVendor(), instance.getType(),
                    "TENCENT_ROLE", true, true);
        }
        boolean apacheInstance = instance.getVendor() == null || instance.getVendor() == InstanceVendor.APACHE;
        return new AclCapabilitiesVO(instance.getId(), instance.getVendor(), instance.getType(),
                apacheInstance ? "APACHE_ACL2" : "STUDIO_LOCAL", apacheInstance, false);
    }


    public PageResult<AclRuleVO> listRules(String principal, String resource, String scope, String decision,
            String instanceId, Integer page, Integer pageSize) {
        int normalizedPage = requireValidPage(page);
        int normalizedPageSize = requireValidPageSize(pageSize);
        requireAcl2Supported(instanceId);
        if (isTencentInstance(instanceId)) {
            List<AclRuleVO> filtered = tencentAclService.listRules(instanceId, principal).stream()
                    .filter(rule -> containsIgnoreCase(rule.getResource(), resource))
                    .filter(rule -> equalsIgnoreCase(rule.getScope(), scope))
                    .filter(rule -> equalsIgnoreCase(rule.getDecision(), decision))
                    .toList();
            return toUtcWire(paginateRules(filtered, normalizedPage, normalizedPageSize));
        }
        log.info("Listing ACL rules for principal={}, resource={}, scope={}, decision={}, page={}, pageSize={}",
                principal, resource, scope, decision, normalizedPage, normalizedPageSize);
        return toUtcWire(aclRepository.findRulePage(principal, resource, scope, decision, null,
                normalizedPage, normalizedPageSize));
    }


    public AclRuleVO createRule(AclRuleVO rule, String instanceId) {
        requireAcl2Supported(instanceId);
        if (isTencentInstance(instanceId)) {
            return tencentAclService.createRule(instanceId, rule);
        }
        log.info("Creating ACL rule for principal={}", rule.getPrincipal());
        if (!StringUtils.hasText(rule.getPrincipal())) {
            throw new BusinessException(400, "ACL principal is required");
        }
        if (!StringUtils.hasText(rule.getResource())) {
            throw new BusinessException(400, "ACL resource is required");
        }
        rule.setGmtCreate(LocalDateTime.now());
        AclRuleVO saved = aclRepository.saveRule(rule);
        auditRule("CREATE_ACL_RULE", saved);
        // Write paths echo the stored row (the stored server-local base) for form prefill; the
        // read paths are what the pages render, so only they convert to the UTC wire.
        return saved;
    }

    public AclRuleVO getRule(String id, String instanceId) {
        if (!StringUtils.hasText(id)) {
            throw new BusinessException(400, "ACL rule id is required");
        }
        if (isTencentInstance(instanceId)) {
            return toUtcWire(tencentAclService.listRules(instanceId, null).stream()
                    .filter(rule -> id.equals(rule.getPrincipal())
                            || rule.getId() != null && id.equals(rule.getId().toString()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(404, "ACL rule not found: " + id)));
        }
        return toUtcWire(aclRepository.findRuleById(EntityIds.parseId(id))
                .orElseThrow(() -> new BusinessException(404, "ACL rule not found: " + id)));
    }

    public AclUserVO getUser(String id, String instanceId) {
        if (!StringUtils.hasText(id)) {
            throw new BusinessException(400, "ACL user id is required");
        }
        return listUsers(instanceId).stream()
                .filter(user -> id.equals(user.getUsername())
                        || user.getId() != null && id.equals(user.getId().toString()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "ACL user not found: " + id));
    }

    public AclRuleVO updateRule(AclRuleVO rule, String instanceId) {
        requireAcl2Supported(instanceId);
        if (isTencentInstance(instanceId)) {
            return tencentAclService.updateRule(instanceId, rule);
        }
        if (rule.getId() == null) {
            throw new BusinessException(400, "ACL rule id is required");
        }
        log.info("Updating ACL rule id={}, principal={}", rule.getId(), rule.getPrincipal());
        AclRuleVO saved = aclRepository.replaceRule(rule)
                .orElseThrow(() -> new BusinessException(404, "ACL rule not found: " + rule.getId()));
        auditRule("UPDATE_ACL_RULE", saved);
        return saved;
    }

    public void deleteRule(String id, String instanceId) {
        requireAcl2Supported(instanceId);
        if (isTencentInstance(instanceId)) {
            tencentAclService.deleteRule(instanceId, id);
            return;
        }
        log.info("Deleting ACL rule id={}", id);
        if (!aclRepository.deleteRule(EntityIds.parseId(id))) {
            throw new BusinessException(404, "ACL rule not found: " + id);
        }
        recordAudit("DELETE_ACL_RULE", "ACL_RULE", id, null, null);
    }


    public List<AclUserVO> listUsers(String instanceId) {
        if (isTencentInstance(instanceId)) {
            return tencentAclService.listUsers(instanceId).stream()
                    .map(this::forWire)
                    .toList();
        }
        log.info("Listing ACL users");
        return aclRepository.findUsers().stream()
                .map(this::forWire)
                .toList();
    }

    public PageResult<AclUserVO> pageUsers(String instanceId, int page, int pageSize, String keyword) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(400, "page must be >= 1 and pageSize must be between 1 and 100");
        }
        if (isTencentInstance(instanceId)) {
            String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
            List<AclUserVO> users = tencentAclService.listUsers(instanceId).stream()
                .filter(user -> query.isEmpty()
                        || containsIgnoreCase(user.getUsername(), query)
                        || containsIgnoreCase(user.getAccessKey(), query))
                .sorted(Comparator.comparing(AclUserVO::getGmtCreate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AclUserVO::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
            int from = (int) Math.min(Pagination.pageOffset(page, pageSize), users.size());
            int to = Math.min(from + pageSize, users.size());
            return PageResult.of(
                    users.subList(from, to).stream().map(this::forWire).toList(),
                    users.size(), page, pageSize);
        }
        PageResult<AclUserVO> result = aclRepository.findUserPage(
                StringUtils.hasText(keyword) ? keyword.trim() : null, page, pageSize);
        return PageResult.of(result.getItems().stream().map(this::forWire).toList(),
                result.getTotal(), result.getPage(), result.getSize());
    }


    public AclUserVO createUser(AclUserVO user, String instanceId) {
        requireAcl2Supported(instanceId);
        if (isTencentInstance(instanceId)) {
            return tencentAclService.createUser(instanceId, user);
        }
        log.info("Creating ACL user username={}", user.getUsername());
        if (!StringUtils.hasText(user.getUsername())) {
            throw new BusinessException(400, "ACL username is required");
        }
        user.setAccessKey(randomCredentialToken());
        user.setSecretKey(randomCredentialToken());
        user.setGmtCreate(LocalDateTime.now());
        AclUserVO saved = aclRepository.saveUser(user);
        auditUser("CREATE_ACL_USER", saved);
        // Write paths echo the stored row (the stored server-local base); only the read paths
        // that the pages render convert to the UTC wire.
        return saved;
    }

    public AclUserVO updateUser(UpdateAclUserDTO user, String instanceId) {
        requireAcl2Supported(instanceId);
        if (isTencentInstance(instanceId)) {
            return tencentAclService.updateUser(instanceId, user.toAclUserVO());
        }
        if (!StringUtils.hasText(user.getId())) {
            throw new BusinessException(400, "ACL user id is required");
        }
        Long userId = EntityIds.parseId(user.getId());
        log.info("Updating ACL user id={}, username={}", userId, user.getUsername());
        AclUserVO existing = aclRepository.findUserById(userId)
                .orElseThrow(() -> new BusinessException(404, "ACL user not found: " + user.getId()));
        if (user.getUsername() != null && !StringUtils.hasText(user.getUsername())) {
            throw new BusinessException(400, "ACL username is required");
        }
        AclUserVO merged = AclUserVO.builder()
                .id(existing.getId())
                .username(user.getUsername() == null ? existing.getUsername() : user.getUsername())
                .accessKey(existing.getAccessKey())
                .secretKey(existing.getSecretKey())
                .admin(user.getAdmin() == null ? existing.isAdmin() : user.getAdmin())
                .clusters(user.getClusters() == null ? existing.getClusters() : user.getClusters())
                .whiteRemoteAddress(existing.getWhiteRemoteAddress())
                .gmtCreate(existing.getGmtCreate())
                .build();
        AclUserVO saved = aclRepository.replaceUser(merged)
                .orElseThrow(() -> new BusinessException(404, "ACL user not found: " + user.getId()));
        auditUser("UPDATE_ACL_USER", saved);
        return maskCredentials(saved);
    }

    public void deleteUser(String id, String instanceId) {
        requireAcl2Supported(instanceId);
        if (isTencentInstance(instanceId)) {
            // For Tencent roles the id is the role name.
            tencentAclService.deleteUser(instanceId, id);
            return;
        }
        log.info("Deleting ACL user id={}", id);
        if (!aclRepository.deleteUser(EntityIds.parseId(id))) {
            throw new BusinessException(404, "ACL user not found: " + id);
        }
        recordAudit("DELETE_ACL_USER", "ACL_USER", id, null, null);
    }

    /**
     * Returns a store-level summary of the ACL accounts provisioned for the cluster from the
     * MySQL-backed store. This does not query live broker state; the {@code clusterId} only
     * scopes which stored accounts are included.
     */
    public AclClusterConfigVO examineBrokerClusterAclConfig(String clusterId) {
        if (!StringUtils.hasText(clusterId)) {
            throw new BusinessException(400, "clusterId is required");
        }
        log.info("Examining broker cluster ACL config for clusterId={}", clusterId);
        return aclRepository.examineBrokerClusterAclConfig(clusterId);
    }

    /**
     * Creates a new plain access account or updates an existing one. The account identity is
     * persisted to {@code rmq_acl_user} (including the IP whitelist) and the per-resource
     * permissions to {@code rmq_acl_rule} via the MySQL-backed repository. The user row and the
     * rule replacement happen in one transaction; a blank secret on an existing account keeps
     * the stored secret unchanged.
     */
    public PlainAccessConfigVO createAndUpdatePlainAccessConfig(PlainAccessConfigVO config) {
        if (config == null || !StringUtils.hasText(config.getAccessKey())) {
            throw new BusinessException(400, "accessKey is required");
        }
        if (config.getWhiteRemoteAddress() != null) {
            String normalizedWhiteRemoteAddress = config.getWhiteRemoteAddress().trim();
            config.setWhiteRemoteAddress(normalizedWhiteRemoteAddress.isEmpty()
                    ? null : normalizedWhiteRemoteAddress);
        }
        if (StringUtils.hasText(config.getWhiteRemoteAddress())
                && !PlainAclRemoteAddressValidator.isValid(config.getWhiteRemoteAddress())) {
            throw new BusinessException(400,
                    "whiteRemoteAddress is not a valid plain ACL address expression: "
                            + config.getWhiteRemoteAddress());
        }
        log.info("Creating/updating plain access config accessKey={}", config.getAccessKey());
        PlainAccessConfigVO saved = aclRepository.createAndUpdatePlainAccessConfig(config);
        String auditDetail = "admin=" + Boolean.TRUE.equals(saved.getAdmin())
                + ", whiteRemoteAddressConfigured="
                + StringUtils.hasText(saved.getWhiteRemoteAddress());
        recordAudit("UPSERT_PLAIN_ACCESS_CONFIG", "ACL_USER", saved.getAccessKey(), null,
                auditDetail);
        return saved;
    }

    /**
     * Pushes every stored ACL account to the master brokers of an Apache instance through the
     * broker's ACL 2.0 admin API (account credentials via createUser/updateUser, policies via
     * updateAcl). The push is additive: each account is upserted under its {@code User:<username>}
     * subject and accounts the dashboard does not know about stay on the broker. Rules the broker
     * model cannot express (non-literal resource patterns, actions outside PUB/SUB/GET) are
     * reported per account instead of failing the whole push, and every account/broker pair keeps
     * its own outcome so one unreachable broker cannot hide the successes.
     */
    public PushAclToBrokerResultVO pushAclToBroker(String instanceId) {
        if (!StringUtils.hasText(instanceId)) {
            throw new BusinessException(400, "instanceId is required");
        }
        requireAcl2Supported(instanceId);
        if (isTencentInstance(instanceId)) {
            throw new BusinessException(400, "Broker push is only supported for Apache instances");
        }
        List<PushPlan> plans = new ArrayList<>();
        for (AclUserVO user : aclRepository.findUsers()) {
            AclUserVO credentials = getUserCredentials(String.valueOf(user.getId()), instanceId);
            List<AclRuleVO> rules = aclRepository.findRulePage(
                    principalOf(user.getUsername()), null, null, null, null, 1,
                    MAX_RULES_PER_ACCOUNT).getItems();
            plans.add(buildPushPlan(user, credentials, rules));
        }
        log.info("Pushing {} ACL account(s) of instance {} to the brokers", plans.size(), instanceId);
        return runtimeAdminClientResolver.execute(instanceId, admin -> {
            DefaultMQAdminExt adminExt = (DefaultMQAdminExt) admin;
            List<String> masters = masterAddresses(adminExt, instanceId);
            if (!masters.isEmpty() && !acl2Enabled(adminExt, masters.get(0))) {
                throw new BusinessException(409, "Broker ACL 2.0 is not enabled on "
                        + masters.get(0) + "; set authenticationEnabled=true and "
                        + "authorizationEnabled=true in the broker configuration before pushing");
            }
            List<PushAclToBrokerResultVO.AccountOutcome> outcomes = new ArrayList<>();
            int pushed = 0;
            int failed = 0;
            for (PushPlan plan : plans) {
                List<PushAclToBrokerResultVO.BrokerOutcome> brokerOutcomes = new ArrayList<>();
                boolean anySuccess = false;
                for (String broker : masters) {
                    try {
                        upsertAccount(adminExt, broker, plan);
                        brokerOutcomes.add(PushAclToBrokerResultVO.BrokerOutcome.builder()
                                .broker(broker).success(true).build());
                        anySuccess = true;
                    } catch (Exception ex) {
                        brokerOutcomes.add(PushAclToBrokerResultVO.BrokerOutcome.builder()
                                .broker(broker).success(false).error(rootMessage(ex)).build());
                    }
                }
                if (anySuccess) {
                    pushed++;
                } else {
                    failed++;
                }
                recordAudit("PUSH_ACL_TO_BROKER", "ACL_USER", plan.username(), null,
                        "brokers=" + masters.size() + ", policies=" + plan.entries().size()
                                + ", success=" + anySuccess);
                outcomes.add(PushAclToBrokerResultVO.AccountOutcome.builder()
                        .username(plan.username())
                        .subject(plan.subject())
                        .policyCount(plan.entries().size())
                        .skipped(plan.skipped())
                        .brokers(brokerOutcomes)
                        .build());
            }
            return PushAclToBrokerResultVO.builder()
                    .instanceId(instanceId)
                    .brokers(masters)
                    .pushed(pushed)
                    .failed(failed)
                    .accounts(outcomes)
                    .build();
        });
    }

    private record PushPlan(String username, String subject, UserInfo userInfo,
                            List<AclInfo.PolicyEntryInfo> entries, List<String> skipped) {
    }

    private PushPlan buildPushPlan(AclUserVO user, AclUserVO credentials, List<AclRuleVO> rules) {
        List<AclInfo.PolicyEntryInfo> entries = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (AclRuleVO rule : rules) {
            String pattern = rule.getResourcePattern() == null ? "" : rule.getResourcePattern();
            if (!"LITERAL".equalsIgnoreCase(pattern)) {
                skipped.add(rule.getResource() + ": " + pattern + " pattern has no broker ACL equivalent");
                continue;
            }
            List<String> actions = mapAclActions(rule.getActions());
            if (actions.isEmpty()) {
                skipped.add(rule.getResource() + ": none of its actions map to broker PUB/SUB/GET");
                continue;
            }
            String resource = brokerResource(rule);
            if (resource == null) {
                // A rule without a resource type does not say whether its resource names a topic
                // or a group. Pushing it as the ANY resource would widen a named grant to the
                // whole cluster, so it is skipped with a reason like every other rule the broker
                // model cannot express.
                skipped.add(rule.getResource() + ": resource type "
                        + (rule.getResourceType() == null ? "" : rule.getResourceType())
                        + " has no broker ACL equivalent");
                continue;
            }
            entries.add(AclInfo.PolicyEntryInfo.of(resource, actions,
                    splitWhiteList(user.getWhiteRemoteAddress()),
                    (rule.getDecision() == null ? "" : rule.getDecision()).toUpperCase(Locale.ROOT)));
        }
        UserInfo userInfo = UserInfo.of(user.getUsername(), credentials.getSecretKey(),
                user.isAdmin() ? USER_TYPE_SUPER : USER_TYPE_NORMAL, USER_STATUS_NORMAL);
        return new PushPlan(user.getUsername(), SUBJECT_PREFIX + user.getUsername(), userInfo,
                entries, skipped);
    }

    private void upsertAccount(DefaultMQAdminExt adminExt, String broker, PushPlan plan)
            throws Exception {
        UserInfo existing = null;
        try {
            existing = adminExt.getUser(broker, plan.userInfo().getUsername());
        } catch (Exception notPresent) {
            // A broker without the account answers with an error rather than an empty result.
            log.debug("No existing broker account {} on {}: {}", plan.username(), broker,
                    rootMessage(notPresent));
        }
        if (existing == null) {
            adminExt.createUser(broker, plan.userInfo());
        } else {
            adminExt.updateUser(broker, plan.userInfo());
        }
        if (plan.entries().isEmpty()) {
            return;
        }
        AclInfo acl = new AclInfo();
        acl.setSubject(plan.subject());
        AclInfo.PolicyInfo policy = new AclInfo.PolicyInfo();
        policy.setPolicyType(POLICY_TYPE_CUSTOM);
        policy.setEntries(plan.entries());
        acl.setPolicies(List.of(policy));
        adminExt.updateAcl(broker, acl);
    }

    /**
     * ACL 2.0 writes fail on the broker with an opaque "system error" when the auth module is off,
     * so read the two switches first and answer with an actionable message instead. When the config
     * cannot be read the check stays out of the way and lets the write report its own error.
     */
    private boolean acl2Enabled(DefaultMQAdminExt adminExt, String broker) {
        try {
            Properties config = adminExt.getBrokerConfig(broker);
            return Boolean.parseBoolean(config.getProperty("authenticationEnabled"))
                    && Boolean.parseBoolean(config.getProperty("authorizationEnabled"));
        } catch (Exception unreadable) {
            log.debug("Could not read ACL switches from broker {}: {}", broker,
                    rootMessage(unreadable));
            return true;
        }
    }

    /**
     * The master brokers this push may write to. The instance's configured cluster decides the set,
     * through the same scope the ACL read uses ({@link AclBrokerScope}): a NameServer shared by
     * several clusters must not receive this instance's accounts and policies on the clusters it is
     * not bound to. An instance that names no cluster keeps every master of the endpoint.
     */
    private List<String> masterAddresses(DefaultMQAdminExt adminExt, String instanceId) throws Exception {
        ClusterInfo clusterInfo = adminExt.examineBrokerClusterInfo();
        if (clusterInfo == null || clusterInfo.getBrokerAddrTable() == null) {
            return List.of();
        }
        String configuredCluster = StringUtils.hasText(instanceId)
                ? runtimeAdminClientResolver.configuredClusterName(instanceId) : null;
        return AclBrokerScope.scopedBrokerNames(clusterInfo, configuredCluster).stream()
                .map(clusterInfo.getBrokerAddrTable()::get)
                .filter(broker -> broker != null && broker.getBrokerAddrs() != null)
                .map(broker -> broker.getBrokerAddrs().get(MixAll.MASTER_ID))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * Broker ACL 2.0 resources are typed strings ("Topic:orders", "Group:cg"); a bare name parses
     * to a null resource and the broker rejects the entry with CODE 16. Cluster-scoped rules map to
     * the ANY resource, which the broker stores as "*". A rule with no resource type (or one the
     * broker model has no equivalent for) returns {@code null}: its resource name cannot be typed,
     * and pushing it as ANY would widen a named grant to the whole cluster.
     */
    private String brokerResource(AclRuleVO rule) {
        String type = rule.getResourceType() == null ? "" : rule.getResourceType();
        if ("Topic".equalsIgnoreCase(type)) {
            return "Topic:" + rule.getResource();
        }
        if ("Group".equalsIgnoreCase(type)) {
            return "Group:" + rule.getResource();
        }
        if ("Cluster".equalsIgnoreCase(type)) {
            return "*";
        }
        return null;
    }

    /** ALL expands to both message directions; anything else the broker cannot act on is dropped. */
    private List<String> mapAclActions(List<String> actions) {
        List<String> mapped = new ArrayList<>();
        for (String action : actions == null ? List.<String>of() : actions) {
            String normalized = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
            List<String> expanded = switch (normalized) {
                case "ALL" -> List.of("PUB", "SUB");
                case "PUB", "SUB", "GET" -> List.of(normalized);
                default -> List.of();
            };
            for (String value : expanded) {
                if (!mapped.contains(value)) {
                    mapped.add(value);
                }
            }
        }
        return mapped;
    }

    private List<String> splitWhiteList(String whiteRemoteAddress) {
        if (!StringUtils.hasText(whiteRemoteAddress)) {
            return List.of();
        }
        return Arrays.stream(whiteRemoteAddress.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String principalOf(String username) {
        return "user:" + username;
    }

    private static String rootMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    /**
     * Returns the plain-text credentials of a user for the "view password" action.
     * The secret is stored base64-encoded in the database and decoded here.
     */
    public AclUserVO getUserCredentials(String id, String instanceId) {
        AclUserVO user;
        if (isTencentInstance(instanceId)) {
            user = tencentAclService.getUserCredentials(instanceId, id);
        } else {
            if (!StringUtils.hasText(id)) {
                throw new BusinessException(400, "ACL user id is required");
            }
            log.info("Revealing credentials for ACL user id={}", id);
            user = aclRepository.findUserById(EntityIds.parseId(id))
                    .orElseThrow(() -> new BusinessException(404, "ACL user not found: " + id));
        }
        // Both vendor paths reveal a secret, so both are audited; the detail never carries it.
        recordAudit("REVEAL_ACL_USER_CREDENTIALS", "ACL_USER", String.valueOf(id), null,
                "instanceId=" + instanceId);
        return withUtcWire(user);
    }

    /**
     * Validates an ACL 2.0 RBAC policy context so the model can be used by callers before it is
     * persisted. This makes the {@link org.apache.rocketmq.studio.model.Acl2PolicyContext} model
     * operational without duplicating the cluster-config endpoints owned by the separate ACL 2.0
     * functional change.
     *
     * <p>Checks: non-blank policy name, a valid binding type, a non-null rules list, and that every
     * IP whitelist entry is a well-formed range (validated through {@link IpRangeMatcher}).
     *
     * @param policy the ACL 2.0 policy to validate
     * @throws BusinessException with HTTP 400 when a required field is missing or malformed
     */
    public void validateAcl2Policy(Acl2PolicyContext policy) {
        if (policy == null) {
            throw new BusinessException(400, "ACL 2.0 policy is required");
        }
        if (!StringUtils.hasText(policy.getPolicyName())) {
            throw new BusinessException(400, "ACL 2.0 policyName is required");
        }
        if (!StringUtils.hasText(policy.getBoundType()) || !isValidAcl2BoundType(policy.getBoundType())) {
            throw new BusinessException(400,
                    "ACL 2.0 boundType must be one of TOPIC, GROUP, *, USER, SERVICE_ACCOUNT (got: "
                            + policy.getBoundType() + ")");
        }
        if (policy.getRules() == null) {
            throw new BusinessException(400, "ACL 2.0 policy rules are required");
        }
        if (policy.getWhiteSet() != null) {
            for (String entry : policy.getWhiteSet()) {
                if (!IpRangeMatcher.isValidRange(entry)) {
                    throw new BusinessException(400,
                            "ACL 2.0 whiteSet entry is not a valid IP/CIDR range: " + entry);
                }
            }
        }
    }

    private boolean isTencentInstance(String instanceId) {
        if (!StringUtils.hasText(instanceId)) {
            return false;
        }
        return instanceResolver.findByIdentifier(instanceId)
                .map(instance -> instance.getVendor() == InstanceVendor.TENCENT)
                .orElse(false);
    }

    /**
     * Guards every ACL/user tool entrypoint so ACL 2.0 operations fail fast with a clear upgrade
     * hint when the backing broker is too old. Tencent instances are exempt (they use role-based
     * ACL rather than the Apache broker auth module). When the broker version cannot be resolved
     * the guard is permissive and lets the operation through to avoid false positives.
     */
    private void requireAcl2Supported(String instanceId) {
        if (isTencentInstance(instanceId)) {
            return;
        }
        int[] detected = detectLowestBrokerVersion(instanceId);
        if (detected == null) {
            return;
        }
        if (compareVersions(detected, MIN_ACL2_BROKER_VERSION) < 0) {
            throw new BusinessException(426,
                    "ACL 2.0 requires broker >= " + formatVersion(MIN_ACL2_BROKER_VERSION)
                            + "; detected " + formatVersion(detected) + " \u2014 please upgrade the broker");
        }
    }

    private int[] detectLowestBrokerVersion(String instanceId) {
        List<BrokerVO> brokers;
        try {
            brokers = clusterProvider.discoverBrokers(instanceId, null);
        } catch (Exception discoveryFailure) {
            log.debug("ACL 2.0 version guard could not discover brokers for instance {}: {}",
                    instanceId, discoveryFailure.getMessage());
            return null;
        }
        if (brokers == null || brokers.isEmpty()) {
            return null;
        }
        int[] lowest = null;
        for (BrokerVO broker : brokers) {
            if (broker == null) {
                continue;
            }
            int[] parsed = parseVersion(broker.getVersion());
            if (parsed == null) {
                continue;
            }
            if (lowest == null || compareVersions(parsed, lowest) < 0) {
                lowest = parsed;
            }
        }
        return lowest;
    }

    /**
     * Normalizes a broker version descriptor into a comparable {@code [major, minor, patch]} tuple.
     * Handles both {@code MQVersion.getVersionDesc} forms (e.g. {@code V5_3_3}) and plain semantic
     * versions (e.g. {@code 5.3.0}). Returns {@code null} when no numeric version can be extracted.
     */
    static int[] parseVersion(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        int start = 0;
        while (start < value.length() && !Character.isDigit(value.charAt(start))) {
            start++;
        }
        if (start == value.length()) {
            return null;
        }
        value = value.substring(start);
        List<Integer> parts = new ArrayList<>(3);
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length() && parts.size() < 3; i++) {
            char character = value.charAt(i);
            if (Character.isDigit(character)) {
                digits.append(character);
            } else if (digits.length() > 0) {
                parts.add(Integer.parseInt(digits.toString()));
                digits.setLength(0);
                if (character != '.' && character != '_') {
                    break;
                }
            } else {
                break;
            }
        }
        if (digits.length() > 0 && parts.size() < 3) {
            parts.add(Integer.parseInt(digits.toString()));
        }
        if (parts.isEmpty()) {
            return null;
        }
        int major = parts.get(0);
        int minor = parts.size() > 1 ? parts.get(1) : 0;
        int patch = parts.size() > 2 ? parts.get(2) : 0;
        return new int[] {major, minor, patch};
    }

    private static int compareVersions(int[] left, int[] right) {
        for (int i = 0; i < 3; i++) {
            if (left[i] != right[i]) {
                return Integer.compare(left[i], right[i]);
            }
        }
        return 0;
    }

    private static String formatVersion(int[] version) {
        return version[0] + "." + version[1] + "." + version[2];
    }

    private boolean isValidAcl2BoundType(String boundType) {
        return switch (boundType.trim().toUpperCase(Locale.ROOT)) {
            case "TOPIC", "GROUP", "*", "USER", "SERVICE_ACCOUNT" -> true;
            default -> false;
        };
    }

    /** Write paths echo the stored row, so masking must not also shift its creation stamp. */
    private AclUserVO maskCredentials(AclUserVO user) {
        return AclUserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .accessKey(CredentialUtils.mask(user.getAccessKey()))
                .secretKey(CredentialUtils.mask(user.getSecretKey()))
                .admin(user.isAdmin())
                .permRead(user.getPermRead())
                .permWrite(user.getPermWrite())
                .clusters(user.getClusters() == null ? null : List.copyOf(user.getClusters()))
                .whiteRemoteAddress(user.getWhiteRemoteAddress())
                .gmtCreate(user.getGmtCreate())
                .build();
    }

    /**
     * The read-path wire shape: a masked copy whose creation stamp is UTC. maskCredentials already
     * builds a fresh row, so the shift lands on the copy and the stored base stays server-local.
     */
    private AclUserVO forWire(AclUserVO user) {
        AclUserVO masked = maskCredentials(user);
        masked.setGmtCreate(toUtcWire(masked.getGmtCreate()));
        return masked;
    }

    /**
     * ACL timestamps are stored as zone-less server-local values (every source produces that
     * shape: the local repositories write {@code LocalDateTime.now()}, the Tencent adapter
     * converts cloud epochs with the server zone). The pages render them in the viewer's zone,
     * so the JSON wire carries a UTC wall clock — the alert / K8s-cert convention — instead of a
     * zone-less value the browser would parse as its own zone. The stored base is not converted,
     * and these mappers return copies so repository- or adapter-owned rows stay untouched.
     */
    private static LocalDateTime toUtcWire(LocalDateTime serverLocal) {
        return serverLocal == null ? null : serverLocal.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static AclRuleVO toUtcWire(AclRuleVO rule) {
        if (rule == null) {
            return null;
        }
        return AclRuleVO.builder()
                .id(rule.getId())
                .principal(rule.getPrincipal())
                .resource(rule.getResource())
                .resourceType(rule.getResourceType())
                .resourcePattern(rule.getResourcePattern())
                .actions(rule.getActions() == null ? null : List.copyOf(rule.getActions()))
                .decision(rule.getDecision())
                .scope(rule.getScope())
                .aclVersion(rule.getAclVersion())
                .gmtCreate(toUtcWire(rule.getGmtCreate()))
                .build();
    }

    private static AclUserVO withUtcWire(AclUserVO user) {
        if (user == null) {
            return null;
        }
        return AclUserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .accessKey(user.getAccessKey())
                .secretKey(user.getSecretKey())
                .admin(user.isAdmin())
                .permRead(user.getPermRead())
                .permWrite(user.getPermWrite())
                .clusters(user.getClusters() == null ? null : List.copyOf(user.getClusters()))
                .whiteRemoteAddress(user.getWhiteRemoteAddress())
                .gmtCreate(toUtcWire(user.getGmtCreate()))
                .build();
    }

    private static PageResult<AclRuleVO> toUtcWire(PageResult<AclRuleVO> page) {
        if (page == null) {
            return null;
        }
        return PageResult.of(page.getItems().stream().map(AclService::toUtcWire).toList(),
                page.getTotal(), page.getPage(), page.getSize());
    }

    private static int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private static int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? DEFAULT_RULE_PAGE_SIZE : pageSize;
    }

    private static int requireValidPage(Integer page) {
        if (page != null && page < 1) {
            throw invalidPagination();
        }
        return normalizePage(page);
    }

    private static int requireValidPageSize(Integer pageSize) {
        if (pageSize != null && (pageSize < 1 || pageSize > MAX_PAGE_SIZE)) {
            throw invalidPagination();
        }
        return normalizePageSize(pageSize);
    }

    private static BusinessException invalidPagination() {
        return new BusinessException(400,
                "page must be >= 1 and pageSize must be between 1 and " + MAX_PAGE_SIZE);
    }

    private static boolean containsIgnoreCase(String value, String expectedFragment) {
        if (!StringUtils.hasText(expectedFragment)) {
            return true;
        }
        return StringUtils.hasText(value)
                && value.toLowerCase(Locale.ROOT).contains(expectedFragment.toLowerCase(Locale.ROOT));
    }

    private static boolean equalsIgnoreCase(String value, String expected) {
        if (!StringUtils.hasText(expected)) {
            return true;
        }
        return StringUtils.hasText(value) && value.equalsIgnoreCase(expected);
    }

    private static PageResult<AclRuleVO> paginateRules(List<AclRuleVO> rules, int page, int pageSize) {
        int total = rules.size();
        int fromIndex = (int) Math.min(Pagination.pageOffset(page, pageSize), total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        return PageResult.of(rules.subList(fromIndex, toIndex), total, page, pageSize);
    }

    private void auditRule(String operation, AclRuleVO rule) {
        recordAudit(operation, "ACL_RULE", String.valueOf(rule.getId()), null,
                "principal=" + rule.getPrincipal());
    }

    private void auditUser(String operation, AclUserVO user) {
        recordAudit(operation, "ACL_USER", String.valueOf(user.getId()), null,
                "username=" + user.getUsername() + ", admin=" + user.isAdmin());
    }

    /**
     * Generates a random 32-char hex token for locally provisioned ACL credentials.
     */
    private static String randomCredentialToken() {
        byte[] bytes = new byte[16];
        CREDENTIAL_RANDOM.nextBytes(bytes);
        StringBuilder token = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            token.append(Character.forDigit((value >> 4) & 0xF, 16));
            token.append(Character.forDigit(value & 0xF, 16));
        }
        return token.toString();
    }


    private void recordAudit(String operation, String resourceType, String resourceName,
                             String clusterId, String detail) {
        try {
            operationAuditService.record(operation, resourceType, resourceName, clusterId, detail, "SUCCESS", null);
        } catch (Exception auditFailure) {
            log.warn("Failed to record audit operation={} resource={}: {}", operation, resourceName,
                    auditFailure.getMessage());
        }
    }

}
