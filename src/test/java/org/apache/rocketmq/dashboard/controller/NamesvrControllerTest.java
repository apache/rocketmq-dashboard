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

import org.apache.rocketmq.dashboard.service.impl.OpsServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

public class NamesvrControllerTest extends BaseControllerTest {

    @InjectMocks
    private NamesvrController namesvrController;

    @Spy
    private OpsServiceImpl opsService;

    @Test
    public void testNsaddr() throws Exception {
        final String url = "/rocketmq/nsaddr.query";
        {
            super.mockRmqConfigure();
        }
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);
        String namesrvAddr = perform.andReturn().getResponse().getContentAsString();
        Assertions.assertEquals(namesrvAddr, "127.0.0.1:9876");
    }

    @Override protected Object getTestController() {
        return namesvrController;
    }
}
