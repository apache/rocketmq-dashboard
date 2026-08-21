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
package org.apache.rocketmq.studio.instance.message;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.persistence.entity.RmqMessageQuery;
import org.apache.rocketmq.studio.persistence.entity.RmqTraceQuery;
import org.apache.rocketmq.studio.persistence.mapper.RmqMessageQueryMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTraceQueryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "studio.auth.login-required=true")
class QueryHistoryServiceIntegrationTest {

    @Autowired
    private QueryHistoryService queryHistoryService;

    @Autowired
    private RmqMessageQueryMapper messageQueryMapper;

    @Autowired
    private RmqTraceQueryMapper traceQueryMapper;

    @Test
    void queryHistoryKeepsFullLengthUsernamesTest() {
        // Usernames may be up to 128 characters; the query-history owner columns must
        // store the full value instead of rejecting or truncating it.
        String longUsername = "long".repeat(32);
        assertThat(longUsername).hasSize(128);
        try {
            AuthenticatedUserContext.setUser(longUsername, true);
            queryHistoryService.recordMessageQuery("qh-cluster", "TOPIC", "qh-topic",
                    null, null, null, null, null, 3);
            queryHistoryService.recordTraceQuery("qh-cluster", "qh-msg-id", "qh-topic", 2, 1);

            PageResult<MessageQueryHistoryVO> messageHistory =
                    queryHistoryService.listMessageQueries("qh-cluster", null, null, 1, 20);
            assertThat(messageHistory.getItems()).anySatisfy(item ->
                    assertThat(item.getQueriedBy()).isEqualTo(longUsername));

            PageResult<TraceQueryHistoryVO> traceHistory =
                    queryHistoryService.listTraceQueries("qh-cluster", null, 1, 20);
            assertThat(traceHistory.getItems()).anySatisfy(item ->
                    assertThat(item.getQueriedBy()).isEqualTo(longUsername));
        } finally {
            AuthenticatedUserContext.clear();
            messageQueryMapper.delete(new QueryWrapper<RmqMessageQuery>()
                    .eq("queried_by", longUsername));
            traceQueryMapper.delete(new QueryWrapper<RmqTraceQuery>()
                    .eq("queried_by", longUsername));
        }
    }
}
