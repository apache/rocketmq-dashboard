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
package org.apache.rocketmq.dashboard.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

public class DlqMessageRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testDeserializeFrontendTopicField() throws Exception {
        DlqMessageRequest request = objectMapper.readValue(
                "{\"topic\":\"retry-topic\",\"consumerGroup\":\"group\"}",
                DlqMessageRequest.class);

        Assert.assertEquals("retry-topic", request.getTopicName());
        Assert.assertEquals("group", request.getConsumerGroup());
    }

    @Test
    public void testDeserializeTopicNameField() throws Exception {
        DlqMessageRequest request = objectMapper.readValue(
                "{\"topicName\":\"retry-topic\"}", DlqMessageRequest.class);

        Assert.assertEquals("retry-topic", request.getTopicName());
    }
}
