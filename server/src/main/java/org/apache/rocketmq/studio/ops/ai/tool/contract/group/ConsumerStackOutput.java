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
package org.apache.rocketmq.studio.ops.ai.tool.contract.group;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.rocketmq.studio.instance.group.ConsumerStackTraceVO;
import org.apache.rocketmq.studio.instance.group.ConsumerThreadStackVO;

import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConsumerStackOutput(
        String instanceId,
        String groupName,
        String clientId,
        String capturedAt,
        int threadCount,
        List<ThreadInfo> threads) {

    public static ConsumerStackOutput from(String instanceId, ConsumerStackTraceVO source) {
        List<ConsumerThreadStackVO> sourceThreads = source.getThreads() == null
                ? List.of() : source.getThreads();
        return new ConsumerStackOutput(
                instanceId,
                source.getGroupName(),
                source.getClientId(),
                source.getCapturedAt() == null ? null : source.getCapturedAt().toString(),
                source.getThreadCount(),
                sourceThreads.stream()
                        .filter(Objects::nonNull)
                        .map(ThreadInfo::from)
                        .toList());
    }

    public record ThreadInfo(
            String threadName,
            long threadId,
            String state,
            long blockedTime,
            long waitedTime,
            List<String> stackTrace) {

        private static ThreadInfo from(ConsumerThreadStackVO source) {
            return new ThreadInfo(
                    source.getThreadName(),
                    source.getThreadId(),
                    source.getState(),
                    source.getBlockedTime(),
                    source.getWaitedTime(),
                    source.getStackTrace() == null ? List.of() : source.getStackTrace());
        }
    }
}
