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
package org.apache.rocketmq.studio.ops.ai.tool.catalog;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceResolver;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolError;
import org.apache.rocketmq.studio.provider.InstanceCapability;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CapabilityResolver {

    private final InstanceProviderRegistry providerRegistry;
    private final InstanceResolver instanceResolver;

    public Set<String> resolve(String cluster) {
        if (cluster == null || cluster.isBlank()) {
            throw ToolError.CAPABILITY_CLUSTER_REQUIRED.exception();
        }
        InstanceVO instance = instanceResolver.findByName(cluster)
                .orElseThrow(() -> ToolError.INSTANCE_NOT_FOUND.exception(cluster));
        return resolve(instance);
    }

    public Set<String> resolve(InstanceVO instance) {
        InstanceVendor vendor = instance.getVendor() == null
                ? InstanceVendor.APACHE : instance.getVendor();
        InstanceType type = instance.getType() == null ? InstanceType.DIRECT : instance.getType();
        Set<String> accessCapabilities = switch (type) {
            case CLOUD -> Set.of("CLOUD_API");
            case DIRECT -> Set.of(
                    "BROKER_ADMIN",
                    "CLUSTER_TOPOLOGY",
                    "NAMESERVER_ADMIN",
                    "REMOTING");
            case PROXY_LOCAL, PROXY_CLUSTER -> Set.of(
                    "PROXY_DISCOVERY",
                    "BROKER_ADMIN",
                    "CLUSTER_TOPOLOGY",
                    "NAMESERVER_ADMIN",
                    "REMOTING");
        };
        Set<String> capabilities = new HashSet<>(accessCapabilities);
        providerRegistry.forVendor(vendor).capabilities().stream()
                .map(InstanceCapability::name)
                .forEach(capabilities::add);
        return Set.copyOf(capabilities);
    }
}
