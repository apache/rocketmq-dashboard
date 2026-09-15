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
package org.apache.rocketmq.studio.provider.apache;

import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.MqAdminProperties;
import org.apache.rocketmq.studio.cluster.broker.OpsDefaultClient;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Component
public class RocketMQDefaultClusterResolver {

    /**
     * Well-known key of the externally supplied admin credential that protects the configured
     * default cluster ({@code studio.cluster.admin.credentials.admin.*}).
     *
     * <p>The default cluster has no database record, so unlike a registered instance it cannot
     * carry a per-instance credential reference: a physical cluster name is never a credential key.
     */
    private static final String DEFAULT_ADMIN_CREDENTIAL_REF = "admin";

    private final RocketMQProperties properties;
    private final MqAdminProperties adminProperties;
    private final MqAdminExtFactory adminFactory;
    private final OpsDefaultClient defaultClient;

    @Autowired
    public RocketMQDefaultClusterResolver(RocketMQProperties properties,
                                          MqAdminProperties adminProperties,
                                          MqAdminExtFactory adminFactory,
                                          OpsDefaultClient defaultClient) {
        this.properties = properties;
        this.adminProperties = adminProperties;
        this.adminFactory = adminFactory;
        this.defaultClient = defaultClient;
    }

    public RocketMQDefaultClusterResolver(RocketMQProperties properties,
                                          MqAdminProperties adminProperties,
                                          MqAdminExtFactory adminFactory) {
        this.properties = properties;
        this.adminProperties = adminProperties;
        this.adminFactory = adminFactory;
        this.defaultClient = null;
    }

    public Optional<InstanceVO> find(String cluster) {
        OpsDefaultClient.Selection defaultSelection = defaultSelection();
        String endpoint = namesrvAddr(defaultSelection);
        if (!StringUtils.hasText(cluster) || !StringUtils.hasText(endpoint)) {
            return Optional.empty();
        }
        return names(defaultSelection).contains(cluster) ? Optional.of(instance(cluster, endpoint)) : Optional.empty();
    }

    public List<String> names() {
        OpsDefaultClient.Selection defaultSelection = defaultSelection();
        if (!StringUtils.hasText(namesrvAddr(defaultSelection))) {
            return List.of();
        }
        return names(defaultSelection);
    }

    private List<String> names(OpsDefaultClient.Selection defaultSelection) {
        // Discovery runs before any instance is resolved and must keep working on deployments
        // without ACL, so it falls back to an anonymous admin connection when no default admin
        // credential is configured. Every other entry point fails closed instead.
        return execute(defaultSelection, admin -> {
            var info = admin.examineBrokerClusterInfo();
            return info == null || info.getClusterAddrTable() == null ? List.of()
                    : info.getClusterAddrTable().keySet().stream().sorted().toList();
        }, configuredAdminCredential());
    }

    public InstanceVO instance(String cluster) {
        String endpoint = requireEndpoint();
        return instance(cluster, endpoint);
    }

    private InstanceVO instance(String cluster, String endpoint) {
        return InstanceVO.builder().name(cluster).vendor(InstanceVendor.APACHE).type(InstanceType.DIRECT)
                .endpoint(endpoint)
                // Advertise the configured default admin credential so RuntimeAdminClientResolver
                // authenticates with it; stay anonymous when ACL is not configured at all.
                .adminCredentialRef(configuredAdminCredential() == null ? null : DEFAULT_ADMIN_CREDENTIAL_REF)
                .build();
    }

    /**
     * Runs an action against the configured default cluster using its configured admin credential.
     *
     * <p>A configured ACL identity is never dropped silently: when the default admin credential is
     * absent or incomplete this fails with 422 instead of reconnecting anonymously.
     */
    public <T> T execute(MqAdminExtFactory.AdminAction<T> action) {
        OpsDefaultClient.Selection defaultSelection = defaultSelection();
        MqAdminProperties.Credential credential = configuredAdminCredential();
        if (credential == null) {
            throw new BusinessException(422,
                    "Admin credential reference is not configured: " + DEFAULT_ADMIN_CREDENTIAL_REF);
        }
        return execute(defaultSelection, action, credential);
    }

    private <T> T execute(OpsDefaultClient.Selection defaultSelection, MqAdminExtFactory.AdminAction<T> action,
                          MqAdminProperties.Credential credential) {
        String endpoint = requireEndpoint(defaultSelection);
        if (credential == null) {
            if (defaultClient == null) {
                return adminFactory.execute(endpoint, null, action);
            }
            return defaultSelection.execute(null, "anonymous", action);
        }
        RPCHook hook = new AclClientRPCHook(new SessionCredentials(
                credential.getAccessKey().trim(), credential.getSecretKey().trim()));
        if (defaultClient == null) {
            return adminFactory.execute(endpoint, hook, DEFAULT_ADMIN_CREDENTIAL_REF, action);
        }
        return defaultSelection.execute(hook, DEFAULT_ADMIN_CREDENTIAL_REF, action);
    }

    /** Returns the usable default admin credential, or {@code null} when ACL is not configured. */
    private MqAdminProperties.Credential configuredAdminCredential() {
        MqAdminProperties.Credential credential = adminProperties.getCredentials().get(DEFAULT_ADMIN_CREDENTIAL_REF);
        if (credential == null || !StringUtils.hasText(credential.getAccessKey())
                || !StringUtils.hasText(credential.getSecretKey())) {
            return null;
        }
        return credential;
    }

    private String requireEndpoint() {
        return requireEndpoint(defaultSelection());
    }

    private String requireEndpoint(OpsDefaultClient.Selection defaultSelection) {
        String namesrvAddr = namesrvAddr(defaultSelection);
        if (!StringUtils.hasText(namesrvAddr)) {
            throw new BusinessException(503, "RocketMQ admin not connected");
        }
        return namesrvAddr.trim();
    }

    private OpsDefaultClient.Selection defaultSelection() {
        return defaultClient == null ? null : defaultClient.select(properties.getNamesrvAddr());
    }

    private String namesrvAddr(OpsDefaultClient.Selection defaultSelection) {
        return defaultSelection == null ? properties.getNamesrvAddr() : defaultSelection.namesrvAddr();
    }
}
