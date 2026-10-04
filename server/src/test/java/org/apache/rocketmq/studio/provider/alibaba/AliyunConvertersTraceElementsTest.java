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

import com.aliyun.sdk.service.rocketmq20220801.models.GetTraceResponseBody;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AliyunConvertersTraceElementsTest {

    @Test
    void toTraceRecordShouldSkipNullSdkListElements() {
        GetTraceResponseBody.ProducerInfo producer = GetTraceResponseBody.ProducerInfo.builder()
                .records(Arrays.asList((GetTraceResponseBody.ProducerInfoRecords) null))
                .build();
        GetTraceResponseBody.BrokerInfo broker = GetTraceResponseBody.BrokerInfo.builder()
                .operations(Arrays.asList((GetTraceResponseBody.Operations) null))
                .build();
        GetTraceResponseBody.ConsumerInfos consumer = GetTraceResponseBody.ConsumerInfos.builder()
                .records(Arrays.asList((GetTraceResponseBody.Records) null))
                .build();
        GetTraceResponseBody.Data data = GetTraceResponseBody.Data.builder()
                .producerInfo(producer)
                .brokerInfo(broker)
                .consumerInfos(Arrays.asList(null, consumer))
                .build();

        assertThat(AliyunConverters.toTraceRecord(data).getNodes()).isEmpty();
    }

    @Test
    void toTraceRecordShouldNormalizeCloudStatusIntoTheTraceDomain() {
        // The frontend mapTraceNodeStatus only knows finish/failed/process/
        // error/wait and maps anything else to 'wait': passing the raw cloud
        // status through (SUCCESS/CONSUME_FAIL) renders a failed consume as
        // waiting (warning) instead of error (critical), and failedNodeCount
        // stays 0.
        GetTraceResponseBody.ProducerInfoRecords producerRecord =
                GetTraceResponseBody.ProducerInfoRecords.builder()
                        .produceTime("2026-10-05 12:00:00")
                        .produceStatus("SUCCESS")
                        .produceDuration(5L)
                        .build();
        GetTraceResponseBody.ProducerInfo producer = GetTraceResponseBody.ProducerInfo.builder()
                .records(java.util.List.of(producerRecord))
                .build();

        GetTraceResponseBody.Records consumeRecord = GetTraceResponseBody.Records.builder()
                .operations(java.util.List.of(GetTraceResponseBody.RecordsOperations.builder()
                        .operateTime("2026-10-05 12:00:01").build()))
                .consumeStatus("CONSUME_FAIL")
                .build();
        GetTraceResponseBody.ConsumerInfos consumer = GetTraceResponseBody.ConsumerInfos.builder()
                .consumerGroupId("gid-fail")
                .records(java.util.List.of(consumeRecord))
                .build();

        GetTraceResponseBody.Records okRecord = GetTraceResponseBody.Records.builder()
                .operations(java.util.List.of(GetTraceResponseBody.RecordsOperations.builder()
                        .operateTime("2026-10-05 12:00:02").build()))
                .consumeStatus("CONSUME_SUCCESS")
                .build();
        GetTraceResponseBody.ConsumerInfos okConsumer = GetTraceResponseBody.ConsumerInfos.builder()
                .consumerGroupId("gid-ok")
                .records(java.util.List.of(okRecord))
                .build();

        GetTraceResponseBody.Data data = GetTraceResponseBody.Data.builder()
                .producerInfo(producer)
                .consumerInfos(java.util.List.of(consumer, okConsumer))
                .build();

        org.apache.rocketmq.studio.instance.message.TraceRecordVO record =
                AliyunConverters.toTraceRecord(data);
        java.util.List<String> statuses = record.getNodes().stream()
                .map(org.apache.rocketmq.studio.instance.message.TraceNodeVO::getStatus)
                .collect(java.util.stream.Collectors.toList());
        // Cloud statuses normalized into the frontend trace domain: success -> finish, failure -> failed
        assertThat(statuses).containsExactly("finish", "failed", "finish");
    }
}
