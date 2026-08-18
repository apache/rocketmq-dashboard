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
package org.apache.rocketmq.studio.cluster.nameserver;

import org.apache.rocketmq.studio.persistence.entity.RmqNameserver;
import org.apache.rocketmq.studio.persistence.mapper.RmqNameserverMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class NameserverSchemaIntegrationTest {

    @Autowired
    private RmqNameserverMapper nameserverMapper;

    @Test
    void schemaShouldEnforceUniqueRegistryNamesTest() {
        nameserverMapper.insert(nameserver("schema-unique-nameserver", "10.0.0.1:9876"));

        assertThatThrownBy(() -> nameserverMapper.insert(
                nameserver("schema-unique-nameserver", "10.0.0.2:9876")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private RmqNameserver nameserver(String name, String address) {
        RmqNameserver entity = new RmqNameserver();
        entity.setName(name);
        entity.setNamesrvAddr(address);
        return entity;
    }
}
