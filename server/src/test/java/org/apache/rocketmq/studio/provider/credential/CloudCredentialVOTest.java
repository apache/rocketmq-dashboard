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
package org.apache.rocketmq.studio.provider.credential;

import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class CloudCredentialVOTest {

    @Test
    void freshVoCarriesNullFields() {
        CloudCredentialVO vo = new CloudCredentialVO();

        assertNull(vo.getId());
        assertNull(vo.getName());
        assertNull(vo.getVendor());
        assertNull(vo.getAccessKey());
        assertNull(vo.getSecretKey());
        assertNull(vo.getRemark());
    }

    @Test
    void settersRoundTripEveryField() {
        CloudCredentialVO vo = new CloudCredentialVO();

        vo.setId(3L);
        vo.setName("aliyun-prod");
        vo.setVendor(InstanceVendor.ALIYUN);
        vo.setAccessKey("ak-1");
        vo.setSecretKey("sk-1");
        vo.setRemark("production");

        assertEquals(3L, vo.getId());
        assertEquals("aliyun-prod", vo.getName());
        assertEquals(InstanceVendor.ALIYUN, vo.getVendor());
        assertEquals("ak-1", vo.getAccessKey());
        assertEquals("sk-1", vo.getSecretKey());
        assertEquals("production", vo.getRemark());
    }

    @Test
    void secretKeyIsExcludedFromToString() {
        CloudCredentialVO vo = new CloudCredentialVO();
        vo.setAccessKey("ak-1");
        vo.setSecretKey("must-not-leak");

        assertFalse(vo.toString().contains("must-not-leak"));
    }
}
