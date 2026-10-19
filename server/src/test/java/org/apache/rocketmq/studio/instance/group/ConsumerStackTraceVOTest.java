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

package org.apache.rocketmq.studio.instance.group;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ConsumerStackTraceVO}, the envelope of a captured consumer stack: who was
 * captured (group, client), when, and the threads — with {@code threadCount} denormalising
 * the count the diagnostics header badges by next to the full thread list. All construction
 * paths must agree so the count and the list never disagree by accident.
 */
class ConsumerStackTraceVOTest {

    @Test
    void aCapturedStackCarriesTheGroupClientAndThreads() {
        ConsumerThreadStackVO thread = ConsumerThreadStackVO.builder()
                .threadName("ConsumeMessageThread_1")
                .threadId(42L)
                .state("WAITING")
                .stackTrace(List.of("java.base/java.lang.Object.wait(Native Method)"))
                .build();

        ConsumerStackTraceVO stack = ConsumerStackTraceVO.builder()
                .groupName("order-consumer-group")
                .clientId("consumer@192.168.1.20")
                .capturedAt(LocalDateTime.of(2026, 10, 13, 12, 0))
                .threadCount(1)
                .threads(List.of(thread))
                .build();

        assertThat(stack.getGroupName()).isEqualTo("order-consumer-group");
        assertThat(stack.getClientId()).isEqualTo("consumer@192.168.1.20");
        assertThat(stack.getCapturedAt()).isEqualTo(LocalDateTime.of(2026, 10, 13, 12, 0));
        assertThat(stack.getThreadCount()).isEqualTo(1);
        assertThat(stack.getThreads()).hasSize(1);
        assertThat(stack.getThreads()).containsExactly(thread);
        assertThat(stack.getThreadCount()).isEqualTo(stack.getThreads().size());
    }

    @Test
    void allConstructionPathsAgree() {
        ConsumerStackTraceVO built = ConsumerStackTraceVO.builder()
                .groupName("order-consumer-group")
                .clientId("consumer@192.168.1.20")
                .threadCount(0)
                .threads(List.of())
                .build();
        ConsumerStackTraceVO allArgs = new ConsumerStackTraceVO(
                "order-consumer-group", "consumer@192.168.1.20", null, 0, List.of());

        assertThat(built).isEqualTo(allArgs);

        ConsumerStackTraceVO empty = new ConsumerStackTraceVO();
        assertThat(empty.getGroupName()).isNull();
        assertThat(empty.getClientId()).isNull();
        assertThat(empty.getCapturedAt()).isNull();
        assertThat(empty.getThreadCount()).isZero();
        assertThat(empty.getThreads()).isNull();
    }
}
