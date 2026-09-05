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

class PlainAccessConfigVOTest {

    @Test
    void builderDefaultsDescribeEmptyAccount() {
        PlainAccessConfigVO vo = PlainAccessConfigVO.builder().build();

        assertNull(vo.getAccessKey());
        assertNull(vo.getSecretKey());
        assertNull(vo.getWhiteRemoteAddress());
        assertFalse(vo.isAdmin());
        assertNull(vo.getDefaultTopicPerm());
        assertNull(vo.getTopicPerms());
        assertNull(vo.getGroupPerms());
        assertNull(vo.getGmtCreate());
    }

    @Test
    void allArgsCarryAccountState() {
        PlainAccessConfigVO vo = PlainAccessConfigVO.builder()
            .accessKey("AK-1")
            .secretKey("sk-1")
            .whiteRemoteAddress("10.0.0.0/8")
            .admin(true)
            .defaultTopicPerm("PUB")
            .defaultGroupPerm("SUB")
            .topicPerms(List.of("order-*=PUB"))
            .groupPerms(List.of("cg-order-*=SUB"))
            .build();

        assertEquals("AK-1", vo.getAccessKey());
        assertEquals("sk-1", vo.getSecretKey());
        assertTrue(vo.isAdmin());
        assertEquals("PUB", vo.getDefaultTopicPerm());
        assertEquals(List.of("order-*=PUB"), vo.getTopicPerms());
        assertEquals(List.of("cg-order-*=SUB"), vo.getGroupPerms());
    }
}
