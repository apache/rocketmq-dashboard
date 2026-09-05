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

import com.aliyun.sdk.service.rocketmq20220801.models.ListInstancesResponseBody;
import org.apache.rocketmq.studio.provider.CloudInstanceOptionVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AliyunConvertersTest {

    @Test
    void toInstanceOptionShouldClampCountsOutsideTheIntegerRange() {
        ListInstancesResponseBody.List data = ListInstancesResponseBody.List.builder()
                .topicCount(Long.MAX_VALUE)
                .groupCount(Long.MIN_VALUE)
                .build();

        var result = AliyunConverters.toInstanceOptionVO(data);

        assertThat(result.getTopicCount()).isEqualTo(Integer.MAX_VALUE);
        assertThat(result.getGroupCount()).isZero();
    }

    @Test
    void toInstanceOptionShouldCarryAllDetailFields() {
        ListInstancesResponseBody.List data = ListInstancesResponseBody.List.builder()
                .instanceId("rmq-cn-xxx")
                .instanceName("prod-mq")
                .status("RUNNING")
                .regionId("cn-hangzhou")
                .remark("prod instance")
                .topicCount(10L)
                .groupCount(5L)
                .build();

        CloudInstanceOptionVO vo = AliyunConverters.toInstanceOptionVO(data);

        assertThat(vo.getInstanceId()).isEqualTo("rmq-cn-xxx");
        assertThat(vo.getInstanceName()).isEqualTo("prod-mq");
        assertThat(vo.getStatus()).isEqualTo("RUNNING");
        assertThat(vo.getRegionId()).isEqualTo("cn-hangzhou");
        assertThat(vo.getRemark()).isEqualTo("prod instance");
        assertThat(vo.getTopicCount()).isEqualTo(10);
        assertThat(vo.getGroupCount()).isEqualTo(5);
    }

    @Test
    void toInstanceOptionShouldKeepNullCountsNull() {
        ListInstancesResponseBody.List data = ListInstancesResponseBody.List.builder()
                .instanceId("rmq-cn-xxx")
                .build();

        CloudInstanceOptionVO vo = AliyunConverters.toInstanceOptionVO(data);

        assertThat(vo.getInstanceId()).isEqualTo("rmq-cn-xxx");
        assertThat(vo.getTopicCount()).isNull();
        assertThat(vo.getGroupCount()).isNull();
    }
}
