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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Placeholder {@link DLQProvider} used until a real DLQ provider is connected.
 *
 * <p>Rather than returning sample DLQ groups (which could mislead operators into believing real
 * DLQ data exists) or silently accepting resend requests as a no-op, both operations fail
 * explicitly with a structured "not implemented" response.
 */
@Component
public class DLQProviderStub implements DLQProvider {

    @Override
    public List<DLQGroupVO> listDLQGroups(String clusterId) {
        throw new BusinessException(HttpStatus.NOT_IMPLEMENTED.value(),
                "DLQ listing is not yet implemented: no DLQ provider is connected");
    }

    @Override
    public void resendMessages(String groupName, Long startTime, Long endTime, String targetTopic) {
        throw new BusinessException(HttpStatus.NOT_IMPLEMENTED.value(),
                "DLQ message resend is not yet implemented: no DLQ provider is connected");
    }
}
