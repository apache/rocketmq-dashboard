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

import com.aliyun.sdk.service.rocketmq20220801.models.GetInstanceResponseBody;
import org.apache.rocketmq.studio.provider.CloudInstanceDetailVO;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AliyunConvertersNullEndpointTest {

    @Test
    void toInstanceDetailShouldSkipNullEndpointEntries() {
        GetInstanceResponseBody.Endpoints endpoint = GetInstanceResponseBody.Endpoints.builder()
                .endpointType("TCP_VPC")
                .endpointUrl("10.0.0.1:8080")
                .build();
        GetInstanceResponseBody.NetworkInfo network = GetInstanceResponseBody.NetworkInfo.builder()
                .endpoints(Arrays.asList(null, endpoint))
                .build();
        GetInstanceResponseBody.Data data = GetInstanceResponseBody.Data.builder()
                .instanceId("rmq-a")
                .networkInfo(network)
                .build();

        assertThat(AliyunConverters.toInstanceDetailVO(data).getEndpoints())
                .singleElement()
                .satisfies(value -> assertThat(value.getEndpointUrl()).isEqualTo("10.0.0.1:8080"));
    }

    @Test
    void toInstanceDetailShouldCopyCoreFields() {
        GetInstanceResponseBody.Data data = GetInstanceResponseBody.Data.builder()
                .instanceId("rmq-b")
                .instanceName("prod-rocketmq")
                .status("RUNNING")
                .regionId("cn-shanghai")
                .remark("production instance")
                .build();

        CloudInstanceDetailVO vo = AliyunConverters.toInstanceDetailVO(data);

        assertThat(vo.getInstanceId()).isEqualTo("rmq-b");
        assertThat(vo.getInstanceName()).isEqualTo("prod-rocketmq");
        assertThat(vo.getStatus()).isEqualTo("RUNNING");
        assertThat(vo.getRegionId()).isEqualTo("cn-shanghai");
        assertThat(vo.getRemark()).isEqualTo("production instance");
        assertThat(vo.getEndpoints()).isEmpty();
    }

    @Test
    void toInstanceDetailShouldHandleAbsentNetworkInfo() {
        GetInstanceResponseBody.Data data = GetInstanceResponseBody.Data.builder()
                .instanceId("rmq-c")
                .networkInfo(null)
                .build();

        assertThat(AliyunConverters.toInstanceDetailVO(data).getEndpoints()).isEmpty();
    }
}
