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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.util.JsonUtil;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
@Slf4j
public class TestController {

    private static final String TEST_TOPIC = "TestTopic";

    private final RMQConfigure rMQConfigure;

    @GetMapping(value = "/runTask.do")
    public Object list() throws MQClientException {
        DefaultMQPushConsumer consumer = getDefaultMQPushConsumer();
        consumer.start();
        final DefaultMQProducer producer = new DefaultMQProducer(TEST_TOPIC + "Group");
        producer.setInstanceName(String.valueOf(System.currentTimeMillis()));
        producer.setNamesrvAddr(rMQConfigure.getNamesrvAddr());
        producer.start();

        new Thread(() -> {

            int i = 0;
            while (true) {
                try {
                    Message msg = new Message(TEST_TOPIC,
                            "TagA" + i,
                            "KEYS" + i,
                            ("Hello RocketMQ " + i).getBytes()
                    );
                    Thread.sleep(1000L);
                    SendResult sendResult = producer.send(msg);
                    log.info("sendMessage={}", JsonUtil.objectToString(sendResult));
                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        Thread.sleep(1000);
                    } catch (Exception ignore) {
                    }
                }
            }
        }).start();
        return true;
    }

    private DefaultMQPushConsumer getDefaultMQPushConsumer() throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(TEST_TOPIC + "Group");
        consumer.setNamesrvAddr(rMQConfigure.getNamesrvAddr());
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.subscribe(TEST_TOPIC, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            log.info("receiveMessage msgSize={}", msgs.size());
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        return consumer;
    }
}
