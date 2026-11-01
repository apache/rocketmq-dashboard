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

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

public class TraceProducer {
    public static void main(String[] args) throws Exception {
        String namesrv = System.getenv().getOrDefault("NAMESRV_ADDR", "nameserver:9876");
        String topic = System.getenv().getOrDefault("TOPIC", "StudioTest");
        long intervalMs = Long.parseLong(System.getenv().getOrDefault("SEND_INTERVAL_MS", "1000"));

        // enableMsgTrace=true 开启消息轨迹，轨迹 topic 为 null 时使用默认 RMQ_SYS_TRACE_TOPIC
        DefaultMQProducer producer = new DefaultMQProducer("studio-trace-producer", true, null);
        producer.setNamesrvAddr(namesrv);
        producer.start();
        System.out.println("producer started, topic=" + topic + ", interval=" + intervalMs + "ms");

        long i = 0;
        while (true) {
            try {
                Message msg = new Message(topic, "TagA", ("studio-msg-" + i).getBytes());
                // 设置业务 Key，支持 queryMsgByKey 按 Key 检索
                msg.setKeys("studio-key-" + i);
                SendResult result = producer.send(msg);
                System.out.println("send #" + i + " " + result.getSendStatus() + " " + result.getMsgId() + " key=studio-key-" + i);
            } catch (Exception e) {
                System.out.println("send #" + i + " failed: " + e.getMessage());
            }
            i++;
            Thread.sleep(intervalMs);
        }
    }
}
