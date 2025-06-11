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

package org.apache.rocketmq.dashboard.model;

import com.google.common.base.Charsets;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.rocketmq.client.trace.TraceBean;
import org.apache.rocketmq.client.trace.TraceContext;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.dashboard.model.trace.MessageTraceStatusEnum;
import org.apache.rocketmq.dashboard.util.MsgTraceDecodeUtil;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@NoArgsConstructor
public class MessageTraceView {
    private String requestId;
    private String msgId;
    private String tags;
    private String keys;
    private String storeHost;
    private String clientHost;
    private int costTime;
    private String msgType;
    private String offSetMsgId;
    private long timeStamp;
    private String topic;
    private String groupName;
    private int retryTimes;
    private String status;
    private String transactionState;
    private String transactionId;
    private boolean fromTransactionCheck;
    private String traceType;


    public static List<MessageTraceView> decodeFromTraceTransData(String key, MessageExt messageExt) {
        List<MessageTraceView> messageTraceViewList = new ArrayList<>();
        String messageBody = new String(messageExt.getBody(), Charsets.UTF_8);
        if (messageBody.isEmpty()) {
            return messageTraceViewList;
        }

        List<TraceContext> traceContextList = MsgTraceDecodeUtil.decoderFromTraceDataString(messageBody);
        for (TraceContext context : traceContextList) {
            MessageTraceView messageTraceView = new MessageTraceView();
            TraceBean traceBean = context.getTraceBeans().get(0);
            if (!traceBean.getMsgId().equals(key)) {
                continue;
            }
            messageTraceView.setCostTime(context.getCostTime());
            messageTraceView.setGroupName(context.getGroupName());
            if (context.isSuccess()) {
                messageTraceView.setStatus(MessageTraceStatusEnum.SUCCESS.getStatus());
            } else {
                messageTraceView.setStatus(MessageTraceStatusEnum.FAILED.getStatus());
            }
            messageTraceView.setKeys(traceBean.getKeys());
            messageTraceView.setMsgId(traceBean.getMsgId());
            messageTraceView.setTags(traceBean.getTags());
            messageTraceView.setTopic(traceBean.getTopic());
            messageTraceView.setMsgType(traceBean.getMsgType() == null ? null : traceBean.getMsgType().name());
            messageTraceView.setOffSetMsgId(traceBean.getOffsetMsgId());
            messageTraceView.setTimeStamp(context.getTimeStamp());
            messageTraceView.setStoreHost(traceBean.getStoreHost());
            messageTraceView.setClientHost(messageExt.getBornHostString());
            messageTraceView.setRequestId(context.getRequestId());
            messageTraceView.setRetryTimes(traceBean.getRetryTimes());
            messageTraceView.setTransactionState(traceBean.getTransactionState() == null ? null : traceBean.getTransactionState().name());
            messageTraceView.setTransactionId(traceBean.getTransactionId());
            messageTraceView.setFromTransactionCheck(traceBean.isFromTransactionCheck());
            messageTraceView.setTraceType(context.getTraceType() == null ? null : context.getTraceType().name());
            messageTraceViewList.add(messageTraceView);
        }
        return messageTraceViewList;
    }
}
