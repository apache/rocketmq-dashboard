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
package org.apache.rocketmq.studio.ops.ai.tool.contract.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.rocketmq.studio.instance.message.MessageQueryPageVO;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;

import java.util.List;

public record MessageQueryOutput(
        List<Item> items,
        boolean resultMayBeTruncated,
        long skippedCount) {

    public static MessageQueryOutput fromPage(MessageQueryPageVO result, boolean includeBody) {
        long skippedCount = Math.max(0, result.getTotal() - result.getItems().size());
        return new MessageQueryOutput(
                result.getItems().stream()
                        .map(message -> Item.from(message, includeBody))
                        .toList(),
                result.isResultMayBeTruncated() || skippedCount > 0,
                skippedCount);
    }

    public static MessageQueryOutput fromUniqueKey(
            List<MessageRecordVO> messages, int limit, boolean includeBody) {
        int to = Math.min(limit, messages.size());
        long skippedCount = messages.size() - to;
        return new MessageQueryOutput(
                messages.subList(0, to).stream()
                        .map(message -> Item.from(message, includeBody))
                        .toList(),
                skippedCount > 0,
                skippedCount);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(
            String msgId,
            String topic,
            String tag,
            String key,
            long storeTime,
            String storeHost,
            String bornHost,
            String body,
            String bodyEncoding,
            Boolean bodyTruncated,
            int size) {

        static Item from(MessageRecordVO message, boolean includeBody) {
            return new Item(
                    message.getMsgId(),
                    message.getTopic(),
                    message.getTag(),
                    message.getKey(),
                    message.getStoreTime(),
                    message.getStoreHost(),
                    message.getBornHost(),
                    includeBody ? message.getBody() : null,
                    includeBody ? message.getBodyEncoding() : null,
                    includeBody ? message.isBodyTruncated() : null,
                    message.getSize());
        }
    }
}
