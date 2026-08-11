/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.cluster.proxy;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.persistence.entity.RmqProxyAddress;
import org.apache.rocketmq.studio.persistence.mapper.RmqProxyAddressMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MybatisPlusProxyAddressRepository implements ProxyAddressRepository {
    private final RmqProxyAddressMapper mapper;

    @Override
    public List<ProxyAddressRecord> findByScope(String scopeId) {
        return mapper.selectList(new QueryWrapper<RmqProxyAddress>()
                        .eq("scope_id", scopeId)
                        .orderByAsc("id"))
                .stream().map(MybatisPlusProxyAddressRepository::toRecord).toList();
    }

    @Override
    public boolean insert(String scopeId, String address, boolean selected) {
        RmqProxyAddress entity = new RmqProxyAddress();
        entity.setScopeId(scopeId);
        entity.setAddress(address);
        entity.setSelected(selected);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return mapper.insert(entity) > 0;
    }

    @Override
    public boolean delete(String scopeId, String address) {
        return mapper.delete(new QueryWrapper<RmqProxyAddress>()
                .eq("scope_id", scopeId).eq("address", address)) > 0;
    }

    @Override
    @Transactional
    public boolean select(String scopeId, String address) {
        mapper.update(null, new UpdateWrapper<RmqProxyAddress>()
                .eq("scope_id", scopeId)
                .set("selected", false));
        return mapper.update(null, new UpdateWrapper<RmqProxyAddress>()
                .eq("scope_id", scopeId)
                .eq("address", address)
                .set("selected", true)
                .set("updated_at", LocalDateTime.now())) > 0;
    }

    private static ProxyAddressRecord toRecord(RmqProxyAddress entity) {
        return ProxyAddressRecord.builder()
                .id(entity.getId())
                .scopeId(entity.getScopeId())
                .address(entity.getAddress())
                .selected(Boolean.TRUE.equals(entity.getSelected()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
