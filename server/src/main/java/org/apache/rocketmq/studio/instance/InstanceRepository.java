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

import org.apache.rocketmq.studio.common.domain.enums.InstanceType;

import org.apache.rocketmq.studio.common.domain.PageResult;

import java.util.List;
import java.util.Optional;

public interface InstanceRepository {
    List<InstanceVO> findAll();

    long countAll();

    List<InstanceVO> findByType(InstanceType type);

    List<InstanceVO> search(String keyword);

    List<InstanceVO> findByTypeAndSearch(InstanceType type, String keyword);

    long count(InstanceType type, String keyword);

    PageResult<InstanceVO> findPage(InstanceType type, String keyword, int page, int pageSize);

    Optional<InstanceVO> findById(Long id);

    Optional<InstanceVO> findByName(String name);

    /**
     * Resolves an instance by the external instance identifier: matches the unique name
     * first, falling back to the numeric primary key for references that carry the id.
     */
    default Optional<InstanceVO> findByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        Optional<InstanceVO> byName = findByName(identifier);
        if (byName.isPresent()) {
            return byName;
        }
        try {
            return findById(Long.parseLong(identifier.trim()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    InstanceVO save(InstanceVO instance);

    boolean deleteById(Long id);

    boolean existsByCredentialId(Long credentialId);

    long countTopicsByInstance(String instanceId);

    long countGroupsByInstance(String instanceId);
}
