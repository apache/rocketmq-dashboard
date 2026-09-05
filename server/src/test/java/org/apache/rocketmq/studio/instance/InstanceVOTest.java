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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceVOTest {

    @Test
    void builderDefaultsDescribeFreshInstance() {
        InstanceVO vo = InstanceVO.builder().build();

        assertNull(vo.getName());
        assertNull(vo.getType());
        assertNull(vo.getEndpoint());
        assertNull(vo.getVendor());
        assertNull(vo.getCredentialId());
        assertEquals(0, vo.getTopicCount());
        assertEquals(0, vo.getConsumerGroupCount());
        assertTrue(vo.isResourceCountsAvailable());
    }

    @Test
    void settersAndBuilderCarryInstanceState() {
        InstanceVO vo = InstanceVO.builder()
            .name("prod-1")
            .type(InstanceType.PROXY_CLUSTER)
            .endpoint("10.0.0.1:8080")
            .vendor(InstanceVendor.APACHE)
            .cloudInstanceId("rmq-1")
            .credentialId(3L)
            .adminCredentialRef("cred-1")
            .regionId("cn-hangzhou")
            .regionName("cn-hangzhou")
            .build();
        vo.setId(1L);
        vo.setTopicCount(5);

        assertEquals("prod-1", vo.getName());
        assertEquals(InstanceType.PROXY_CLUSTER, vo.getType());
        assertEquals(InstanceVendor.APACHE, vo.getVendor());
        assertEquals(1L, vo.getId());
        assertEquals(5, vo.getTopicCount());
        assertTrue(vo.isResourceCountsAvailable());
    }
}
