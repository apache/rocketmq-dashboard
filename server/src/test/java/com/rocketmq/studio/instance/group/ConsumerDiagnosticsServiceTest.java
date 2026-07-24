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

package com.rocketmq.studio.instance.group;

import com.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsumerDiagnosticsServiceTest {

    @Mock
    private ConsumerDiagnosticsProvider diagnosticsProvider;

    @InjectMocks
    private ConsumerDiagnosticsService diagnosticsService;

    @Test
    void getConsumerStackShouldDelegateToProvider() {
        ConsumerStackTraceVO stackTrace = ConsumerStackTraceVO.builder()
                .groupName("cg-orders")
                .clientId("client-1")
                .capturedAt(LocalDateTime.now())
                .threadCount(0)
                .threads(List.of())
                .build();

        when(diagnosticsProvider.getConsumerStack("cg-orders", "client-1")).thenReturn(stackTrace);

        ConsumerStackTraceVO result = diagnosticsService.getConsumerStack("cg-orders", "client-1");

        assertThat(result.getGroupName()).isEqualTo("cg-orders");
        assertThat(result.getClientId()).isEqualTo("client-1");
        verify(diagnosticsProvider).getConsumerStack("cg-orders", "client-1");
    }

    @Test
    void getConsumerStackShouldRejectBlankGroupName() {
        assertThatThrownBy(() -> diagnosticsService.getConsumerStack(" ", "client-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("groupName is required");
    }

    @Test
    void getConsumerStackShouldRejectBlankClientId() {
        assertThatThrownBy(() -> diagnosticsService.getConsumerStack("cg-orders", " "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("clientId is required");
    }
}
