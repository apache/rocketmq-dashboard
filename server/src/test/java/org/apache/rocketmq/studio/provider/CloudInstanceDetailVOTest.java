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

package org.apache.rocketmq.studio.provider;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link CloudInstanceDetailVO}, one cloud-vendor instance in the import dialog: its
 * vendor-side identity (id, name, status, region, remark) and the endpoints it offers —
 * each endpoint a type/url pair the dialog lists for the user to pick from.
 */
class CloudInstanceDetailVOTest {

    @Test
    void anInstanceCarriesItsIdentityAndEndpoints() {
        CloudInstanceDetailVO instance = new CloudInstanceDetailVO();
        instance.setInstanceId("rmq-cn-7pp2x1a01");
        instance.setInstanceName("production");
        instance.setStatus("RUNNING");
        instance.setRegionId("cn-hangzhou");
        instance.setRemark("order traffic");
        instance.setEndpoints(List.of(
                new CloudInstanceDetailVO.CloudEndpoint("PUBLIC", "rmq-cn-xxx.public.example.com:9876"),
                new CloudInstanceDetailVO.CloudEndpoint("VPC", "rmq-cn-xxx.vpc.example.com:9876")));

        assertThat(instance.getInstanceId()).isEqualTo("rmq-cn-7pp2x1a01");
        assertThat(instance.getInstanceName()).isEqualTo("production");
        assertThat(instance.getStatus()).isEqualTo("RUNNING");
        assertThat(instance.getRegionId()).isEqualTo("cn-hangzhou");
        assertThat(instance.getRemark()).isEqualTo("order traffic");
        assertThat(instance.getEndpoints()).hasSize(2);
        assertThat(instance.getEndpoints()).extracting(CloudInstanceDetailVO.CloudEndpoint::getEndpointType)
                .containsExactly("PUBLIC", "VPC");
        assertThat(instance.getEndpoints()).extracting(CloudInstanceDetailVO.CloudEndpoint::getEndpointUrl)
                .containsExactly("rmq-cn-xxx.public.example.com:9876", "rmq-cn-xxx.vpc.example.com:9876");
    }

    @Test
    void anEndpointCarriesItsTypeAndUrl() {
        CloudInstanceDetailVO.CloudEndpoint endpoint =
                new CloudInstanceDetailVO.CloudEndpoint("PUBLIC", "http://example.com:9876");
        assertThat(endpoint.getEndpointType()).isEqualTo("PUBLIC");
        assertThat(endpoint.getEndpointUrl()).isEqualTo("http://example.com:9876");

        CloudInstanceDetailVO.CloudEndpoint empty = new CloudInstanceDetailVO.CloudEndpoint();
        assertThat(empty.getEndpointType()).isNull();
        assertThat(empty.getEndpointUrl()).isNull();
    }
}
