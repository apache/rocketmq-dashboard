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

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.apache.rocketmq.studio.persistence.entity.RmqNameserver;
import org.apache.rocketmq.studio.persistence.mapper.RmqNameserverMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:nameserver_registry_atomic;MODE=MySQL;DB_CLOSE_DELAY=-1;"
            + "DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
    "studio.auth.login-required=false"
})
@ActiveProfiles("dev")
class NameserverRegistryTransactionTest {

    @Autowired
    private NameserverRegistryService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private RmqNameserverMapper nameserverMapper;

    @Test
    void failedOptionalColumnCleanupShouldRollBackTheRegistryUpdateTest() {
        RmqNameserver original = insertEntry();
        String updatedName = "renamed-" + UUID.randomUUID();
        AtomicBoolean cleanupReached = new AtomicBoolean();
        try {
            doAnswer(invocation -> {
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT name FROM rmq_nameserver WHERE id = ?", String.class, original.getId()))
                        .isEqualTo(updatedName);
                cleanupReached.set(true);
                throw new IllegalStateException("optional column cleanup failed");
            }).when(nameserverMapper).update(isNull(), any(UpdateWrapper.class));

            assertThatThrownBy(() -> service.update(updateCommand(original.getId(), updatedName)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("optional column cleanup failed");

            assertThat(cleanupReached).isTrue();
            RmqNameserver persisted = nameserverMapper.selectById(original.getId());
            assertThat(persisted.getName()).isEqualTo(original.getName());
            assertThat(persisted.getNamesrvAddr()).isEqualTo(original.getNamesrvAddr());
            assertThat(persisted.getK8sNamespace()).isEqualTo(original.getK8sNamespace());
            assertThat(persisted.getK8sId()).isEqualTo(original.getK8sId());
            assertThat(persisted.getDescription()).isEqualTo(original.getDescription());
        } finally {
            deleteEntry(original.getId());
        }
    }

    @Test
    void successfulUpdateShouldCommitTheReplacementAndClearOptionalColumnsTest() {
        RmqNameserver original = insertEntry();
        String updatedName = "renamed-" + UUID.randomUUID();
        try {
            NameserverRegistryVO result = service.update(updateCommand(original.getId(), updatedName));

            RmqNameserver persisted = nameserverMapper.selectById(original.getId());
            assertThat(persisted.getName()).isEqualTo(updatedName);
            assertThat(persisted.getNamesrvAddr()).isEqualTo("new-nameserver:9876");
            assertThat(persisted.getK8sNamespace()).isNull();
            assertThat(persisted.getK8sId()).isNull();
            assertThat(persisted.getDescription()).isNull();
            assertThat(result.getName()).isEqualTo(persisted.getName());
            assertThat(result.getNamesrvAddr()).isEqualTo(persisted.getNamesrvAddr());
            assertThat(result.getK8sNamespace()).isNull();
            assertThat(result.getK8sId()).isNull();
            assertThat(result.getDescription()).isNull();
        } finally {
            deleteEntry(original.getId());
        }
    }

    private RmqNameserver insertEntry() {
        RmqNameserver entry = new RmqNameserver();
        entry.setName("original-" + UUID.randomUUID());
        entry.setNamesrvAddr("old-nameserver:9876");
        entry.setK8sNamespace("old-namespace");
        entry.setK8sId("old-k8s");
        entry.setDescription("old description");
        nameserverMapper.insert(entry);
        return entry;
    }

    private UpdateNameserverRegistryDTO updateCommand(Long id, String name) {
        return UpdateNameserverRegistryDTO.builder()
                .id(id)
                .name(name)
                .namesrvAddr("new-nameserver:9876")
                .build();
    }

    private void deleteEntry(Long id) {
        jdbcTemplate.update("DELETE FROM rmq_nameserver WHERE id = ?", id);
    }
}
