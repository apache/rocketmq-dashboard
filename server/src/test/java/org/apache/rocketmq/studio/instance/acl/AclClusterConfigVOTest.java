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
package org.apache.rocketmq.studio.instance.acl;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AclClusterConfigVOTest {

    @Test
    void builderDefaultsDescribeEmptyConfig() {
        AclClusterConfigVO vo = AclClusterConfigVO.builder().build();

        assertNull(vo.getClusterId());
        assertFalse(vo.isAclEnabled());
        assertNull(vo.getAclVersion());
        assertNull(vo.getGlobalWhiteRemoteAddresses());
        assertNull(vo.getAccounts());
        assertEquals(0, vo.getAccountCount());
    }

    @Test
    void allArgsCarryConfigState() {
        AclClusterConfigVO vo = AclClusterConfigVO.builder()
            .clusterId("cluster-1")
            .aclEnabled(true)
            .aclVersion("ACL 2.0")
            .globalWhiteRemoteAddresses(List.of("10.0.0.0/8"))
            .accounts(List.of())
            .accountCount(2)
            .build();

        assertEquals("cluster-1", vo.getClusterId());
        assertTrue(vo.isAclEnabled());
        assertEquals("ACL 2.0", vo.getAclVersion());
        assertEquals(List.of("10.0.0.0/8"), vo.getGlobalWhiteRemoteAddresses());
        assertEquals(2, vo.getAccountCount());
    }
}
