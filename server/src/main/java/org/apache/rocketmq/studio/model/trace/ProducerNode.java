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
package org.apache.rocketmq.studio.model.trace;


import java.util.List;

public class ProducerNode {
    private String msgId;
    private String tags;
    private String keys;
    private String offSetMsgId;
    private String topic;
    private String groupName;
    private TraceNode traceNode;
    private List<TraceNode> transactionNodeList;
    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getKeys() {
        return keys;
    }

    public void setKeys(String keys) {
        this.keys = keys;
    }

    public String getOffSetMsgId() {
        return offSetMsgId;
    }

    public void setOffSetMsgId(String offSetMsgId) {
        this.offSetMsgId = offSetMsgId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public TraceNode getTraceNode() {
        return traceNode;
    }

    public void setTraceNode(TraceNode traceNode) {
        this.traceNode = traceNode;
    }

    public List<TraceNode> getTransactionNodeList() {
        return transactionNodeList;
    }

    public void setTransactionNodeList(List<TraceNode> transactionNodeList) {
        this.transactionNodeList = transactionNodeList;
    }

}
