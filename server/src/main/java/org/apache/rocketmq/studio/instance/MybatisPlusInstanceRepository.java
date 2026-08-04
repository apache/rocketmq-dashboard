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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.persistence.entity.RmqGroup;
import org.apache.rocketmq.studio.persistence.entity.RmqInstance;
import org.apache.rocketmq.studio.persistence.entity.RmqTopic;
import org.apache.rocketmq.studio.persistence.mapper.RmqGroupMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqInstanceMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTopicMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class MybatisPlusInstanceRepository implements InstanceRepository {

    private final RmqInstanceMapper instanceMapper;
    private final RmqTopicMapper topicMapper;
    private final RmqGroupMapper groupMapper;

    public MybatisPlusInstanceRepository(RmqInstanceMapper instanceMapper,
                                         RmqTopicMapper topicMapper,
                                         RmqGroupMapper groupMapper) {
        this.instanceMapper = instanceMapper;
        this.topicMapper = topicMapper;
        this.groupMapper = groupMapper;
    }

    @Override
    public List<InstanceVO> findAll() {
        return withCounts(instanceMapper.selectList(
                new QueryWrapper<RmqInstance>().orderByAsc("id")));
    }

    @Override
    public List<InstanceVO> findByType(InstanceType type) {
        return withCounts(instanceMapper.selectList(
                new QueryWrapper<RmqInstance>()
                        .eq("type", type.name())
                        .orderByAsc("id")));
    }

    @Override
    public List<InstanceVO> search(String keyword) {
        return withCounts(instanceMapper.selectList(
                new QueryWrapper<RmqInstance>()
                        .and(w -> w.like("name", keyword)
                                .or().like("endpoint", keyword)
                                .or().like("remark", keyword))
                        .orderByAsc("id")));
    }

    @Override
    public List<InstanceVO> findByTypeAndSearch(InstanceType type, String keyword) {
        return withCounts(instanceMapper.selectList(
                new QueryWrapper<RmqInstance>()
                        .eq("type", type.name())
                        .and(w -> w.like("name", keyword)
                                .or().like("endpoint", keyword)
                                .or().like("remark", keyword))
                        .orderByAsc("id")));
    }

    @Override
    public Optional<InstanceVO> findById(String id) {
        RmqInstance entity = instanceMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        Map<String, Long> topicCounts = countByInstance(topicMapper.selectMaps(
                new QueryWrapper<RmqTopic>().select("instance_id", "COUNT(*) AS total")
                        .isNotNull("instance_id").eq("instance_id", id).groupBy("instance_id")));
        Map<String, Long> groupCounts = countByInstance(groupMapper.selectMaps(
                new QueryWrapper<RmqGroup>().select("instance_id", "COUNT(*) AS total")
                        .isNotNull("instance_id").eq("instance_id", id).groupBy("instance_id")));
        return Optional.of(toVO(entity, topicCounts, groupCounts));
    }

    @Override
    public InstanceVO save(InstanceVO instance) {
        RmqInstance entity = toEntity(instance);
        if (instanceMapper.selectById(entity.getId()) != null) {
            instanceMapper.updateById(entity);
        } else {
            instanceMapper.insert(entity);
        }
        return instance;
    }

    @Override
    public void deleteById(String id) {
        instanceMapper.deleteById(id);
    }

    private List<InstanceVO> withCounts(List<RmqInstance> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        Map<String, Long> topicCounts = countByInstance(topicMapper.selectMaps(
                new QueryWrapper<RmqTopic>().select("instance_id", "COUNT(*) AS total")
                        .isNotNull("instance_id").groupBy("instance_id")));
        Map<String, Long> groupCounts = countByInstance(groupMapper.selectMaps(
                new QueryWrapper<RmqGroup>().select("instance_id", "COUNT(*) AS total")
                        .isNotNull("instance_id").groupBy("instance_id")));
        return entities.stream()
                .map(entity -> toVO(entity, topicCounts, groupCounts))
                .collect(Collectors.toList());
    }

    private Map<String, Long> countByInstance(List<Map<String, Object>> rows) {
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object instanceId = row.get("instance_id");
            Object total = row.get("total");
            if (instanceId != null && total != null) {
                counts.put(instanceId.toString(), ((Number) total).longValue());
            }
        }
        return counts;
    }

    private InstanceVO toVO(RmqInstance entity,
                            Map<String, Long> topicCounts,
                            Map<String, Long> groupCounts) {
        InstanceVO vo = InstanceVO.builder()
                .name(entity.getName())
                .remark(entity.getRemark())
                .type(parseType(entity.getType()))
                .endpoint(entity.getEndpoint())
                .topicCount(topicCounts.getOrDefault(entity.getId(), 0L).intValue())
                .consumerGroupCount(groupCounts.getOrDefault(entity.getId(), 0L).intValue())
                .build();
        vo.setId(entity.getId());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private InstanceType parseType(String type) {
        try {
            return InstanceType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException ex) {
            return InstanceType.PROXY;
        }
    }

    private RmqInstance toEntity(InstanceVO vo) {
        RmqInstance entity = new RmqInstance();
        entity.setId(vo.getId());
        entity.setName(vo.getName());
        entity.setRemark(vo.getRemark());
        entity.setType(vo.getType() == null ? null : vo.getType().name());
        entity.setEndpoint(vo.getEndpoint());
        entity.setCreatedAt(vo.getCreatedAt());
        entity.setUpdatedAt(vo.getUpdatedAt());
        return entity;
    }
}
