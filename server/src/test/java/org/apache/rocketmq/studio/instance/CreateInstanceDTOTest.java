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

class CreateInstanceDTOTest {

    private CreateInstanceDTO sample() {
        CreateInstanceDTO dto = new CreateInstanceDTO();
        dto.setName("prod-rocketmq");
        dto.setType(InstanceType.PROXY_CLUSTER);
        dto.setEndpoint("10.132.218.11:8081");
        dto.setRemark("production cluster");
        dto.setVendor(InstanceVendor.APACHE);
        dto.setCloudInstanceId("rmq-1");
        dto.setCredentialId(7L);
        dto.setAdminCredentialRef("cred-admin");
        dto.setRegionId("cn-hangzhou");
        return dto;
    }

    @Test
    void mapsEveryFieldToInstanceVo() {
        CreateInstanceDTO dto = sample();

        InstanceVO vo = dto.toInstanceVO();

        assertEquals("prod-rocketmq", vo.getName());
        assertEquals(InstanceType.PROXY_CLUSTER, vo.getType());
        assertEquals("10.132.218.11:8081", vo.getEndpoint());
        assertEquals("production cluster", vo.getRemark());
        assertEquals(InstanceVendor.APACHE, vo.getVendor());
        assertEquals("rmq-1", vo.getCloudInstanceId());
        assertEquals(7L, vo.getCredentialId());
        assertEquals("cred-admin", vo.getAdminCredentialRef());
        assertEquals("cn-hangzhou", vo.getRegionId());
    }

    @Test
    void leavesComputedCountersAtDefaults() {
        InstanceVO vo = sample().toInstanceVO();

        assertEquals(0, vo.getTopicCount());
        assertEquals(0, vo.getConsumerGroupCount());
        assertTrue(vo.isResourceCountsAvailable());
    }

    @Test
    void mapsEmptyPayloadWithoutNpe() {
        InstanceVO vo = new CreateInstanceDTO().toInstanceVO();

        assertNull(vo.getName());
        assertNull(vo.getType());
        assertNull(vo.getEndpoint());
        assertNull(vo.getCredentialId());
        assertTrue(vo.isResourceCountsAvailable());
    }
}
