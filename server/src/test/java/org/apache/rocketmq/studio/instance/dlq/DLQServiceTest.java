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

package org.apache.rocketmq.studio.instance.dlq;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.instance.ResourceOwnershipGuard;
import org.apache.rocketmq.studio.instance.ResourceOwnershipGuard.Kind;
import org.apache.rocketmq.studio.instance.ResourceOwnershipGuard.Resource;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DLQServiceTest {

    @Mock
    private DLQProvider dlqProvider;

    @Mock
    private InstanceProviderRegistry providerRegistry;

    @Mock
    private ResourceOwnershipGuard ownershipGuard;

    @InjectMocks
    private DLQService dlqService;

    @BeforeEach
    void ownEveryResource() {
        // The ownership check is what the resend tests are not about: let it pass by default and keep
        // the mocked provider reachable through the wrapper. Lenient because the validation-failure
        // tests never reach it.
        lenient().when(ownershipGuard.requireInstance(anyString()))
                .thenReturn(InstanceVO.builder().name("instance-1").vendor(InstanceVendor.APACHE).build());
        lenient().when(ownershipGuard.topicResource(anyString()))
                .thenAnswer(invocation -> new Resource(Kind.TOPIC, invocation.getArgument(0)));
        lenient().when(ownershipGuard.withOwned(any(), anyList(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());
    }

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
        when(dlqProvider.listDLQGroups("instance-1")).thenReturn(groups);

        List<DLQGroupVO> result = dlqService.listDLQGroups("instance-1");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getGroupName()).isEqualTo("group-1");
        assertThat(result.get(0).getDlqTopic()).isEqualTo("%DLQ%group-1");
        verify(dlqProvider).listDLQGroups("instance-1");
    }

    @Test
    void listDLQGroupsShouldReturnEmptyWhenNone() {
        when(dlqProvider.listDLQGroups("instance-2")).thenReturn(List.of());

        List<DLQGroupVO> result = dlqService.listDLQGroups("instance-2");

        assertThat(result).isEmpty();
        verify(dlqProvider).listDLQGroups("instance-2");
    }

    @Test
    void resendMessagesShouldDelegateToProvider() {
        dlqService.resendMessages("instance-1", "group-1", 1000L, 2000L, "target-topic");

        verify(dlqProvider).resendMessages("instance-1", "group-1", 1000L, 2000L, "target-topic");
    }

    @Test
    void resendMessagesShouldCheckOwnershipOfTheGroupAndTheTargetTopicTest() {
        dlqService.resendMessages("instance-1", "group-1", 1000L, 2000L, "target-topic");

        // The DLQ topic %DLQ%group-1 belongs to the group's owner, and the destination is a topic
        // write of its own: the resend must be gated on both.
        verify(ownershipGuard).withOwned(
                any(InstanceVO.class),
                eq(List.of(new Resource(Kind.GROUP, "group-1"), new Resource(Kind.TOPIC, "target-topic"))),
                any());
    }

    @Test
    void resendSelectedMessagesShouldCheckOwnershipOfTheGroupOnlyWhenNoTargetIsNamedTest() {
        dlqService.resendSelectedMessages("instance-1", "group-1", List.of("msg-1"), null);

        verify(ownershipGuard).withOwned(
                any(InstanceVO.class),
                eq(List.of(new Resource(Kind.GROUP, "group-1"))),
                any());
    }

    @Test
    void resendMessagesShouldNotReachTheProviderWhenOwnershipRefusesTest() {
        // doThrow: the when(...) form would invoke the mock and run the lenient answer below.
        doThrow(new BusinessException(409, "Resource is owned by another instance"))
                .when(ownershipGuard).withOwned(any(), anyList(), any());

        assertThatThrownBy(() ->
                dlqService.resendMessages("instance-1", "group-1", 1000L, 2000L, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(409));
        verify(dlqProvider, never()).resendMessages(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void actionsShouldNormalizeIdentifiersBeforeDelegatingTest() {
        List<String> msgIds = List.of(" msg-1 ", " msg-2 ");

        dlqService.resendMessages("instance-1", " group-1 ", 1000L, 2000L, " target-topic ");
        dlqService.exportMessages("instance-1", " group-1 ", 1000L, 2000L, 100);
        dlqService.listMessages("instance-1", " group-1 ", 1000L, 2000L, 1, 20);
        dlqService.resendSelectedMessages("instance-1", " group-1 ", msgIds, " target-topic ");
        dlqService.exportExcel("instance-1", " group-1 ", 1000L, 2000L, msgIds);

        verify(dlqProvider).resendMessages(
                "instance-1", "group-1", 1000L, 2000L, "target-topic");
        verify(dlqProvider).exportMessages("instance-1", "group-1", 1000L, 2000L, 100);
        verify(dlqProvider).listMessages("instance-1", "group-1", 1000L, 2000L, 1, 20);
        verify(dlqProvider).resendMessages(
                "instance-1", "group-1", List.of("msg-1", "msg-2"), "target-topic");
        verify(dlqProvider).exportExcel(
                "instance-1", "group-1", 1000L, 2000L, List.of("msg-1", "msg-2"));
    }

    @Test
    void resendMessagesShouldTreatBlankTargetTopicAsAbsentTest() {
        dlqService.resendMessages("instance-1", "group-1", 1000L, 2000L, "   ");

        verify(dlqProvider).resendMessages("instance-1", "group-1", 1000L, 2000L, null);
    }

    @Test
    void selectedActionsShouldRejectBlankMsgIdsTest() {
        assertThatThrownBy(() -> dlqService.resendSelectedMessages(
                "instance-1", "group-1", List.of("msg-1", " "), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("msgId must not be blank");
        assertThatThrownBy(() -> dlqService.exportExcel(
                "instance-1", "group-1", null, null, List.of("msg-1", " ")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("msgId must not be blank");

        verifyNoInteractions(dlqProvider);
    }

    @Test
    void resendMessagesShouldAcceptNullTimeRange() {
        dlqService.resendMessages("instance-1", "group-1", null, null, "target-topic");

        verify(dlqProvider).resendMessages("instance-1", "group-1", null, null, "target-topic");
    }

    @Test
    void resendMessagesShouldRejectBlankGroupName() {
        assertThatThrownBy(() -> dlqService.resendMessages("instance-1", " ", 1000L, 2000L, "target-topic"))
                .hasMessage("groupName is required");

        verifyNoInteractions(dlqProvider);
    }

    @Test
    void resendMessagesShouldRejectPartialTimeRange() {
        assertThatThrownBy(() -> dlqService.resendMessages("instance-1", "group-1", 1000L, null, "target-topic"))
                .hasMessage("startTime and endTime must be provided together");

        verifyNoInteractions(dlqProvider);
    }

    @Test
    void resendMessagesShouldRejectNonPositiveTimeRange() {
        assertThatThrownBy(() -> dlqService.resendMessages("instance-1", "group-1", 0L, 2000L, "target-topic"))
                .hasMessage("startTime and endTime must be positive");

        verifyNoInteractions(dlqProvider);
    }

    @Test
    void resendMessagesShouldRejectReversedTimeRange() {
        assertThatThrownBy(() -> dlqService.resendMessages("instance-1", "group-1", 2000L, 1000L, "target-topic"))
                .hasMessage("endTime must not be earlier than startTime");

        verifyNoInteractions(dlqProvider);
    }

    @Test
    void exportMessagesShouldDelegateToProviderTest() {
        dlqService.exportMessages("instance-1", "group-1", 1000L, 2000L, 100);

        verify(dlqProvider).exportMessages("instance-1", "group-1", 1000L, 2000L, 100);
    }

    @Test
    void exportMessagesShouldAcceptNullTimeRangeTest() {
        dlqService.exportMessages("instance-1", "group-1", null, null, null);

        verify(dlqProvider).exportMessages("instance-1", "group-1", null, null, null);
    }

    @Test
    void exportMessagesShouldRejectPartialTimeRangeTest() {
        assertThatThrownBy(() -> dlqService.exportMessages("instance-1", "group-1", 1000L, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("startTime and endTime must be provided together")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verifyNoInteractions(dlqProvider);
    }

    @Test
    void exportMessagesShouldRejectReversedTimeRangeTest() {
        assertThatThrownBy(() -> dlqService.exportMessages("instance-1", "group-1", 2000L, 1000L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("endTime must not be earlier than startTime")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verifyNoInteractions(dlqProvider);
    }

    @Test
    void listDLQGroupsShouldDelegatePagedQueryWithTrimmedSearch() {
        PageResult<DLQGroupVO> page = PageResult.of(List.of(), 0, 2, 50);
        when(dlqProvider.listDLQGroups("instance-1", "order", 2, 50)).thenReturn(page);

        PageResult<DLQGroupVO> result = dlqService.listDLQGroups("instance-1", " order ", 2, 50);

        assertThat(result).isSameAs(page);
        verify(dlqProvider).listDLQGroups("instance-1", "order", 2, 50);
    }

    @Test
    void listDLQGroupsShouldRejectInvalidPagination() {
        assertThatThrownBy(() -> dlqService.listDLQGroups("instance-1", null, 0, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid page or pageSize");
        assertThatThrownBy(() -> dlqService.listDLQGroups("instance-1", null, 1, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid page or pageSize");
        assertThatThrownBy(() -> dlqService.listDLQGroups("instance-1", null, 1, 101))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid page or pageSize");

        verifyNoInteractions(dlqProvider);
    }

    @Test
    void everyActionShouldRejectAnEmptyTimeWindowTest() {
        assertThatThrownBy(() -> dlqService.resendMessages("instance-1", "group-1", 5000L, 5000L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("startTime must be before endTime");
        assertThatThrownBy(() -> dlqService.listMessages("instance-1", "group-1", 5000L, 5000L, 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("startTime must be before endTime");
        assertThatThrownBy(() -> dlqService.exportMessages("instance-1", "group-1", 5000L, 5000L, 100))
                .isInstanceOf(BusinessException.class)
                .hasMessage("startTime must be before endTime");
        assertThatThrownBy(() -> dlqService.exportExcel("instance-1", "group-1", 5000L, 5000L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("startTime must be before endTime");

        verifyNoInteractions(dlqProvider);
    }

    @Test
    void everyActionShouldStillAcceptAOneMillisecondTimeWindowTest() {
        dlqService.resendMessages("instance-1", "group-1", 5000L, 5001L, null);
        dlqService.listMessages("instance-1", "group-1", 5000L, 5001L, 1, 20);
        dlqService.exportMessages("instance-1", "group-1", 5000L, 5001L, 100);
        dlqService.exportExcel("instance-1", "group-1", 5000L, 5001L, null);

        verify(dlqProvider).resendMessages("instance-1", "group-1", 5000L, 5001L, null);
        verify(dlqProvider).listMessages("instance-1", "group-1", 5000L, 5001L, 1, 20);
        verify(dlqProvider).exportMessages("instance-1", "group-1", 5000L, 5001L, 100);
        verify(dlqProvider).exportExcel("instance-1", "group-1", 5000L, 5001L, null);
    }
}
