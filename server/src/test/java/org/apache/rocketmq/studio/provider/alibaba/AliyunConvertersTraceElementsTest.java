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
import org.apache.rocketmq.studio.common.domain.enums.DeliveryStatus;
import org.apache.rocketmq.studio.instance.message.ConsumerStatusVO;
import org.apache.rocketmq.studio.instance.message.TraceNodeVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

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
    void toTraceRecordShouldBuildConsumerStatusFromRecordLessGroups() {
        GetTraceResponseBody.ConsumerInfos succeeded = consumerInfo("group-a", "SUCCESS");
        GetTraceResponseBody.ConsumerInfos failed = consumerInfo("group-b", "FAILED");
        GetTraceResponseBody.ConsumerInfos unknown = consumerInfo("group-c", "UNKNOWN");
        GetTraceResponseBody.Data data = GetTraceResponseBody.Data.builder()
                .producerInfo(GetTraceResponseBody.ProducerInfo.builder()
                        .records(Arrays.asList()).build())
                .brokerInfo(GetTraceResponseBody.BrokerInfo.builder()
                        .operations(Arrays.asList()).build())
                .consumerInfos(Arrays.asList(succeeded, failed, unknown))
                .build();

        TraceRecordVO trace = AliyunConverters.toTraceRecord(data);

        assertThat(trace.getNodes()).hasSize(3);
        assertThat(trace.getNodes())
                .extracting(TraceNodeVO::getTitle)
                .containsExactly("Consumer group-a", "Consumer group-b", "Consumer group-c");
        assertThat(trace.getConsumerStatus())
                .extracting(ConsumerStatusVO::getDeliveryStatus)
                .containsExactly(DeliveryStatus.success, DeliveryStatus.failed, DeliveryStatus.pending);
    }

    @Test
    void toTraceRecordShouldTolerateNullConsumerInfoLists() {
        GetTraceResponseBody.Data data = GetTraceResponseBody.Data.builder()
                .producerInfo(GetTraceResponseBody.ProducerInfo.builder()
                        .records(Arrays.asList()).build())
                .brokerInfo(GetTraceResponseBody.BrokerInfo.builder()
                        .operations(Arrays.asList()).build())
                .consumerInfos(null)
                .build();

        TraceRecordVO trace = AliyunConverters.toTraceRecord(data);

        assertThat(trace.getNodes()).isEmpty();
        assertThat(trace.getConsumerStatus()).isEmpty();
    }

    private static GetTraceResponseBody.ConsumerInfos consumerInfo(String group, String status) {
        return GetTraceResponseBody.ConsumerInfos.builder()
                .consumerGroupId(group)
                .consumeStatus(status)
                .records(List.of())
                .build();
    }
}
