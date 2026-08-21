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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Excel row model for dead-letter message export (single message or a selected batch).
 */
@Data
public class DLQMessageExcelRow {

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
    @ExcelProperty("Keys")
    private String keys;
    @ExcelProperty("Body")
    private String body;

    public static DLQMessageExcelRow from(DLQMessageVO vo) {
        DLQMessageExcelRow row = new DLQMessageExcelRow();
        row.setMsgId(vo.getMsgId());
        row.setTopic(vo.getTopic());
        row.setQueueId(vo.getQueueId());
        row.setOffset(vo.getOffset());
        row.setStoreTime(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(vo.getStoreTime()), ZoneId.systemDefault()).format(STORE_TIME_FORMAT));
        row.setKeys(vo.getKeys());
        row.setBody(vo.getBody());
        return row;
    }
}
