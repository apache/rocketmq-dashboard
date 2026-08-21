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

public interface MessageProvider {
    List<MessageRecordVO> queryMessages(String instanceId, String topic, String msgId, String tag, String key, Long startTime,
                                        Long endTime);

    TraceRecordVO getMessageTrace(String instanceId, String msgId, String topic);

    /**
     * Ask the broker to re-deliver a stored message directly to a consumer group, bypassing the
     * normal rebalance path. Equivalent to the classic dashboard {@code consumeMessageDirectly.do}.
     *
     * @param instanceId    the selected instance identifier
     * @param topic         the topic that owns the message
     * @param msgId         the stored message id
     * @param consumerGroup the consumer group that should receive the re-delivery
     * @param clientId      optional client id within the group; when empty the broker picks one
     */
    MessageResendResultVO resendMessage(String instanceId, String topic, String msgId,
                                        String consumerGroup, String clientId);
}
