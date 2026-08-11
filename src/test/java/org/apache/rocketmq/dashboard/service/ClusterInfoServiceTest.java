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
package org.apache.rocketmq.dashboard.service;

import jakarta.annotation.PreDestroy;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.concurrent.ScheduledExecutorService;

public class ClusterInfoServiceTest {

    @Test
    public void testShutdownSchedulerOnDestroy() throws Exception {
        ClusterInfoService service = new ClusterInfoService();
        ScheduledExecutorService scheduler =
                (ScheduledExecutorService) ReflectionTestUtils.getField(service, "scheduler");
        Method shutdown = ClusterInfoService.class.getMethod("shutdown");

        Assert.assertNotNull(shutdown.getAnnotation(PreDestroy.class));
        Assert.assertNotNull(scheduler);
        Assert.assertFalse(scheduler.isShutdown());

        service.shutdown();

        Assert.assertTrue(scheduler.isShutdown());
    }
}
