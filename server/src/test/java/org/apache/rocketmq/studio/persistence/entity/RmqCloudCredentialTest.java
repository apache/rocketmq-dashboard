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

class RmqCloudCredentialTest {

    @Test
    void freshEntityCarriesNullFields() {
        RmqCloudCredential entity = new RmqCloudCredential();

        assertNull(entity.getId());
        assertNull(entity.getName());
        assertNull(entity.getVendor());
        assertNull(entity.getAccessKey());
        assertNull(entity.getSecretKey());
        assertNull(entity.getRemark());
        assertNull(entity.getGmtCreate());
    }

    @Test
    void settersRoundTripEveryField() {
        RmqCloudCredential entity = new RmqCloudCredential();
        LocalDateTime created = LocalDateTime.parse("2026-09-01T08:00:00");

        entity.setId(3L);
        entity.setName("aliyun-prod");
        entity.setVendor("ALIYUN");
        entity.setAccessKey("ak-1");
        entity.setSecretKey("sk-1");
        entity.setRemark("production credential");
        entity.setGmtCreate(created);
        entity.setGmtModified(created);

        assertEquals(3L, entity.getId());
        assertEquals("aliyun-prod", entity.getName());
        assertEquals("ALIYUN", entity.getVendor());
        assertEquals("ak-1", entity.getAccessKey());
        assertEquals("sk-1", entity.getSecretKey());
        assertEquals("production credential", entity.getRemark());
        assertEquals(created, entity.getGmtCreate());
    }
}
