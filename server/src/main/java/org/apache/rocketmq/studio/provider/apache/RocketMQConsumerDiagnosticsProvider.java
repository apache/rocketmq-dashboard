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
package org.apache.rocketmq.studio.provider.apache;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.body.ConsumerRunningInfo;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.group.ConsumerDiagnosticsProvider;
import org.apache.rocketmq.studio.instance.group.ConsumerStackTraceVO;
import org.apache.rocketmq.studio.instance.group.ConsumerThreadStackVO;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads live consumer diagnostics from RocketMQ and converts the raw client jstack dump into
 * structured rows that the Studio UI can render.
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class RocketMQConsumerDiagnosticsProvider implements ConsumerDiagnosticsProvider {

    private static final Pattern THREAD_HEADER =
            Pattern.compile("^(?<name>.+?)TID:\\s+(?<id>\\d+)\\s+STATE:\\s+(?<state>\\S+)\\s*$");
    static final int MAX_JSTACK_CHARS = 2 * 1024 * 1024;

    private final RuntimeAdminClientResolver runtimeAdminClientResolver;
    private final MqAdminExtFactory adminFactory;
    private final RocketMQProperties properties;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ProxyConsumerResolver proxyConsumerResolver;

    @Override
    public ConsumerStackTraceVO getConsumerStack(String instanceId, String groupName, String clientId) {
        String normalizedGroup = groupName == null ? null : groupName.trim();
        String normalizedClient = clientId == null ? null : clientId.trim();
        if (normalizedGroup == null || normalizedGroup.isEmpty()) {
            throw new BusinessException(400, "consumer group is required");
        }
        if (normalizedClient == null || normalizedClient.isEmpty()) {
            throw new BusinessException(400, "consumer client id is required");
        }
        // Clients that connect through a proxy keep their channel on the proxy and never register
        // on a broker, so ask the proxy first; the broker only knows directly connected clients
        // and answers "not online" for everyone else.
        ConsumerRunningInfo viaProxy = proxyConsumerResolver == null
                ? null
                : proxyConsumerResolver.resolveConsumerRunningInfo(instanceId, normalizedGroup, normalizedClient);
        if (viaProxy != null) {
            return toStackTrace(normalizedGroup, normalizedClient, viaProxy);
        }
        if (StringUtils.hasText(instanceId)) {
            return runtimeAdminClientResolver.execute(instanceId,
                    admin -> getConsumerStack(admin, normalizedGroup, normalizedClient));
        }
        if (!StringUtils.hasText(properties.getNamesrvAddr())) {
            throw new BusinessException(503, "RocketMQ admin not connected");
        }
        return adminFactory.execute(properties.getNamesrvAddr(), null,
                admin -> getConsumerStack(admin, normalizedGroup, normalizedClient));
    }

    private ConsumerStackTraceVO getConsumerStack(MQAdminExt admin, String groupName, String clientId) {
        try {
            ConsumerRunningInfo runningInfo = admin.getConsumerRunningInfo(groupName, clientId, true);
            if (runningInfo == null) {
                throw new BusinessException(404, "Consumer client not found: " + clientId);
            }
            return toStackTrace(groupName, clientId, runningInfo);
        } catch (BusinessException e) {
            throw e;
        } catch (MQClientException e) {
            if (e.getResponseCode() == ResponseCode.CONSUMER_NOT_ONLINE) {
                throw new BusinessException(404, notReachable(clientId));
            }
            throw diagnosticsFailure(groupName, clientId, e);
        } catch (Exception e) {
            throw diagnosticsFailure(groupName, clientId, e);
        }
    }

    private ConsumerStackTraceVO toStackTrace(String groupName, String clientId, ConsumerRunningInfo runningInfo) {
        List<ConsumerThreadStackVO> threads = parseJstack(runningInfo.getJstack());
        return ConsumerStackTraceVO.builder()
                .groupName(groupName)
                .clientId(clientId)
                .capturedAt(LocalDateTime.now())
                .threadCount(threads.size())
                .threads(threads)
                .build();
    }

    private String notReachable(String clientId) {
        return "Consumer client is not reachable from any proxy or broker: " + clientId;
    }

    private BusinessException diagnosticsFailure(String groupName, String clientId, Exception exception) {
        log.warn("Failed to get consumer stack, groupName={}, clientId={}: {}",
                groupName, clientId, exception.getMessage());
        String rootMessage = rootMessage(exception);
        // The broker answers "not online" for every proxy-connected client; surface that as the
        // business state it is instead of a raw broker error the operator cannot act on.
        if (rootMessage != null && rootMessage.contains("not online")) {
            return new BusinessException(404, notReachable(clientId));
        }
        return new BusinessException(502,
                "Failed to get consumer stack for " + clientId + ": " + rootMessage);
    }

    private String rootMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }

    private List<ConsumerThreadStackVO> parseJstack(String jstack) {
        if (!StringUtils.hasText(jstack)) {
            return List.of();
        }
        if (jstack.length() > MAX_JSTACK_CHARS) {
            throw new BusinessException(502,
                    "Consumer stack exceeds the supported size of " + MAX_JSTACK_CHARS + " characters");
        }

        List<ConsumerThreadStackVO> threads = new ArrayList<>();
        ThreadBuilder current = null;
        for (String rawLine : jstack.split("\\R")) {
            if (!StringUtils.hasText(rawLine)) {
                continue;
            }
            Matcher header = THREAD_HEADER.matcher(rawLine);
            if (header.matches()) {
                if (current != null) {
                    threads.add(current.build());
                    current = null;
                }
                try {
                    current = new ThreadBuilder(
                            header.group("name").trim(),
                            Long.parseLong(header.group("id")),
                            header.group("state").trim());
                } catch (NumberFormatException malformedThreadId) {
                    log.debug("Ignoring consumer stack row with an invalid thread id");
                }
                continue;
            }
            if (current != null) {
                current.addFrame(stripThreadNamePrefix(rawLine, current.threadName()));
            }
        }
        if (current != null) {
            threads.add(current.build());
        }
        return threads;
    }

    private String stripThreadNamePrefix(String line, String threadName) {
        if (line.startsWith(threadName)) {
            return line.substring(threadName.length()).trim();
        }
        return line.trim();
    }

    private record ThreadBuilder(String threadName, long threadId, String state, List<String> stackTrace) {

        private ThreadBuilder(String threadName, long threadId, String state) {
            this(threadName, threadId, state, new ArrayList<>());
        }

        private void addFrame(String frame) {
            if (StringUtils.hasText(frame)) {
                stackTrace.add(frame);
            }
        }

        private ConsumerThreadStackVO build() {
            return ConsumerThreadStackVO.builder()
                    .threadName(threadName)
                    .threadId(threadId)
                    .state(state)
                    .blockedTime(0)
                    .waitedTime(0)
                    .stackTrace(List.copyOf(stackTrace))
                    .build();
        }
    }
}
