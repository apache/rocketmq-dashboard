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
package org.apache.rocketmq.studio.provider.apache;

import org.apache.rocketmq.studio.instance.topic.SendMessageVO;
import org.apache.rocketmq.studio.instance.topic.SendMessageDTO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupSettingsVO;


public interface AdminClient {
    TopicVO getTopic(String name);
    ConsumerGroupVO getConsumerGroup(String instanceId, String name);
    TopicVO createTopic(TopicVO topic);
    TopicVO updateTopic(TopicVO topic);
    void deleteTopic(String instanceId, String name);
    SendMessageVO sendMessage(SendMessageDTO request);
    ConsumerGroupVO createConsumerGroup(ConsumerGroupVO group);
    ConsumerGroupSettingsVO getConsumerGroupSettings(String instanceId, String name);
    ConsumerGroupSettingsVO updateConsumerGroupSettings(String instanceId, String name, int retryQueueNums,
                                                         int retryMaxTimes);
    void deleteConsumerGroup(String instanceId, String name);
    void resetOffset(String instanceId, String name, long timestamp, String topic);
}
