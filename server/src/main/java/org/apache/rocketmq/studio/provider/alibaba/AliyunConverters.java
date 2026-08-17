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
package org.apache.rocketmq.studio.provider.alibaba;

import org.apache.rocketmq.studio.common.util.InstanceIds;
import com.aliyun.sdk.service.rocketmq20220801.models.DataTopicLagMapValue;
import com.aliyun.sdk.service.rocketmq20220801.models.GetConsumerGroupLagResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.GetInstanceResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.GetTraceResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ListConsumerGroupSubscriptionsResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ListConsumerGroupsResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ListInstancesResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ListMessagesResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ListRegionsResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ListTopicSubscriptionsResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ListTopicsResponseBody;
import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.TraceNodeVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.provider.CloudInstanceDetailVO;
import org.apache.rocketmq.studio.provider.CloudInstanceOptionVO;
import org.apache.rocketmq.studio.provider.CloudRegionVO;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Static converters between Aliyun RocketMQ OpenAPI SDK models and Studio VOs.
 */
final class AliyunConverters {

    static final int PAGE_SIZE = 100;
    static final int MAX_PAGES = 5;
    static final int MESSAGE_PAGE_SIZE = 20;
    static final int MESSAGE_MAX_PAGES = 5;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // Aliyun RocketMQ OpenAPI timestamps are unzoned "yyyy-MM-dd HH:mm:ss" strings interpreted as
    // UTC+8 (Asia/Shanghai), regardless of the server's default zone.
    private static final ZoneId ALIYUN_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private AliyunConverters() {
    }

    static CloudRegionVO toRegionVO(ListRegionsResponseBody.Data data) {
        return new CloudRegionVO(data.getRegionId(), data.getRegionName());
    }

    static CloudInstanceOptionVO toInstanceOptionVO(ListInstancesResponseBody.List data) {
        CloudInstanceOptionVO vo = new CloudInstanceOptionVO();
        vo.setInstanceId(data.getInstanceId());
        vo.setInstanceName(data.getInstanceName());
        vo.setStatus(data.getStatus());
        vo.setRegionId(data.getRegionId());
        vo.setTopicCount(toInteger(data.getTopicCount()));
        vo.setGroupCount(toInteger(data.getGroupCount()));
        vo.setRemark(data.getRemark());
        return vo;
    }

    static CloudInstanceDetailVO toInstanceDetailVO(GetInstanceResponseBody.Data data) {
        CloudInstanceDetailVO vo = new CloudInstanceDetailVO();
        vo.setInstanceId(data.getInstanceId());
        vo.setInstanceName(data.getInstanceName());
        vo.setStatus(data.getStatus());
        vo.setRegionId(data.getRegionId());
        vo.setRemark(data.getRemark());
        List<CloudInstanceDetailVO.CloudEndpoint> endpoints = new ArrayList<>();
        if (data.getNetworkInfo() != null && data.getNetworkInfo().getEndpoints() != null) {
            for (GetInstanceResponseBody.Endpoints endpoint : data.getNetworkInfo().getEndpoints()) {
                if (endpoint == null) {
                    continue;
                }
                endpoints.add(new CloudInstanceDetailVO.CloudEndpoint(
                        endpoint.getEndpointType(), endpoint.getEndpointUrl()));
            }
        }
        vo.setEndpoints(endpoints);
        return vo;
    }

    static TopicVO toTopicVO(ListTopicsResponseBody.List data, String studioInstanceId) {
        TopicVO vo = new TopicVO();
        vo.setName(data.getTopicName());
        vo.setInstanceId(InstanceIds.parseLongOrNull(studioInstanceId));
        vo.setType(toTopicType(data.getMessageType()));
        vo.setRemark(data.getRemark());
        vo.setGmtCreate(parseDateTime(data.getCreateTime()));
        vo.setGmtModified(parseDateTime(data.getUpdateTime()));
        vo.setWriteQueues(0);
        vo.setReadQueues(0);
        return vo;
    }

    static TopicType toTopicType(String messageType) {
        if (messageType == null) {
            return null;
        }
        switch (messageType.toUpperCase(Locale.ROOT)) {
            case "NORMAL":
                return TopicType.NORMAL;
            case "FIFO":
                return TopicType.FIFO;
            case "DELAY":
                return TopicType.DELAY;
            case "TRANSACTION":
                return TopicType.TRANSACTION;
            default:
                return null;
        }
    }

    static TopicConsumerVO toTopicConsumerVO(ListTopicSubscriptionsResponseBody.Data data) {
        return TopicConsumerVO.builder()
                .group(data.getConsumerGroupId())
                .consumeType(toConsumeType(data.getMessageModel()))
                .messageModel(data.getMessageModel())
                .build();
    }

    static ConsumerGroupVO toConsumerGroupVO(ListConsumerGroupsResponseBody.List data, String studioInstanceId) {
        ConsumerGroupVO vo = new ConsumerGroupVO();
        vo.setName(data.getConsumerGroupId());
        vo.setInstanceId(InstanceIds.parseLongOrNull(studioInstanceId));
        vo.setConsumeType(toConsumeType(data.getMessageModel()));
        vo.setGmtCreate(parseDateTime(data.getCreateTime()));
        vo.setGmtModified(parseDateTime(data.getUpdateTime()));
        return vo;
    }

    static ConsumeType toConsumeType(String messageModel) {
        if (messageModel == null) {
            return null;
        }
        if ("Clustering".equalsIgnoreCase(messageModel)) {
            return ConsumeType.CLUSTERING;
        }
        if ("Broadcasting".equalsIgnoreCase(messageModel)) {
            return ConsumeType.BROADCASTING;
        }
        return null;
    }

    static List<QueueProgressVO> toQueueProgressRows(GetConsumerGroupLagResponseBody.Data data) {
        List<QueueProgressVO> rows = new ArrayList<>();
        Map<String, DataTopicLagMapValue> topicLagMap = data.getTopicLagMap();
        if (topicLagMap != null) {
            for (Map.Entry<String, DataTopicLagMapValue> entry : topicLagMap.entrySet()) {
                long ready = entry.getValue() == null || entry.getValue().getReadyCount() == null
                        ? 0L : entry.getValue().getReadyCount();
                rows.add(QueueProgressVO.builder()
                        .broker("topic:" + entry.getKey())
                        .queueId(0)
                        .brokerOffset(0L)
                        .consumerOffset(0L)
                        .diffTotal(ready)
                        .build());
            }
        }
        GetConsumerGroupLagResponseBody.TotalLag totalLag = data.getTotalLag();
        if (totalLag != null && totalLag.getReadyCount() != null) {
            rows.add(QueueProgressVO.builder()
                    .broker("total")
                    .queueId(0)
                    .brokerOffset(0L)
                    .consumerOffset(0L)
                    .diffTotal(totalLag.getReadyCount())
                    .build());
        }
        return rows;
    }

    static SubscriptionEntryVO toSubscriptionEntry(ListConsumerGroupSubscriptionsResponseBody.Data data) {
        return SubscriptionEntryVO.builder()
                .topic(data.getTopicName())
                .expression(data.getFilterExpression())
                .type(data.getFilterExpressionType())
                .consistency(data.getConsistency() == null ? null : String.valueOf(data.getConsistency()))
                .build();
    }

    static MessageRecordVO toMessageRecord(ListMessagesResponseBody.List data) {
        String rawBody = data.getBody();
        String decodedBody = tryBase64Decode(rawBody);
        MessageRecordVO.MessageRecordVOBuilder builder = MessageRecordVO.builder()
                .msgId(data.getMessageId())
                .topic(data.getTopicName())
                .tag(data.getMessageTag())
                .key(joinMessageKeys(data.getMessageKeys()))
                .bornHost(data.getBornHost())
                .storeHost(data.getStoreHost())
                .storeTime(parseTimeMillis(data.getStoreTime()))
                .properties(data.getUserProperties())
                .size(data.getBodySize() == null ? 0 : data.getBodySize());
        if (decodedBody != null) {
            builder.body(decodedBody).bodyEncoding("UTF-8");
        } else {
            builder.body(rawBody).bodyEncoding("TEXT");
        }
        return builder.build();
    }

    static TraceRecordVO toTraceRecord(GetTraceResponseBody.Data data) {
        List<TraceNodeVO> nodes = new ArrayList<>();
        if (data.getProducerInfo() != null && data.getProducerInfo().getRecords() != null) {
            for (GetTraceResponseBody.ProducerInfoRecords record : data.getProducerInfo().getRecords()) {
                if (record == null) {
                    continue;
                }
                nodes.add(TraceNodeVO.builder()
                        .title("Producer")
                        .timestamp(parseTimeMillis(record.getProduceTime()))
                        .status(record.getProduceStatus())
                        .costTime(record.getProduceDuration() == null ? 0L : record.getProduceDuration())
                        .description(joinParts(", ", record.getClientHost(), record.getMessageSource()))
                        .build());
            }
        }
        if (data.getBrokerInfo() != null && data.getBrokerInfo().getOperations() != null) {
            for (GetTraceResponseBody.Operations operation : data.getBrokerInfo().getOperations()) {
                if (operation == null) {
                    continue;
                }
                nodes.add(TraceNodeVO.builder()
                        .title("Broker " + operation.getOperateType())
                        .timestamp(parseTimeMillis(operation.getOperateTime()))
                        .build());
            }
        }
        if (data.getConsumerInfos() != null) {
            for (GetTraceResponseBody.ConsumerInfos consumerInfo : data.getConsumerInfos()) {
                if (consumerInfo == null) {
                    continue;
                }
                if (consumerInfo.getRecords() == null || consumerInfo.getRecords().isEmpty()) {
                    nodes.add(TraceNodeVO.builder()
                            .title("Consumer " + consumerInfo.getConsumerGroupId())
                            .status(consumerInfo.getConsumeStatus())
                            .build());
                    continue;
                }
                for (GetTraceResponseBody.Records record : consumerInfo.getRecords()) {
                    if (record == null) {
                        continue;
                    }
                    String operateTime = null;
                    if (record.getOperations() != null && !record.getOperations().isEmpty()) {
                        operateTime = record.getOperations().get(0).getOperateTime();
                    }
                    nodes.add(TraceNodeVO.builder()
                            .title("Consumer " + consumerInfo.getConsumerGroupId())
                            .timestamp(parseTimeMillis(operateTime))
                            .status(record.getConsumeStatus())
                            .description(joinParts(", ", record.getClientHost(), record.getUserName()))
                            .build());
                }
            }
        }
        return TraceRecordVO.builder().nodes(nodes).build();
    }

    static java.time.LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDateTime.parse(value, TIME_FORMATTER);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    static long parseTimeMillis(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            LocalDateTime dateTime = LocalDateTime.parse(value, TIME_FORMATTER);
            return dateTime.atZone(ALIYUN_TIME_ZONE).toInstant().toEpochMilli();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    static String formatTimeMillis(long epochMillis) {
        return TIME_FORMATTER.format(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ALIYUN_TIME_ZONE));
    }

    static String tryBase64Decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ignored) {
            return null;
        }
    }

    private static String joinMessageKeys(List<String> keys) {
        return keys == null ? null : joinParts(" ", keys.toArray(String[]::new));
    }

    private static String joinParts(String separator, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(separator);
            }
            sb.append(part);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static Integer toInteger(Long value) {
        if (value == null) {
            return null;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < 0) {
            return 0;
        }
        return value.intValue();
    }
}
