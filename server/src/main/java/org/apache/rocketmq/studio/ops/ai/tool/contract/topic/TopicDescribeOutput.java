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
import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.instance.topic.BrokerRouteVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TopicDescribeOutput(
        String cluster,
        String name,
        String namespace,
        String clusterId,
        TopicType type,
        int writeQueues,
        int readQueues,
        TopicPerm perm,
        long messageCount,
        double tps,
        int consumerGroupCount,
        String remark,
        List<ConsumerGroup> consumerGroups,
        List<Route> routes) {

    public static TopicDescribeOutput from(
            TopicVO topic, String cluster,
            List<TopicConsumerVO> consumers,
            List<BrokerRouteVO> routes) {
        List<ConsumerGroup> groups = consumers == null
                ? List.of()
                : consumers.stream().map(ConsumerGroup::from).toList();
        List<Route> routeList = routes == null
                ? List.of()
                : routes.stream().map(Route::from).toList();
        return new TopicDescribeOutput(
                cluster,
                topic.getName(),
                topic.getNamespace(),
                topic.getClusterId(),
                topic.getType(),
                topic.getWriteQueues(),
                topic.getReadQueues(),
                topic.getPerm(),
                topic.getMessageCount(),
                topic.getTps(),
                topic.getConsumerGroupCount(),
                topic.getRemark(),
                groups,
                routeList);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConsumerGroup(
            String group,
            String consumeType,
            String messageModel,
            double consumeTps,
            long diffTotal,
            boolean metricsAvailable) {

        static ConsumerGroup from(TopicConsumerVO source) {
            return new ConsumerGroup(
                    source.getGroup(),
                    source.getConsumeType() != null ? source.getConsumeType().name() : null,
                    source.getMessageModel(),
                    source.getConsumeTps(),
                    source.getDiffTotal(),
                    source.isMetricsAvailable());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Route(
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

        static Route from(BrokerRouteVO source) {
            return new Route(
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
}
