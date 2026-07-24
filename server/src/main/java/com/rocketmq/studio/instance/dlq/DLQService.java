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
package com.rocketmq.studio.instance.dlq;

import com.rocketmq.studio.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DLQService {

    private final DLQProvider dlqProvider;

    public List<DLQGroupVO> listDLQGroups(String clusterId) {
        log.info("Listing DLQ groups for cluster: {}", clusterId);
        return dlqProvider.listDLQGroups(clusterId);
    }

    public void resendMessages(String groupName, Long startTime, Long endTime, String targetTopic) {
        validateResendRequest(groupName, startTime, endTime);
        log.info("Resending DLQ messages: group={}, targetTopic={}", groupName, targetTopic);
        dlqProvider.resendMessages(groupName, startTime, endTime, targetTopic);
    }

    private void validateResendRequest(String groupName, Long startTime, Long endTime) {
        if (!StringUtils.hasText(groupName)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "groupName is required");
        }
        if ((startTime == null) != (endTime == null)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST.value(), "startTime and endTime must be provided together");
        }
        if (startTime == null) {
            return;
        }
        if (startTime <= 0 || endTime <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST.value(), "startTime and endTime must be positive");
        }
        if (endTime < startTime) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST.value(), "endTime must not be earlier than startTime");
        }
    }
}
