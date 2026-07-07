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
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class InMemoryInstanceRepository implements InstanceRepository {

    private final Map<String, InstanceVO> store = new ConcurrentHashMap<>();

    public InMemoryInstanceRepository() {
        initData();
    }

    private void initData() {
        addInstance("inst-1", "production-proxy", "Production Proxy InstanceVO",
                InstanceType.PROXY, "10.0.1.100:8080", 42, 18,
                LocalDateTime.now().minusDays(90));
        addInstance("inst-2", "staging-proxy", "Staging Proxy InstanceVO",
                InstanceType.PROXY, "10.0.2.100:8080", 15, 8,
                LocalDateTime.now().minusDays(60));
        addInstance("inst-3", "dev-direct", "Development Direct InstanceVO",
                InstanceType.DIRECT, "10.0.3.100:10911", 8, 3,
                LocalDateTime.now().minusDays(30));
    }

    private void addInstance(String id, String name, String remark, InstanceType type,
                             String endpoint, int topicCount, int consumerGroupCount,
                             LocalDateTime createdAt) {
        InstanceVO instance = InstanceVO.builder()
                .name(name)
                .remark(remark)
                .type(type)
                .endpoint(endpoint)
                .topicCount(topicCount)
                .consumerGroupCount(consumerGroupCount)
                .build();
        instance.setId(id);
        instance.setCreatedAt(createdAt);
        instance.setUpdatedAt(createdAt);
        store.put(id, instance);
    }

    @Override
    public List<InstanceVO> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<InstanceVO> findByType(InstanceType type) {
        return store.values().stream()
                .filter(i -> i.getType() == type)
                .collect(Collectors.toList());
    }

    @Override
    public List<InstanceVO> search(String keyword) {
        String lower = keyword.toLowerCase();
        return store.values().stream()
                .filter(i -> i.getName().toLowerCase().contains(lower)
                        || i.getRemark() != null && i.getRemark().toLowerCase().contains(lower))
                .collect(Collectors.toList());
    }

    @Override
    public List<InstanceVO> findByTypeAndSearch(InstanceType type, String keyword) {
        String lower = keyword.toLowerCase();
        return store.values().stream()
                .filter(i -> i.getType() == type)
                .filter(i -> i.getName().toLowerCase().contains(lower)
                        || i.getRemark() != null && i.getRemark().toLowerCase().contains(lower))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<InstanceVO> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public InstanceVO save(InstanceVO instance) {
        store.put(instance.getId(), instance);
        return instance;
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
    }
}
