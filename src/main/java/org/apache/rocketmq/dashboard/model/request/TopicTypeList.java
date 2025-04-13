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
package org.apache.rocketmq.dashboard.model.request;

import java.util.List;

public class TopicTypeList {
    private List<String> topicNameList;
    private List<String> messageTypeList;

    public List<String> getTopicNameList() {
        return topicNameList;
    }

    public void setTopicNameList(List<String> topicNameList) {
        this.topicNameList = topicNameList;
    }

    public List<String> getMessageTypeList() {
        return messageTypeList;
    }

    public void setMessageTypeList(List<String> messageTypeList) {
        this.messageTypeList = messageTypeList;
    }

    public TopicTypeList(List<String> topicNameList, List<String> messageTypeList) {
        this.topicNameList = topicNameList;
        this.messageTypeList = messageTypeList;
    }
}
