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

package com.rocketmq.studio.instance;

import com.rocketmq.studio.common.domain.enums.InstanceType;
import com.rocketmq.studio.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstanceService {

    private final InstanceRepository instanceRepository;

    public List<InstanceVO> listInstances(InstanceType type, String search) {
        log.debug("Listing instances, type={}, search={}", type, search);
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();

        if (type != null && normalizedSearch != null) {
            return instanceRepository.findByTypeAndSearch(type, normalizedSearch);
        } else if (type != null) {
            return instanceRepository.findByType(type);
        } else if (normalizedSearch != null) {
            return instanceRepository.search(normalizedSearch);
        }
        return instanceRepository.findAll();
    }

    public InstanceVO createInstance(InstanceVO instance) {
        log.info("Creating instance: {}", instance.getName());

        if (instance.getName() == null || instance.getName().isBlank()) {
            throw new BusinessException(400, "InstanceVO name is required");
        }
        if (instance.getEndpoint() == null || instance.getEndpoint().isBlank()) {
            throw new BusinessException(400, "InstanceVO endpoint is required");
        }

        instance.setId(UUID.randomUUID().toString());
        instance.setCreatedAt(LocalDateTime.now());
        instance.setUpdatedAt(LocalDateTime.now());
        return instanceRepository.save(instance);
    }

    public InstanceVO updateInstance(InstanceVO instance) {
        log.info("Updating instance: {}", instance.getId());

        if (instance.getId() == null || instance.getId().isBlank()) {
            throw new BusinessException(400, "InstanceVO ID is required");
        }

        InstanceVO existing = instanceRepository.findById(instance.getId())
                .orElseThrow(() -> new BusinessException(404, "InstanceVO not found: " + instance.getId()));

        if (instance.getName() != null) {
            existing.setName(instance.getName());
        }
        if (instance.getType() != null) {
            existing.setType(instance.getType());
        }
        if (instance.getEndpoint() != null) {
            existing.setEndpoint(instance.getEndpoint());
        }
        if (instance.getRemark() != null) {
            existing.setRemark(instance.getRemark());
        }
        existing.setUpdatedAt(LocalDateTime.now());

        return instanceRepository.save(existing);
    }

    public void deleteInstance(String id) {
        log.info("Deleting instance: {}", id);

        if (id == null || id.isBlank()) {
            throw new BusinessException(400, "InstanceVO ID is required");
        }

        instanceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "InstanceVO not found: " + id));
        instanceRepository.deleteById(id);
    }
}
