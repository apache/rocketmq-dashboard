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

package com.rocketmq.studio.instance.group;

import com.rocketmq.studio.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ConsumerDiagnosticsService {

    private final ConsumerDiagnosticsProvider diagnosticsProvider;

    public ConsumerStackTraceVO getConsumerStack(String groupName, String clientId) {
        if (!StringUtils.hasText(groupName)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "groupName is required");
        }
        if (!StringUtils.hasText(clientId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "clientId is required");
        }
        return diagnosticsProvider.getConsumerStack(groupName, clientId);
    }
}
