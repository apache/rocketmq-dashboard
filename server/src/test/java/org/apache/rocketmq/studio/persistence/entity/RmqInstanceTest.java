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
package org.apache.rocketmq.studio.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the instance entity {@link RmqInstance}: it maps to the {@code rmq_instance} table and
 * every column round-trips - the local identity and the cloud routing fields (vendor, cloud
 * instance id, credential reference, region) an instance-scoped page needs to reach its broker.
 */
class RmqInstanceTest {

    @Test
    void theEntityMapsToTheInstanceTable() {
        TableName annotation = RmqInstance.class.getAnnotation(TableName.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("rmq_instance");
    }

    @Test
    void everyColumnRoundTrips() {
        RmqInstance instance = new RmqInstance();
        instance.setId(7L);
        instance.setName("order-cluster");
        instance.setRemark("production");
        instance.setType("V5_PROXY_CLUSTER");
        instance.setEndpoint("10.0.0.1:8080");
        instance.setVendor("ALIYUN");
        instance.setCloudInstanceId("rmq-abc123");
        instance.setCredentialId(11L);
        instance.setAdminCredentialRef("cred-ref-1");
        instance.setRegionId("cn-hangzhou");

        assertThat(instance.getId()).isEqualTo(7L);
        assertThat(instance.getName()).isEqualTo("order-cluster");
        assertThat(instance.getRemark()).isEqualTo("production");
        assertThat(instance.getType()).isEqualTo("V5_PROXY_CLUSTER");
        assertThat(instance.getEndpoint()).isEqualTo("10.0.0.1:8080");
        assertThat(instance.getVendor()).isEqualTo("ALIYUN");
        assertThat(instance.getCloudInstanceId()).isEqualTo("rmq-abc123");
        assertThat(instance.getCredentialId()).isEqualTo(11L);
        assertThat(instance.getAdminCredentialRef()).isEqualTo("cred-ref-1");
        assertThat(instance.getRegionId()).isEqualTo("cn-hangzhou");
    }
}
