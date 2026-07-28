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

package org.apache.rocketmq.studio.model;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.metadata.BaseRowModel;
import com.alibaba.excel.util.DateUtils;
import com.google.common.base.Charsets;
import org.apache.rocketmq.common.message.MessageExt;

import java.io.Serializable;
import java.util.Date;

public class DlqMessageExcelModel extends BaseRowModel implements Serializable {

    @ExcelProperty(value = "topic", index = 0)
    @ColumnWidth(value = 15)
    private String topic;

    @ExcelProperty(value = "msgId", index = 1)
    @ColumnWidth(value = 15)
    private String msgId;

    @ExcelProperty(value = "bornHost", index = 2)
    @ColumnWidth(value = 15)
    private String bornHost;

    @ExcelProperty(value = "bornTimestamp", index = 3)
    @ColumnWidth(value = 25)
    private String bornTimestamp;

    @ExcelProperty(value = "storeTimestamp", index = 4)
    @ColumnWidth(value = 25)
    private String storeTimestamp;

    @ExcelProperty(value = "reconsumeTimes", index = 5)
    @ColumnWidth(value = 25)
    private int reconsumeTimes;

    @ExcelProperty(value = "properties", index = 6)
    @ColumnWidth(value = 20)
    private String properties;

    @ExcelProperty(value = "messageBody", index = 7)
    @ColumnWidth(value = 20)
    private String messageBody;

    @ExcelProperty(value = "bodyCRC", index = 8)
    @ColumnWidth(value = 15)
    private int bodyCRC;

    @ExcelProperty(value = "exception", index = 9)
    @ColumnWidth(value = 30)
    private String exception;

    public DlqMessageExcelModel(MessageExt messageExt) {
        this.topic = messageExt.getTopic();
        this.msgId = messageExt.getMsgId();
        this.bornHost = messageExt.getBornHostString();
        this.bornTimestamp = DateUtils.format(new Date(messageExt.getBornTimestamp()), DateUtils.DATE_FORMAT_19);
        this.storeTimestamp = DateUtils.format(new Date(messageExt.getStoreTimestamp()), DateUtils.DATE_FORMAT_19);
        this.reconsumeTimes = messageExt.getReconsumeTimes();
        this.properties = messageExt.getProperties().toString();
        this.messageBody = new String(messageExt.getBody(), Charsets.UTF_8);
        this.bodyCRC = messageExt.getBodyCRC();
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public String getBornHost() {
        return bornHost;
    }

    public void setBornHost(String bornHost) {
        this.bornHost = bornHost;
    }

    public String getBornTimestamp() {
        return bornTimestamp;
    }

    public void setBornTimestamp(String bornTimestamp) {
        this.bornTimestamp = bornTimestamp;
    }

    public String getStoreTimestamp() {
        return storeTimestamp;
    }

    public void setStoreTimestamp(String storeTimestamp) {
        this.storeTimestamp = storeTimestamp;
    }

    public int getReconsumeTimes() {
        return reconsumeTimes;
    }

    public void setReconsumeTimes(int reconsumeTimes) {
        this.reconsumeTimes = reconsumeTimes;
    }

    public String getProperties() {
        return properties;
    }

    public void setProperties(String properties) {
        this.properties = properties;
    }

    public String getMessageBody() {
        return messageBody;
    }

    public void setMessageBody(String messageBody) {
        this.messageBody = messageBody;
    }

    public int getBodyCRC() {
        return bodyCRC;
    }

    public void setBodyCRC(int bodyCRC) {
        this.bodyCRC = bodyCRC;
    }

    public String getException() {
        return exception;
    }

    public void setException(String exception) {
        this.exception = exception;
    }

}
