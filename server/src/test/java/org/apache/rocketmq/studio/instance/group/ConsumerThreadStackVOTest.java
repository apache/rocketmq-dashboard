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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ConsumerThreadStackVO}, one thread of a captured consumer stack: its name,
 * id and state, how long it spent blocked and waiting, and the stack trace lines under it.
 * The diagnostics detail view renders exactly these columns, so all construction paths must
 * agree and the stack trace must travel as an ordered list, not a joined string.
 */
class ConsumerThreadStackVOTest {

    @Test
    void aThreadCarriesItsIdentityAndItsStackTrace() {
        ConsumerThreadStackVO thread = ConsumerThreadStackVO.builder()
                .threadName("ConsumeMessageThread_1")
                .threadId(42L)
                .state("WAITING")
                .blockedTime(0)
                .waitedTime(1200)
                .stackTrace(List.of(
                        "java.base/java.lang.Object.wait(Native Method)",
                        "org.apache.rocketmq.client.impl.consumer.ProcessQueue.popMessage(ProcessQueue.java:210)"))
                .build();

        assertThat(thread.getThreadName()).isEqualTo("ConsumeMessageThread_1");
        assertThat(thread.getThreadId()).isEqualTo(42L);
        assertThat(thread.getState()).isEqualTo("WAITING");
        assertThat(thread.getBlockedTime()).isZero();
        assertThat(thread.getWaitedTime()).isEqualTo(1200);
        assertThat(thread.getStackTrace()).hasSize(2);
        assertThat(thread.getStackTrace()).first().isEqualTo("java.base/java.lang.Object.wait(Native Method)");
    }

    @Test
    void allConstructionPathsAgree() {
        ConsumerThreadStackVO built = ConsumerThreadStackVO.builder()
                .threadName("ConsumeMessageThread_2")
                .threadId(43L)
                .state("RUNNABLE")
                .blockedTime(0)
                .waitedTime(0)
                .stackTrace(List.of())
                .build();
        ConsumerThreadStackVO allArgs = new ConsumerThreadStackVO(
                "ConsumeMessageThread_2", 43L, "RUNNABLE", 0, 0, List.of());

        assertThat(built).isEqualTo(allArgs);

        ConsumerThreadStackVO empty = new ConsumerThreadStackVO();
        assertThat(empty.getThreadName()).isNull();
        assertThat(empty.getThreadId()).isZero();
        assertThat(empty.getState()).isNull();
        assertThat(empty.getBlockedTime()).isZero();
        assertThat(empty.getWaitedTime()).isZero();
        assertThat(empty.getStackTrace()).isNull();
    }
}
