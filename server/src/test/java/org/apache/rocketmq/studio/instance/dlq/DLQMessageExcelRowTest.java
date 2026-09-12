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

import com.alibaba.excel.EasyExcel;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DLQMessageExcelRowTest {

    @Test
    void shouldExportTheMessageRedeliveryCountTest() {
        DLQMessageVO message = DLQMessageVO.builder()
                .msgId("msg-1")
                .topic("%DLQ%group-1")
                .queueId(7)
                .offset(17L)
                .storeTime(1_700_000_000_000L)
                .reconsumeTimes(3)
                .keys("order-1")
                .body("payload")
                .build();

        Map<String, String> cells = firstDataRowByColumnName(List.of(message));

        assertThat(cells)
                .containsEntry("Message ID", "msg-1")
                .containsEntry("Reconsume Times", "3");
    }

    @Test
    void shouldPreserveBinaryBodyInBase64ColumnTest() {
        DLQMessageVO message = DLQMessageVO.builder()
                .msgId("binary-msg")
                .storeTime(1_700_000_000_000L)
                .bodyBase64("wyg=")
                .build();

        Map<String, String> cells = firstDataRowByColumnName(List.of(message));

        assertThat(cells.get("Body")).isNull();
        assertThat(cells).containsEntry("Body Base64", "wyg=");
    }

    @Test
    void shouldClampAnOversizedBodyOnACodePointBoundaryTest() {
        // Asserted on the row rather than on a written sheet: POI sanitises an unpaired surrogate
        // on the way out, so only the in-memory cell shows whether the cut split a pair. The astral
        // run reaches index 32753, putting a high surrogate exactly at the 32752 cut point.
        DLQMessageVO message = DLQMessageVO.builder()
                .msgId("msg-huge")
                .storeTime(1_700_000_000_000L)
                .body("\uD835\uDC00".repeat(16_377) + "x".repeat(40_000))
                .build();

        String cell = DLQMessageExcelRow.from(message).getBody();

        assertThat(cell).hasSize(32_766).endsWith("...[truncated]");
        assertThat(cell.codePoints()).noneMatch(codePoint -> codePoint >= 0xD800 && codePoint <= 0xDFFF);
    }

    private static Map<String, String> firstDataRowByColumnName(List<DLQMessageVO> messages) {
        List<Map<Integer, String>> rows = readBack(messages);
        Map<Integer, String> header = rows.get(0);
        Map<Integer, String> values = rows.get(1);
        Map<String, String> cells = new HashMap<>();
        header.forEach((column, name) -> cells.put(name, values.get(column)));
        return cells;
    }

    private static List<Map<Integer, String>> readBack(List<DLQMessageVO> messages) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        EasyExcel.write(output, DLQMessageExcelRow.class)
                .sheet("dlq")
                .doWrite(messages.stream().map(DLQMessageExcelRow::from).toList());
        return EasyExcel.read(new ByteArrayInputStream(output.toByteArray()))
                .sheet()
                .headRowNumber(0)
                .doReadSync();
    }
}
