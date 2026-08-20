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
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.persistence.entity.RmqGroup;
import org.apache.rocketmq.studio.persistence.entity.RmqInstance;
import org.apache.rocketmq.studio.persistence.entity.RmqTopic;
import org.apache.rocketmq.studio.persistence.mapper.RmqGroupMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqInstanceMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTopicMapper;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class MybatisPlusInstanceRepository implements InstanceRepository {

    private final RmqInstanceMapper instanceMapper;
    private final RmqTopicMapper topicMapper;
    private final RmqGroupMapper groupMapper;

    @Override
    public List<InstanceVO> findAll() {
        return instanceMapper.selectList(
                new QueryWrapper<RmqInstance>().orderByAsc("id")).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public List<InstanceVO> findByType(InstanceType type) {
        return instanceMapper.selectList(
                new QueryWrapper<RmqInstance>()
                        .eq("type", type.name())
                        .orderByAsc("id")).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public List<InstanceVO> search(String keyword) {
        return instanceMapper.selectList(
                new QueryWrapper<RmqInstance>()
                        .and(w -> w.like("name", keyword)
                                .or().like("endpoint", keyword)
                                .or().like("remark", keyword))
                        .orderByAsc("id")).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public List<InstanceVO> findByTypeAndSearch(InstanceType type, String keyword) {
        return instanceMapper.selectList(
                new QueryWrapper<RmqInstance>()
                        .eq("type", type.name())
                        .and(w -> w.like("name", keyword)
                                .or().like("endpoint", keyword)
                                .or().like("remark", keyword))
                        .orderByAsc("id")).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public Optional<InstanceVO> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        RmqInstance entity = instanceMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(toVO(entity));
    }

    @Override
    public Optional<InstanceVO> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        RmqInstance entity = instanceMapper.selectOne(
                new QueryWrapper<RmqInstance>().eq("name", name).last("LIMIT 1"));
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(toVO(entity));
    }

    @Override
    @Transactional
    public InstanceVO save(InstanceVO instance) {
        RmqInstance entity = toEntity(instance);
        if (entity.getId() != null && instanceMapper.selectById(entity.getId()) != null) {
            if (instanceMapper.updateById(entity) == 0) {
                throw new BusinessException(409,
                        "Instance update was not applied: " + entity.getId());
            }
        } else {
            instanceMapper.insert(entity);
            instance.setId(entity.getId());
        }
        return instance;
    }

    @Override
    public boolean deleteById(Long id) {
        return id != null && instanceMapper.deleteById(id) > 0;
    }

    @Override
    public boolean existsByCredentialId(Long credentialId) {
        if (credentialId == null) {
            return false;
        }
        return instanceMapper.selectCount(
                new QueryWrapper<RmqInstance>().eq("credential_id", credentialId)) > 0;
    }

    @Override
    public long countTopicsByInstance(String instanceId) {
        return topicMapper.selectCount(
                new QueryWrapper<RmqTopic>().eq("instance_id", instanceId));
    }

    @Override
    public long countGroupsByInstance(String instanceId) {
        return groupMapper.selectCount(
                new QueryWrapper<RmqGroup>().eq("instance_id", instanceId));
    }

    private InstanceVO toVO(RmqInstance entity) {
        InstanceVO vo = InstanceVO.builder()
                .name(entity.getName())
                .remark(entity.getRemark())
                .type(parseType(entity.getId(), entity.getType()))
                .endpoint(entity.getEndpoint())
                .vendor(parseVendor(entity.getId(), entity.getVendor()))
                .cloudInstanceId(entity.getCloudInstanceId())
                .credentialId(entity.getCredentialId())
                .adminCredentialRef(entity.getAdminCredentialRef())
                .regionId(entity.getRegionId())
                .build();
        vo.setId(entity.getId());
        vo.setGmtCreate(entity.getGmtCreate());
        vo.setGmtModified(entity.getGmtModified());
        return vo;
    }

    private InstanceType parseType(Long instanceId, String type) {
        try {
            return InstanceType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw invalidPersistedValue(instanceId, "type", type);
        }
    }

    private InstanceVendor parseVendor(Long instanceId, String vendor) {
        try {
            return InstanceVendor.valueOf(vendor);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw invalidPersistedValue(instanceId, "vendor", vendor);
        }
    }

    private BusinessException invalidPersistedValue(Long instanceId, String field, String value) {
        return new BusinessException(500, "Invalid persisted instance " + field
                + " for instance " + instanceId + ": " + value);
    }

    private RmqInstance toEntity(InstanceVO vo) {
        RmqInstance entity = new RmqInstance();
        entity.setId(vo.getId());
        entity.setName(vo.getName());
        entity.setRemark(vo.getRemark());
        entity.setType(vo.getType() == null ? null : vo.getType().name());
        entity.setEndpoint(vo.getEndpoint());
        entity.setVendor(vo.getVendor() == null ? InstanceVendor.APACHE.name() : vo.getVendor().name());
        entity.setCloudInstanceId(vo.getCloudInstanceId());
        entity.setCredentialId(vo.getCredentialId());
        entity.setAdminCredentialRef(vo.getAdminCredentialRef());
        entity.setRegionId(vo.getRegionId());
        entity.setGmtCreate(vo.getGmtCreate());
        entity.setGmtModified(vo.getGmtModified());
        return entity;
    }
}
