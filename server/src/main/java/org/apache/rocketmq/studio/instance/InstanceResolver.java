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
import org.apache.rocketmq.studio.provider.apache.RocketMQDefaultClusterResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class InstanceResolver {
    private final InstanceRepository instanceRepository;
    private final RocketMQDefaultClusterResolver defaultClusters;

    public Optional<InstanceVO> findByName(String cluster) {
        return instanceRepository.findByName(cluster).or(() -> defaultClusters.find(cluster));
    }

    public Optional<InstanceVO> findByIdentifier(String identifier) {
        Optional<InstanceVO> registered = instanceRepository.findByIdentifier(identifier);
        if (registered.isPresent() && (identifier.equals(registered.get().getName())
                || instanceRepository.findByName(identifier).isPresent())) {
            return registered;
        }
        return defaultClusters.find(identifier).or(() -> registered);
    }

    public String resolveClusterName(String identifier) {
        if (!StringUtils.hasText(identifier) || instanceRepository.findByName(identifier).isPresent()) {
            return null;
        }
        Optional<InstanceVO> configured = defaultClusters.find(identifier);
        if (configured.isPresent()) {
            return configured.get().getName();
        }
        if (instanceRepository.findByIdentifier(identifier).isPresent()) {
            return null;
        }
        throw new BusinessException(404, "Instance not found: " + identifier);
    }
}
