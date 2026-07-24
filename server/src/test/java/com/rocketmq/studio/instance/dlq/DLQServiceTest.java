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

package com.rocketmq.studio.instance.dlq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DLQServiceTest {

    @Mock
    private DLQProvider dlqProvider;

    @InjectMocks
    private DLQService dlqService;

    @Test
    void listDLQGroupsShouldReturnGroupsFromProvider() {
        List<DLQGroupVO> groups = List.of(
                DLQGroupVO.builder()
                        .groupName("group-1")
                        .dlqTopic("%DLQ%group-1")
                        .messageCount(10)
                        .status("ACTIVE")
                        .build(),
                DLQGroupVO.builder()
                        .groupName("group-2")
                        .dlqTopic("%DLQ%group-2")
                        .messageCount(5)
                        .status("ACTIVE")
                        .build()
        );
        when(dlqProvider.listDLQGroups("cluster-1")).thenReturn(groups);

        List<DLQGroupVO> result = dlqService.listDLQGroups("cluster-1");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getGroupName()).isEqualTo("group-1");
        assertThat(result.get(0).getDlqTopic()).isEqualTo("%DLQ%group-1");
        verify(dlqProvider).listDLQGroups("cluster-1");
    }

    @Test
    void listDLQGroupsShouldReturnEmptyWhenNone() {
        when(dlqProvider.listDLQGroups("cluster-2")).thenReturn(List.of());

        List<DLQGroupVO> result = dlqService.listDLQGroups("cluster-2");

        assertThat(result).isEmpty();
        verify(dlqProvider).listDLQGroups("cluster-2");
    }

    @Test
    void resendMessagesShouldDelegateToProvider() {
        dlqService.resendMessages("group-1", 1000L, 2000L, "target-topic");

        verify(dlqProvider).resendMessages("group-1", 1000L, 2000L, "target-topic");
    }

    @Test
    void resendMessagesShouldAcceptNullTimeRange() {
        dlqService.resendMessages("group-1", null, null, "target-topic");

        verify(dlqProvider).resendMessages("group-1", null, null, "target-topic");
    }

    @Test
    void resendMessagesShouldRejectBlankGroupName() {
        assertThatThrownBy(() -> dlqService.resendMessages(" ", 1000L, 2000L, "target-topic"))
                .hasMessage("groupName is required");

        verifyNoInteractions(dlqProvider);
    }

    @Test
    void resendMessagesShouldRejectPartialTimeRange() {
        assertThatThrownBy(() -> dlqService.resendMessages("group-1", 1000L, null, "target-topic"))
                .hasMessage("startTime and endTime must be provided together");

        verifyNoInteractions(dlqProvider);
    }

    @Test
    void resendMessagesShouldRejectNonPositiveTimeRange() {
        assertThatThrownBy(() -> dlqService.resendMessages("group-1", 0L, 2000L, "target-topic"))
                .hasMessage("startTime and endTime must be positive");

        verifyNoInteractions(dlqProvider);
    }

    @Test
    void resendMessagesShouldRejectReversedTimeRange() {
        assertThatThrownBy(() -> dlqService.resendMessages("group-1", 2000L, 1000L, "target-topic"))
                .hasMessage("endTime must not be earlier than startTime");

        verifyNoInteractions(dlqProvider);
    }
}
