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
package org.apache.rocketmq.studio.provider;

import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class InstanceProviderRegistry {

    private final Map<InstanceVendor, InstanceProvider> providers = new EnumMap<>(InstanceVendor.class);
    private final Map<InstanceVendor, CloudCatalogProvider> catalogs = new EnumMap<>(InstanceVendor.class);
    private final InstanceRepository instanceRepository;

    public InstanceProviderRegistry(List<InstanceProvider> providerList,
                                    List<CloudCatalogProvider> catalogList,
                                    InstanceRepository instanceRepository) {
        providerList.forEach(provider -> registerProvider(provider.vendor(), provider));
        catalogList.forEach(catalog -> registerCatalog(catalog.vendor(), catalog));
        this.instanceRepository = instanceRepository;
    }

    private void registerProvider(InstanceVendor vendor, InstanceProvider provider) {
        if (providers.putIfAbsent(vendor, provider) != null) {
            throw new IllegalStateException("Duplicate instance provider registered for vendor " + vendor);
        }
    }

    private void registerCatalog(InstanceVendor vendor, CloudCatalogProvider catalog) {
        if (catalogs.putIfAbsent(vendor, catalog) != null) {
            throw new IllegalStateException("Duplicate cloud catalog provider registered for vendor " + vendor);
        }
    }

    public InstanceProvider forVendor(InstanceVendor vendor) {
        InstanceProvider provider = providers.get(vendor);
        if (provider == null) {
            throw new BusinessException(501, "No instance provider registered for vendor " + vendor);
        }
        return provider;
    }

    /**
     * Resolves the provider for the given Studio instance id. Returns empty for a blank id
     * so callers can fall back to the legacy global behavior.
     */
    public Optional<InstanceProvider> byInstanceId(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return Optional.empty();
        }
        InstanceVO instance = instanceRepository.findByIdentifier(instanceId)
                .orElseThrow(() -> new BusinessException(404, "Instance not found: " + instanceId));
        InstanceVendor vendor = instance.getVendor() == null ? InstanceVendor.APACHE : instance.getVendor();
        return Optional.of(forVendor(vendor));
    }

    public CloudCatalogProvider catalogFor(InstanceVendor vendor) {
        CloudCatalogProvider catalog = catalogs.get(vendor);
        if (catalog == null) {
            throw new BusinessException(501, "No cloud catalog provider registered for vendor " + vendor);
        }
        return catalog;
    }
}
