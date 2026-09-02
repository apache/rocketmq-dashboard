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
        String normalizedGroupName = requireGroupName(groupName);
        validateTimeRange(startTime, endTime);
        String normalizedTargetTopic = normalizeOptional(targetTopic);
        log.info("Resending DLQ messages: group={}, targetTopic={}",
                normalizedGroupName, normalizedTargetTopic);
        return dlqProvider.resendMessages(
                instanceId, normalizedGroupName, startTime, endTime, normalizedTargetTopic);
    }

    public DLQExportResultVO exportMessages(String instanceId, String groupName, Long startTime, Long endTime,
                                            Integer maxCount) {
        requireApacheInstance(instanceId);
        String normalizedGroupName = requireGroupName(groupName);
        validateTimeRange(startTime, endTime);
        log.info("Exporting DLQ messages: group={}, maxCount={}", normalizedGroupName, maxCount);
        return dlqProvider.exportMessages(instanceId, normalizedGroupName, startTime, endTime, maxCount);
    }

    public PageResult<DLQMessageVO> listMessages(String instanceId, String groupName, Long startTime, Long endTime,
                                                 int page, int pageSize) {
        requireApacheInstance(instanceId);
        String normalizedGroupName = requireGroupName(groupName);
        validateTimeRange(startTime, endTime);
        log.info("Listing DLQ messages: group={}, page={}, pageSize={}", normalizedGroupName, page, pageSize);
        return dlqProvider.listMessages(
                instanceId, normalizedGroupName, startTime, endTime, page, pageSize);
    }

    public DLQResendResultVO resendSelectedMessages(String instanceId, String groupName, List<String> msgIds,
                                                    String targetTopic) {
        requireApacheInstance(instanceId);
        String normalizedGroupName = requireGroupName(groupName);
        if (msgIds == null || msgIds.isEmpty()) {
            throw new BusinessException(400, "At least one msgId is required");
        }
        if (msgIds.size() > MAX_SELECTED_MESSAGES) {
            throw new BusinessException(400, "At most 100 msgIds are allowed per resend");
        }
        List<String> normalizedMsgIds = normalizeMsgIds(msgIds);
        String normalizedTargetTopic = normalizeOptional(targetTopic);
        log.info("Resending selected DLQ messages: group={}, count={}, targetTopic={}",
                normalizedGroupName, normalizedMsgIds.size(), normalizedTargetTopic);
        return dlqProvider.resendMessages(
                instanceId, normalizedGroupName, normalizedMsgIds, normalizedTargetTopic);
    }

    public DLQExcelExportResultVO exportExcel(String instanceId, String groupName, Long startTime, Long endTime,
                                              List<String> msgIds) {
        requireApacheInstance(instanceId);
        String normalizedGroupName = requireGroupName(groupName);
        validateTimeRange(startTime, endTime);
        if (msgIds != null && msgIds.size() > MAX_SELECTED_MESSAGES) {
            throw new BusinessException(400, "At most 100 msgIds are allowed per export");
        }
        List<String> normalizedMsgIds = msgIds == null ? null : normalizeMsgIds(msgIds);
        log.info("Exporting DLQ messages as Excel: group={}, selected={}", normalizedGroupName,
                normalizedMsgIds == null ? 0 : normalizedMsgIds.size());
        return dlqProvider.exportExcel(
                instanceId, normalizedGroupName, startTime, endTime, normalizedMsgIds);
    }

    private void requireApacheInstance(String instanceId) {
        providerRegistry.byInstanceId(instanceId).ifPresent(provider -> {
            if (provider.vendor() != InstanceVendor.APACHE) {
                throw new BusinessException(501, "DLQ operations are not supported for cloud instances");
            }
        });
    }

    private String requireGroupName(String groupName) {
        if (!StringUtils.hasText(groupName)) {
            throw new BusinessException(400, "groupName is required");
        }
        return groupName.trim();
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> normalizeMsgIds(List<String> msgIds) {
        return msgIds.stream()
                .map(msgId -> {
                    if (!StringUtils.hasText(msgId)) {
                        throw new BusinessException(400, "msgId must not be blank");
                    }
                    return msgId.trim();
                })
                .toList();
    }

    private void validateTimeRange(Long startTime, Long endTime) {
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
