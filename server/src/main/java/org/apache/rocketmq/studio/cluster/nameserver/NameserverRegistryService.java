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
        String name = command.getName().strip();
        String namesrvAddr = command.getNamesrvAddr().strip();
        Long existing = nameserverMapper.selectCount(new QueryWrapper<RmqNameserver>()
                .eq("name", name));
        if (existing != null && existing > 0) {
            throw new BusinessException(400, "NameServer registry name already exists: " + name);
        }
        RmqNameserver entity = new RmqNameserver();
        entity.setName(name);
        entity.setNamesrvAddr(namesrvAddr);
        entity.setK8sNamespace(command.getK8sNamespace());
        entity.setK8sId(command.getK8sId());
        entity.setDescription(command.getDescription());
        nameserverMapper.insert(entity);
        return toVO(nameserverMapper.selectById(entity.getId()));
    }

    public NameserverRegistryVO update(UpdateNameserverRegistryDTO command) {
        RmqNameserver entity = nameserverMapper.selectById(command.getId());
        if (entity == null) {
            throw new BusinessException(404, "NameServer registry entry not found: " + command.getId());
        }
        String name = command.getName().strip();
        String namesrvAddr = command.getNamesrvAddr().strip();
        Long duplicates = nameserverMapper.selectCount(new QueryWrapper<RmqNameserver>()
                .eq("name", name)
                .ne("id", command.getId()));
        if (duplicates != null && duplicates > 0) {
            throw new BusinessException(400, "NameServer registry name already exists: " + name);
        }
        entity.setName(name);
        entity.setNamesrvAddr(namesrvAddr);
        entity.setK8sNamespace(command.getK8sNamespace());
        entity.setK8sId(command.getK8sId());
        entity.setDescription(command.getDescription());
        nameserverMapper.updateById(entity);
        return toVO(nameserverMapper.selectById(entity.getId()));
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
