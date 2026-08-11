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

package org.apache.rocketmq.studio.cluster.proxy;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import lombok.RequiredArgsConstructor;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyAddressService {

    private final InstanceRepository instanceRepository;

    /**
     * A managed Proxy instance has one durable access endpoint. Multi-address discovery and
     * failover require a Proxy Admin contract and are intentionally not inferred here.
     */
    public ProxyHomeVO getHomePage(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            throw new BusinessException(400, "instanceId is required");
        }
        InstanceVO instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new BusinessException(404, "Instance not found: " + instanceId));
        if (instance.getType() != InstanceType.PROXY) {
            throw new BusinessException(400, "Instance is not a Proxy instance: " + instanceId);
        }
        String endpoint = instance.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new BusinessException(409, "Proxy instance has no configured endpoint: " + instanceId);
        }
        String normalizedEndpoint = endpoint.trim();
        log.debug("Resolved Proxy endpoint for instance {}", instanceId);
        return ProxyHomeVO.builder()
                .proxyAddrList(List.of(normalizedEndpoint))
                .currentProxyAddr(normalizedEndpoint)
                .build();
    }

}
