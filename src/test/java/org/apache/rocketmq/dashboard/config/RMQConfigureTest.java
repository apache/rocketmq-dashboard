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

import java.io.File;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.ErrorPageRegistrar;
import org.springframework.boot.web.server.ErrorPageRegistry;

public class RMQConfigureTest {

    private RMQConfigure rmqConfigure = new RMQConfigure();

    @Test
    public void testSet() {
        rmqConfigure.setAccessKey("rocketmq");
        rmqConfigure.setSecretKey("12345678");
        rmqConfigure.setDataPath("/tmp/rocketmq-console/data/test");
        rmqConfigure.setEnableDashBoardCollect("true");
        rmqConfigure.setIsVIPChannel("true");
        rmqConfigure.setUseTLS(true);
        rmqConfigure.setLoginRequired(true);
        rmqConfigure.setNamesrvAddr("127.0.0.1:9876");
        rmqConfigure.setTimeoutMillis(3000L);
    }

    @Test
    public void testGet() {
        testSet();
        Assert.assertEquals(rmqConfigure.getAccessKey(), "rocketmq");
        Assert.assertEquals(rmqConfigure.getSecretKey(), "12345678");
        Assert.assertTrue(rmqConfigure.isACLEnabled());
        Assert.assertTrue(rmqConfigure.isUseTLS());
        Assert.assertEquals(rmqConfigure.getDashboardCollectData(), "/tmp/rocketmq-console/data/test" + File.separator + "dashboard");
        Assert.assertEquals(rmqConfigure.getRocketMqDashboardDataPath(), "/tmp/rocketmq-console/data/test");
        Assert.assertEquals(rmqConfigure.getIsVIPChannel(), "true");
        Assert.assertTrue(rmqConfigure.isEnableDashBoardCollect());
        Assert.assertTrue(rmqConfigure.isLoginRequired());
        Assert.assertEquals(rmqConfigure.getNamesrvAddr(), "127.0.0.1:9876");
        Assert.assertEquals(rmqConfigure.getTimeoutMillis().longValue(), 3000L);
        ErrorPageRegistrar registrar = rmqConfigure.errorPageRegistrar();
        registrar.registerErrorPages(new ErrorPageRegistry() {
            @Override
            public void addErrorPages(ErrorPage... errorPages) {

            }
        });
    }
}
