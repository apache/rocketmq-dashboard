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
package org.apache.rocketmq.dashboard.config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Test;

public class CollectExecutorConfigTest {

    private final static int COUNT = 100;

    @Test
    public void testCollectExecutor() throws Exception {
        AtomicInteger num = new AtomicInteger(0);
        CollectExecutorConfig config = new CollectExecutorConfig();
        config.setCoreSize(10);
        config.setMaxSize(10);
        config.setQueueSize(500);
        config.setKeepAliveTime(3000);
        ExecutorService collectExecutor = config.collectExecutor(config);
        Assert.assertNotNull(collectExecutor);
        CountDownLatch countDownLatch = new CountDownLatch(COUNT);
        for (int i = 0; i < COUNT; i++) {
            collectExecutor.submit(() -> {
                num.getAndIncrement();
                countDownLatch.countDown();
            });
        }
        countDownLatch.await();
        System.out.println(collectExecutor.isTerminated());
        Assert.assertEquals(COUNT, num.get());
    }
}
