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
package org.apache.rocketmq.studio.cluster.nameserver;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.persistence.entity.RmqNameserver;
import org.apache.rocketmq.studio.persistence.mapper.RmqNameserverMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NameserverRegistryService {

    private final RmqNameserverMapper nameserverMapper;

    public List<NameserverRegistryVO> list() {
        return nameserverMapper.selectList(new QueryWrapper<RmqNameserver>().orderByAsc("id")).stream()
                .map(this::toVO)
                .toList();
    }

    public NameserverRegistryVO create(CreateNameserverRegistryDTO command) {
        String name = normalizeName(command.getName());
        Long existing = nameserverMapper.selectCount(new QueryWrapper<RmqNameserver>()
                .eq("name", name));
        if (existing != null && existing > 0) {
            throw duplicateName(name);
        }
        RmqNameserver entity = new RmqNameserver();
        entity.setName(name);
        entity.setNamesrvAddr(NamesrvAddrParser.normalize(command.getNamesrvAddr()));
        entity.setK8sNamespace(command.getK8sNamespace());
        entity.setK8sId(command.getK8sId());
        entity.setDescription(command.getDescription());
        try {
            nameserverMapper.insert(entity);
        } catch (DataIntegrityViolationException exception) {
            // The unique index is the final guard against concurrent duplicate creates.
            throw duplicateName(name);
        }
        return toVO(nameserverMapper.selectById(entity.getId()));
    }

    public NameserverRegistryVO update(UpdateNameserverRegistryDTO command) {
        RmqNameserver entity = nameserverMapper.selectById(command.getId());
        if (entity == null) {
            throw new BusinessException(404, "NameServer registry entry not found: " + command.getId());
        }
        String name = normalizeName(command.getName());
        Long duplicates = nameserverMapper.selectCount(new QueryWrapper<RmqNameserver>()
                .eq("name", name)
                .ne("id", command.getId()));
        if (duplicates != null && duplicates > 0) {
            throw duplicateName(name);
        }
        entity.setName(name);
        entity.setNamesrvAddr(NamesrvAddrParser.normalize(command.getNamesrvAddr()));
        entity.setK8sNamespace(command.getK8sNamespace());
        entity.setK8sId(command.getK8sId());
        entity.setDescription(command.getDescription());
        try {
            nameserverMapper.updateById(entity);
        } catch (DataIntegrityViolationException exception) {
            // The unique index is the final guard against concurrent rename collisions.
            throw duplicateName(name);
        }
        return toVO(nameserverMapper.selectById(entity.getId()));
    }

    /**
     * Registry names are compared and displayed as-is, so surrounding whitespace must not
     * create near-duplicate entries ("prod" vs "prod ").
     */
    private static String normalizeName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new BusinessException(400, "name must not be blank");
        }
        return name;
    }

    private static BusinessException duplicateName(String name) {
        return new BusinessException(409, "NameServer registry name already exists: " + name);
    }

    public void delete(Long id) {
        if (nameserverMapper.selectById(id) == null) {
            throw new BusinessException(404, "NameServer registry entry not found: " + id);
        }
        nameserverMapper.deleteById(id);
    }

    private NameserverRegistryVO toVO(RmqNameserver entity) {
        return NameserverRegistryVO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .namesrvAddr(entity.getNamesrvAddr())
                .k8sNamespace(entity.getK8sNamespace())
                .k8sId(entity.getK8sId())
                .status(entity.getStatus())
                .description(entity.getDescription())
                .gmtCreate(entity.getGmtCreate())
                .gmtModified(entity.getGmtModified())
                .build();
    }
}
