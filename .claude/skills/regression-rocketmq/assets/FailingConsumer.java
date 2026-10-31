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

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;

/**
 * Always-failing push consumer used to drive messages into the dead-letter queue.
 * Retries follow the concurrent-consume delay ladder (10s, 30s, ...), so with
 * MAX_RECONSUME_TIMES=2 a message lands in %DLQ&lt;group&gt; roughly 40s after first delivery.
 */
public class FailingConsumer {
    public static void main(String[] args) throws Exception {
        String namesrv = System.getenv().getOrDefault("NAMESRV_ADDR", "rmq-nameserver:9876");
        String topic = System.getenv().getOrDefault("TOPIC", "E2EDlq");
        String group = System.getenv().getOrDefault("GROUP", "E2EDlqGid");
        int maxReconsume = Integer.parseInt(System.getenv().getOrDefault("MAX_RECONSUME_TIMES", "2"));

        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group);
        consumer.setNamesrvAddr(namesrv);
        consumer.setMaxReconsumeTimes(maxReconsume);
        // The messages are produced before this consumer exists; a fresh group defaults to
        // CONSUME_FROM_LAST_OFFSET and would silently skip them.
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.subscribe(topic, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, ctx) -> {
            msgs.forEach(m -> System.out.println(
                "fail-consume " + m.getMsgId() + " reconsumeTimes=" + m.getReconsumeTimes()));
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        });
        consumer.start();
        System.out.println("failing consumer started, topic=" + topic + " group=" + group
            + " maxReconsumeTimes=" + maxReconsume);
        Thread.currentThread().join();
    }
}
