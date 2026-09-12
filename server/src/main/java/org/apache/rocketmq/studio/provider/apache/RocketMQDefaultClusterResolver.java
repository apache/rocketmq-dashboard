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

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.MqAdminProperties;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RocketMQDefaultClusterResolver {
    private final RocketMQProperties properties;
    private final MqAdminProperties adminProperties;
    private final MqAdminExtFactory adminFactory;

    public Optional<InstanceVO> find(String cluster) {
        if (!StringUtils.hasText(cluster) || !StringUtils.hasText(properties.getNamesrvAddr())) {
            return Optional.empty();
        }
        return names().contains(cluster) ? Optional.of(instance(cluster)) : Optional.empty();
    }

    public List<String> names() {
        if (!StringUtils.hasText(properties.getNamesrvAddr())) {
            return List.of();
        }
        return execute(admin -> {
            var info = admin.examineBrokerClusterInfo();
            return info == null || info.getClusterAddrTable() == null ? List.of()
                    : info.getClusterAddrTable().keySet().stream().sorted().toList();
        });
    }

    public InstanceVO instance(String cluster) {
        if (!StringUtils.hasText(properties.getNamesrvAddr())) {
            throw new BusinessException(503, "RocketMQ admin not connected");
        }
        return InstanceVO.builder().name(cluster).vendor(InstanceVendor.APACHE).type(InstanceType.DIRECT)
                .endpoint(properties.getNamesrvAddr().trim())
                .adminCredentialRef(cluster) // Use the cluster name as the default credential reference.
                .build();
    }

    public <T> T execute(MqAdminExtFactory.AdminAction<T> action) {
        InstanceVO instance = instance(null);
        String reference = instance.getAdminCredentialRef();
        if (!StringUtils.hasText(reference)) {
            return adminFactory.execute(instance.getEndpoint(), null, action);
        }
        reference = reference.trim();
        MqAdminProperties.Credential credential = adminProperties.getCredentials().get(reference);
        if (credential == null || !StringUtils.hasText(credential.getAccessKey())
                || !StringUtils.hasText(credential.getSecretKey())) {
            throw new BusinessException(422, "Admin credential reference is not configured: " + reference);
        }
        RPCHook hook = new AclClientRPCHook(new SessionCredentials(
                credential.getAccessKey().trim(), credential.getSecretKey().trim()));
        return adminFactory.execute(instance.getEndpoint(), hook, reference, action);
    }
}
