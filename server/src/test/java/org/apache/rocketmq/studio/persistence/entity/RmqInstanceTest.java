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

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RmqInstanceTest {

    @Test
    void freshEntityCarriesNullFields() {
        RmqInstance entity = new RmqInstance();

        assertNull(entity.getId());
        assertNull(entity.getName());
        assertNull(entity.getType());
        assertNull(entity.getVendor());
        assertNull(entity.getCredentialId());
        assertNull(entity.getGmtCreate());
        assertNull(entity.getGmtModified());
    }

    @Test
    void settersRoundTripEveryField() {
        RmqInstance entity = new RmqInstance();
        LocalDateTime created = LocalDateTime.parse("2026-09-01T08:00:00");
        LocalDateTime modified = LocalDateTime.parse("2026-09-02T09:00:00");

        entity.setId(1L);
        entity.setName("prod-1");
        entity.setRemark("production");
        entity.setType("PROXY_CLUSTER");
        entity.setEndpoint("10.0.0.1:8080");
        entity.setVendor("APACHE");
        entity.setCloudInstanceId("rmq-1");
        entity.setCredentialId(3L);
        entity.setAdminCredentialRef("cred-1");
        entity.setRegionId("cn-hangzhou");
        entity.setGmtCreate(created);
        entity.setGmtModified(modified);

        assertEquals(1L, entity.getId());
        assertEquals("prod-1", entity.getName());
        assertEquals("production", entity.getRemark());
        assertEquals("PROXY_CLUSTER", entity.getType());
        assertEquals("10.0.0.1:8080", entity.getEndpoint());
        assertEquals("APACHE", entity.getVendor());
        assertEquals("rmq-1", entity.getCloudInstanceId());
        assertEquals(3L, entity.getCredentialId());
        assertEquals("cred-1", entity.getAdminCredentialRef());
        assertEquals("cn-hangzhou", entity.getRegionId());
        assertEquals(created, entity.getGmtCreate());
        assertEquals(modified, entity.getGmtModified());
    }
}
