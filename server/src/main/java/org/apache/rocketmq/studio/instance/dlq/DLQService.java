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
package org.apache.rocketmq.studio.instance.dlq;

import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DLQService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SELECTED_MESSAGES = 100;

    private final DLQProvider dlqProvider;
    private final InstanceProviderRegistry providerRegistry;

    public PageResult<DLQGroupVO> listDLQGroups(String instanceId, String search, int page, int pageSize) {
        requireApacheInstance(instanceId);
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(400, "Invalid page or pageSize");
        }
        return dlqProvider.listDLQGroups(instanceId,
                StringUtils.hasText(search) ? search.trim() : null, page, pageSize);
    }

    public List<DLQGroupVO> listDLQGroups(String instanceId) {
        requireApacheInstance(instanceId);
        log.info("Listing DLQ groups for instance: {}", instanceId);
        return dlqProvider.listDLQGroups(instanceId);
    }

    public DLQResendResultVO resendMessages(String instanceId, String groupName, Long startTime, Long endTime,
                                             String targetTopic) {
        requireApacheInstance(instanceId);
        validateResendRequest(groupName, startTime, endTime);
        log.info("Resending DLQ messages: group={}, targetTopic={}", groupName, targetTopic);
        return dlqProvider.resendMessages(instanceId, groupName, startTime, endTime, targetTopic);
    }

    public List<DLQMessageVO> exportMessages(String instanceId, String groupName, Long startTime, Long endTime,
                                             Integer maxCount) {
        requireApacheInstance(instanceId);
        validateResendRequest(groupName, startTime, endTime);
        log.info("Exporting DLQ messages: group={}, maxCount={}", groupName, maxCount);
        return dlqProvider.exportMessages(instanceId, groupName, startTime, endTime, maxCount);
    }

    public DLQMessagePageVO listMessages(String instanceId, String groupName, Long startTime, Long endTime,
                                          int page, int pageSize) {
        requireApacheInstance(instanceId);
        validateResendRequest(groupName, startTime, endTime);
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(400, "Invalid page or pageSize");
        }
        return dlqProvider.listMessages(instanceId, groupName.trim(), startTime, endTime, page, pageSize);
    }

    public DLQResendResultVO resendSelectedMessages(String instanceId, String groupName, Long startTime,
                                                      Long endTime, String targetTopic,
                                                      List<DLQMessageRefDTO> messages) {
        requireApacheInstance(instanceId);
        validateResendRequest(groupName, startTime, endTime);
        if (messages == null || messages.isEmpty() || messages.size() > MAX_SELECTED_MESSAGES) {
            throw new BusinessException(400, "messages must contain between 1 and 100 entries");
        }
        return dlqProvider.resendSelectedMessages(instanceId, groupName.trim(), startTime, endTime, targetTopic,
                messages);
    }

    private void requireApacheInstance(String instanceId) {
        providerRegistry.byInstanceId(instanceId).ifPresent(provider -> {
            if (provider.vendor() != InstanceVendor.APACHE) {
                throw new BusinessException(501, "DLQ operations are not supported for cloud instances");
            }
        });
    }

    private void validateResendRequest(String groupName, Long startTime, Long endTime) {
        if (!StringUtils.hasText(groupName)) {
            throw new BusinessException(400, "groupName is required");
        }
        if ((startTime == null) != (endTime == null)) {
            throw new BusinessException(400, "startTime and endTime must be provided together");
        }
        if (startTime == null) {
            return;
        }
        if (startTime <= 0 || endTime <= 0) {
            throw new BusinessException(400, "startTime and endTime must be positive");
        }
        if (endTime < startTime) {
            throw new BusinessException(400, "endTime must not be earlier than startTime");
        }
    }
}
