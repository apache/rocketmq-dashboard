/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.cluster.broker;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class RuntimeAdminClientResolver {

    private final InstanceRepository instanceRepository;
    private final MqAdminExtFactory adminFactory;

    public InstanceVO resolveInstance(String instanceId) {
        if (!StringUtils.hasText(instanceId)) {
            throw new BusinessException(400, "instanceId is required");
        }
        return instanceRepository.findById(instanceId)
                .orElseThrow(() -> new BusinessException(404, "Instance not found: " + instanceId));
    }

    public String resolveEndpoint(String instanceId) {
        InstanceVO instance = requireApacheInstance(resolveInstance(instanceId));
        if (!StringUtils.hasText(instance.getEndpoint())) {
            throw new BusinessException(400, "Instance has no endpoint: " + instanceId);
        }
        return instance.getEndpoint().trim();
    }

    public <T> T execute(String instanceId, MqAdminExtFactory.AdminAction<T> action) {
        return execute(resolveInstance(instanceId), action);
    }

    public <T> T execute(InstanceVO instance, MqAdminExtFactory.AdminAction<T> action) {
        requireApacheInstance(instance);
        if (instance == null || !StringUtils.hasText(instance.getEndpoint())) {
            throw new BusinessException(400, "Instance endpoint is required");
        }
        return adminFactory.execute(instance.getEndpoint().trim(), null, action);
    }

    private InstanceVO requireApacheInstance(InstanceVO instance) {
        if (instance != null && instance.getVendor() != null && instance.getVendor() != InstanceVendor.APACHE) {
            throw new BusinessException(400,
                    "Runtime AdminClient only supports Apache instances: " + instance.getId());
        }
        return instance;
    }
}
