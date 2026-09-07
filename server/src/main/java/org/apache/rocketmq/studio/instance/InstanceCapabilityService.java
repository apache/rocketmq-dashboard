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
package org.apache.rocketmq.studio.instance;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.provider.InstanceCapability;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Resolves the capability contract for an instance by delegating to the vendor provider.
 */
@Service
@RequiredArgsConstructor
public class InstanceCapabilityService {

    private final InstanceRepository instanceRepository;
    private final InstanceProviderRegistry providerRegistry;

    public InstanceCapabilitiesVO getCapabilities(Long instanceId) {
        InstanceVO instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new BusinessException(404, "Instance not found: " + instanceId));
        InstanceVendor vendor = instance.getVendor() == null ? InstanceVendor.APACHE : instance.getVendor();
        InstanceProvider provider = providerRegistry.forVendor(vendor);
        List<InstanceCapability> capabilities = provider.capabilities().stream()
                .sorted(Comparator.comparingInt(InstanceCapability::ordinal))
                .toList();
        return new InstanceCapabilitiesVO(instance.getName(), vendor, instance.getType(), capabilities);
    }
}
