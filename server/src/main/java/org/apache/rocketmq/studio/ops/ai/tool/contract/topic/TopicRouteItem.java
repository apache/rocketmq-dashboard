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
package org.apache.rocketmq.studio.ops.ai.tool.contract.topic;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.rocketmq.studio.instance.topic.BrokerRouteVO;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TopicRouteItem(
        String brokerName,
        String brokerAddr,
        String masterAddr,
        Map<Long, String> brokerAddrs,
        List<Long> brokerIds,
        int replicaCount,
        int writeQueues,
        int readQueues,
        String perm,
        int permCode,
        boolean readable,
        boolean writable,
        int topicSysFlag) {

    public static TopicRouteItem from(BrokerRouteVO source) {
        return new TopicRouteItem(
                source.getBrokerName(),
                source.getBrokerAddr(),
                source.getMasterAddr(),
                source.getBrokerAddrs(),
                source.getBrokerIds(),
                source.getReplicaCount(),
                source.getWriteQueues(),
                source.getReadQueues(),
                source.getPerm() != null ? source.getPerm().name() : null,
                source.getPermCode(),
                source.isReadable(),
                source.isWritable(),
                source.getTopicSysFlag());
    }
}
