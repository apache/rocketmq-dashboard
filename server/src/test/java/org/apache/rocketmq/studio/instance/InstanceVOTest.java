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
package org.apache.rocketmq.studio.instance;

import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the instance view of {@link InstanceVO}: the local identity, the cloud routing fields
 * and the computed counters (topics, groups, regions) round-trip through the builder, and a
 * fresh instance counts zero.
 */
class InstanceVOTest {

    @Test
    void everyFieldRoundTripsThroughTheBuilder() {
        InstanceVO vo = InstanceVO.builder()
                .name("order-cluster")
                .remark("production")
                .type(InstanceType.PROXY_CLUSTER)
                .endpoint("10.0.0.1:8080")
                .vendor(InstanceVendor.ALIYUN)
                .cloudInstanceId("rmq-abc123")
                .credentialId(11L)
                .adminCredentialRef("cred-ref-1")
                .regionId("cn-hangzhou")
                .regionName("Hangzhou")
                .topicCount(120)
                .consumerGroupCount(45)
                .build();

        assertThat(vo.getName()).isEqualTo("order-cluster");
        assertThat(vo.getRemark()).isEqualTo("production");
        assertThat(vo.getType()).isEqualTo(InstanceType.PROXY_CLUSTER);
        assertThat(vo.getEndpoint()).isEqualTo("10.0.0.1:8080");
        assertThat(vo.getVendor()).isEqualTo(InstanceVendor.ALIYUN);
        assertThat(vo.getCloudInstanceId()).isEqualTo("rmq-abc123");
        assertThat(vo.getCredentialId()).isEqualTo(11L);
        assertThat(vo.getAdminCredentialRef()).isEqualTo("cred-ref-1");
        assertThat(vo.getRegionId()).isEqualTo("cn-hangzhou");
        assertThat(vo.getRegionName()).isEqualTo("Hangzhou");
        assertThat(vo.getTopicCount()).isEqualTo(120);
        assertThat(vo.getConsumerGroupCount()).isEqualTo(45);
    }

    @Test
    void aFreshInstanceCountsZero() {
        InstanceVO vo = new InstanceVO();
        assertThat(vo.getTopicCount()).isZero();
        assertThat(vo.getConsumerGroupCount()).isZero();
        assertThat(vo.getName()).isNull();
    }
}
