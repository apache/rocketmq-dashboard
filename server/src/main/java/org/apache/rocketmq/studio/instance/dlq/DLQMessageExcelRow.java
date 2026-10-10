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

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Excel row model for dead-letter message export (single message or a selected batch).
 */
@Data
public class DLQMessageExcelRow {

    /**
     * POI rejects any cell whose text exceeds this many characters, so one oversized dead-letter
     * body would otherwise abort the whole export with a 502. The budget is counted in UTF-16
     * chars, not code points, which is why {@code TextBounds} cannot be reused here.
     */
    private static final int MAX_EXCEL_CELL_CHARS = 32_767;
    private static final String TRUNCATION_SUFFIX = "...[truncated]";

    private static final DateTimeFormatter STORE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @ExcelProperty("Message ID")
    private String msgId;
    @ExcelProperty("Topic")
    private String topic;
    @ExcelProperty("Queue ID")
    private int queueId;
    @ExcelProperty("Offset")
    private long offset;
    @ExcelProperty("Store Time")
    private String storeTime;
    @ExcelProperty("Reconsume Times")
    private int reconsumeTimes;
    @ExcelProperty("Keys")
    private String keys;
    @ExcelProperty("Body")
    private String body;
    @ExcelProperty("Body Base64")
    private String bodyBase64;

    public static DLQMessageExcelRow from(DLQMessageVO vo) {
        DLQMessageExcelRow row = new DLQMessageExcelRow();
        row.setMsgId(vo.getMsgId());
        row.setTopic(vo.getTopic());
        row.setQueueId(vo.getQueueId());
        row.setOffset(vo.getOffset());
        // Zoneless datetimes are UTC across this app (alert events, silences, outbox,
        // sessions, query history), so the export column must not shift with the JVM zone.
        row.setStoreTime(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(vo.getStoreTime()), ZoneOffset.UTC).format(STORE_TIME_FORMAT));
        row.setReconsumeTimes(vo.getReconsumeTimes());
        row.setKeys(vo.getKeys());
        row.setBody(abbreviateBody(vo.getBody()));
        row.setBodyBase64(vo.getBody() == null ? vo.getBodyBase64() : null);
        return row;
    }

    private static String abbreviateBody(String body) {
        if (body == null || body.length() <= MAX_EXCEL_CELL_CHARS) {
            return body;
        }
        int keep = MAX_EXCEL_CELL_CHARS - TRUNCATION_SUFFIX.length();
        // Back off onto a code-point boundary so the cut never splits a surrogate pair.
        if (Character.isHighSurrogate(body.charAt(keep - 1))) {
            keep--;
        }
        return body.substring(0, keep) + TRUNCATION_SUFFIX;
    }
}
