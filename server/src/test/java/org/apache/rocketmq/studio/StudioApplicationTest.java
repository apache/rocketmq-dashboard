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
package org.apache.rocketmq.studio;

import org.apache.rocketmq.studio.ops.ai.tool.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.ToolGatewayService;
import org.apache.rocketmq.studio.persistence.mapper.RmqInstanceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StudioApplicationTest {

    @Autowired
    private ToolCatalog toolCatalog;

    @Autowired
    private ToolGatewayService toolGatewayService;

    @Autowired
    private RmqInstanceMapper instanceMapper;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void applicationContextLoadsWithInitializedDevSchema() throws Exception {
        assertThat(toolCatalog.getVersion()).isEqualTo("1.0.0");
        assertThat(toolGatewayService.discover(null)).isNotEmpty();
        assertThat(instanceMapper.selectList(null)).isEmpty();

        mockMvc.perform(get("/api/instances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
