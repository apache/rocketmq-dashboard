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
package org.apache.rocketmq.studio.instance.message;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Placeholder {@link MessageProvider} used until a real message query provider is connected.
 *
 * <p>Rather than returning empty results (which could mislead operators into thinking no message
 * or trace exists), both operations fail explicitly with a structured "not implemented" response.
 */
@Component
public class MessageProviderStub implements MessageProvider {

    @Override
    public List<MessageRecordVO> queryMessages(String topic, String msgId, String tag, String key, Long startTime,
                                               Long endTime) {
        throw new BusinessException(HttpStatus.NOT_IMPLEMENTED.value(),
                "Message query is not yet implemented: no message provider is connected");
    }

    @Override
    public TraceRecordVO getMessageTrace(String msgId) {
        throw new BusinessException(HttpStatus.NOT_IMPLEMENTED.value(),
                "Message trace is not yet implemented: no message provider is connected");
    }
}
