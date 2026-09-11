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

import java.util.List;

/**
 * Message query outcome with a provider truncation signal.
 *
 * @param messages rows returned within the provider's bounded result budget
 * @param mayBeTruncated true when the provider stopped because its result budget was reached,
 *         not because the query was exhausted
 */
public record MessageQueryResult(
        List<MessageRecordVO> messages,
        boolean mayBeTruncated) {

    public MessageQueryResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public static MessageQueryResult complete(List<MessageRecordVO> messages) {
        return new MessageQueryResult(messages, false);
    }

    public static MessageQueryResult truncated(List<MessageRecordVO> messages) {
        return new MessageQueryResult(messages, true);
    }
}
