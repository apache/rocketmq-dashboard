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

import com.google.common.base.Objects;
import lombok.Data;

import java.util.List;

@Data
public class TopicConfigInfo {

    private List<String> clusterNameList;
    /**
     * -- GETTER --
     *  topicConfig
     */
    private List<String> brokerNameList;

    /**
     * topicConfig
     */
    private String topicName;
    private int writeQueueNums;
    private int readQueueNums;
    private int perm;
    private boolean order;

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TopicConfigInfo that = (TopicConfigInfo) o;
        return writeQueueNums == that.writeQueueNums &&
                readQueueNums == that.readQueueNums &&
                perm == that.perm &&
                order == that.order &&
                Objects.equal(topicName, that.topicName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(topicName, writeQueueNums, readQueueNums, perm, order);
    }

}
