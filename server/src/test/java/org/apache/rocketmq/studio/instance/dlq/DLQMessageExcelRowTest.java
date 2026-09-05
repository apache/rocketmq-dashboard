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

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DLQMessageExcelRow#from(DLQMessageVO)}, the mapping used when a
 * dead-letter message (or selected batch) is exported to Excel.
 */
class DLQMessageExcelRowTest {

    private static final DateTimeFormatter STORE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Test
    void mapsEveryFieldAndFormatsTheStoreTime() {
        long storeTime = 1_700_000_000_000L;
        DLQMessageVO vo = DLQMessageVO.builder()
                .msgId("msg-1")
                .topic("DLQ_test")
                .queueId(3)
                .offset(42L)
                .storeTime(storeTime)
                .keys("keyA keyB")
                .body("payload")
                .build();

        DLQMessageExcelRow row = DLQMessageExcelRow.from(vo);

        assertThat(row.getMsgId()).isEqualTo("msg-1");
        assertThat(row.getTopic()).isEqualTo("DLQ_test");
        assertThat(row.getQueueId()).isEqualTo(3);
        assertThat(row.getOffset()).isEqualTo(42L);
        assertThat(row.getStoreTime()).isEqualTo(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(storeTime), ZoneId.systemDefault()).format(STORE_TIME_FORMAT));
        assertThat(row.getKeys()).isEqualTo("keyA keyB");
        assertThat(row.getBody()).isEqualTo("payload");
    }

    @Test
    void passesThroughNullOptionalTextFields() {
        DLQMessageVO vo = DLQMessageVO.builder()
                .msgId("msg-2")
                .topic("DLQ_test")
                .queueId(0)
                .offset(0L)
                .storeTime(0L)
                .build();

        DLQMessageExcelRow row = DLQMessageExcelRow.from(vo);

        assertThat(row.getKeys()).isNull();
        assertThat(row.getBody()).isNull();
        assertThat(row.getStoreTime()).isEqualTo(LocalDateTime.ofInstant(
                Instant.EPOCH, ZoneId.systemDefault()).format(STORE_TIME_FORMAT));
    }
}
