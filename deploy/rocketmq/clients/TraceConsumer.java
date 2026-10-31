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

import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;

public class TraceConsumer {
    public static void main(String[] args) throws Exception {
        String namesrv = System.getenv().getOrDefault("NAMESRV_ADDR", "nameserver:9876");
        String topic = System.getenv().getOrDefault("TOPIC", "StudioTest");

        // enableMsgTrace=true 开启消息轨迹，轨迹 topic 为 null 时使用默认 RMQ_SYS_TRACE_TOPIC
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("studio-trace-consumer", true, null);
        consumer.setNamesrvAddr(namesrv);
        consumer.subscribe(topic, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, ctx) -> {
            msgs.forEach(m -> System.out.println(
                "consume " + m.getMsgId() + " body=" + new String(m.getBody(), StandardCharsets.UTF_8)));
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        System.out.println("consumer started, topic=" + topic);
        Thread.currentThread().join();
    }
}
