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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.PageResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Fallback {@link DLQProvider} used only by tests; not registered as a Spring bean. The live
 * implementation is {@code RocketMQDLQProvider}.
 */
@Slf4j
public class DLQProviderStub implements DLQProvider {

    @Override
    public List<DLQGroupVO> listDLQGroups(String instanceId) {
        log.warn("DLQProviderStub.listDLQGroups called but no real DLQ provider is configured. instanceId={}",
                instanceId);
        throw unsupported();
    }

    @Override
    public PageResult<DLQGroupVO> listDLQGroups(String instanceId, String search, int page, int pageSize) {
        log.warn("DLQProviderStub.listDLQGroups(paged) called but no real DLQ provider is configured. "
                + "instanceId={}, page={}, pageSize={}", instanceId, page, pageSize);
        throw unsupported();
    }

    @Override
    public DLQResendResultVO resendMessages(String instanceId, String groupName, Long startTime, Long endTime,
                                             String targetTopic) {
        log.warn("DLQProviderStub.resendMessages called but no real DLQ provider is configured. group={}, targetTopic={}",
                groupName, targetTopic);
        throw unsupported();
    }

    @Override
    public DLQExportResultVO exportMessages(String instanceId, String groupName, Long startTime, Long endTime,
                                            Integer maxCount) {
        log.warn("DLQProviderStub.exportMessages called but no real DLQ provider is configured. group={}", groupName);
        throw unsupported();
    }

    @Override
    public PageResult<DLQMessageVO> listMessages(String instanceId, String groupName, Long startTime, Long endTime,
                                                 int page, int pageSize) {
        log.warn("DLQProviderStub.listMessages called but no real DLQ provider is configured. group={}", groupName);
        throw unsupported();
    }

    @Override
    public DLQResendResultVO resendMessages(String instanceId, String groupName, List<String> msgIds,
                                             String targetTopic) {
        log.warn("DLQProviderStub.resendMessages(selected) called but no real DLQ provider is configured. group={}",
                groupName);
        throw unsupported();
    }

    @Override
    public DLQExcelExportResultVO exportExcel(String instanceId, String groupName, Long startTime, Long endTime,
                                              List<String> msgIds, java.io.OutputStream out) {
        log.warn("DLQProviderStub.exportExcel called but no real DLQ provider is configured. group={}", groupName);
        throw unsupported();
    }

    private BusinessException unsupported() {
        return new BusinessException(501, "DLQ provider is not configured");
    }
}
