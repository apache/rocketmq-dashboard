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
package org.apache.rocketmq.dashboard.controller;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.dashboard.service.TopicService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class TopicControllerBrokerTest {

    @Test
    public void testExamineTopicConfigForwardsBrokerName() throws Exception {
        String topic = "topic-test";
        String brokerName = "broker-a";
        TopicConfig expected = new TopicConfig(topic);
        AtomicReference<Object[]> receivedArguments = new AtomicReference<>();
        TopicService topicService = (TopicService) Proxy.newProxyInstance(
            TopicService.class.getClassLoader(), new Class<?>[] {TopicService.class},
            (proxy, method, arguments) -> {
                if (method.getName().equals("examineTopicConfig") && method.getParameterCount() == 2) {
                    receivedArguments.set(arguments);
                    return expected;
                }
                throw new AssertionError("Unexpected service call: " + method);
            });
        TopicController controller = new TopicController();
        ReflectionTestUtils.setField(controller, "topicService", topicService);

        Object result = controller.examineTopicConfig(topic, brokerName);

        Assert.assertSame(expected, result);
        Assert.assertArrayEquals(new Object[] {topic, brokerName}, receivedArguments.get());
    }
}
