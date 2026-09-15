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
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.apache.rocketmq.studio.instance.message.MessageQueryPageVO;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageOutput;

import java.util.List;

public record MessageQueryOutput(
        @JsonUnwrapped PageOutput<Item> pageOutput,
        boolean resultMayBeTruncated) {

    public static MessageQueryOutput fromPage(MessageQueryPageVO result, boolean includeBody) {
        return new MessageQueryOutput(
                new PageOutput<>(result.getPage(), result.getSize(), result.getTotal(),
                        result.getItems().stream()
                                .map(message -> Item.from(message, includeBody))
                                .toList()),
                result.isResultMayBeTruncated());
    }

    public static MessageQueryOutput fromUniqueKey(
            List<MessageRecordVO> messages, int page, int pageSize, boolean includeBody) {
        long offset = (long) (page - 1) * pageSize;
        int from = (int) Math.min(offset, messages.size());
        int to = Math.min(from + pageSize, messages.size());
        return new MessageQueryOutput(
                new PageOutput<>(page, pageSize, messages.size(), messages.subList(from, to).stream()
                        .map(message -> Item.from(message, includeBody))
                        .toList()),
                false);
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
