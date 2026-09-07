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
}
