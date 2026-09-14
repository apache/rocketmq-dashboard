/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.message;

import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@JdbcTest(properties = {"spring.sql.init.mode=never",
    "spring.datasource.url=jdbc:h2:mem:named-queries;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = NamedMessageQueryService.class)
@Import(NamedMessageQueryService.class)
class NamedMessageQueryServiceTest {
    @Autowired
    private NamedMessageQueryService service;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @MockitoBean
    private InstanceService instanceService;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS rmq_instance (id BIGINT PRIMARY KEY)");
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/named-message-queries.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/named-message-queries.sql"));
        }
        jdbc.update("INSERT INTO rmq_instance(id) VALUES (1), (2)");
        when(instanceService.resolveInstanceId("alpha")).thenReturn(1L);
        when(instanceService.resolveInstanceId("beta")).thenReturn(2L);
    }

    private NamedMessageQueryService.Draft draft(String instance, String name) {
        return new NamedMessageQueryService.Draft(instance, name, "key", "orders", "key-1", null, null, null);
    }

    @Test
    void sharesAcrossOperatorsAndServiceInstancesTest() {
        try {
            AuthenticatedUserContext.setUser("alice", false);
            service.save(draft("alpha", "Order lookup"));
            AuthenticatedUserContext.setUser("bob", false);
            var secondServer = new NamedMessageQueryService(jdbc, instanceService);
            assertThat(secondServer.list("alpha")).singleElement().satisfies(query -> {
                assertThat(query.name()).isEqualTo("Order lookup");
                assertThat(query.createdBy()).isEqualTo("alice");
                assertThat(query.key()).isEqualTo("key-1");
                assertThat(query.startTime()).isNull();
            });
            assertThat(secondServer.list("beta")).isEmpty();
        } finally {
            AuthenticatedUserContext.clear();
        }
    }

    @Test
    void rejectsDuplicateNamesWithoutDroppingExistingQueriesTest() {
        service.save(draft("alpha", "Orders"));
        assertThatThrownBy(() -> service.save(draft("alpha", " orders "))).isInstanceOf(BusinessException.class);
        service.save(draft("beta", "Orders"));
        assertThat(service.list("alpha")).hasSize(1);
    }

    @Test
    void enforcesCapacityWithoutEvictingSharedQueriesTest() {
        for (int i = 0; i < 50; i++) {
            service.save(draft("alpha", "query-" + i));
        }
        assertThatThrownBy(() -> service.save(draft("alpha", "overflow"))).isInstanceOf(BusinessException.class);
        assertThat(service.list("alpha")).hasSize(50).noneMatch(query -> query.name().equals("overflow"));
    }

    @Test
    void renamesAndDeletesOnlyWithinRequestedInstanceTest() {
        service.save(draft("alpha", "Orders"));
        String id = service.list("alpha").getFirst().id();
        assertThatThrownBy(() -> service.rename("beta", id, "Wrong")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.delete("beta", id)).isInstanceOf(BusinessException.class);
        service.rename("alpha", id, "Updated");
        assertThat(service.list("alpha").getFirst().name()).isEqualTo("Updated");
        service.delete("alpha", id);
        assertThat(service.list("alpha")).isEmpty();
    }

    @Test
    void validatesModeTimeRangeAndRequiredFieldsTest() {
        assertThatThrownBy(() -> service.save(draft("alpha", " "))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.save(draft("alpha", "x".repeat(81)))).isInstanceOf(BusinessException.class);
        for (String mode : new String[] {"queue", "topic", "msgid"}) {
            assertThatThrownBy(() -> service.save(new NamedMessageQueryService.Draft(
                    "alpha", mode, mode, "orders", null, null, 20L, 10L))).isInstanceOf(BusinessException.class);
        }
        service.save(new NamedMessageQueryService.Draft("alpha", "Time", "topic", "orders", null, null, 10L, 20L));
        service.save(new NamedMessageQueryService.Draft("alpha", "ID", "msgid", "orders", null, "msg-1", null, null));
        assertThat(service.list("alpha")).hasSize(2);
        assertThat(service.list("alpha")).anySatisfy(query -> {
            assertThat(query.mode()).isEqualTo("topic");
            assertThat(query.startTime()).isEqualTo(10L);
            assertThat(query.endTime()).isEqualTo(20L);
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentSavesCannotExceedCapacityTest() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 49; i++) {
                service.save(draft("alpha", "query-" + i));
            }
            CountDownLatch start = new CountDownLatch(1);
            var first = executor.submit(() -> saveAfterSignal(start, "first"));
            var second = executor.submit(() -> saveAfterSignal(start, "second"));
            start.countDown();
            int saved = (first.get(10, TimeUnit.SECONDS) ? 1 : 0) + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(saved).isEqualTo(1);
            assertThat(service.list("alpha")).hasSize(50);
        } finally {
            jdbc.update("DELETE FROM rmq_named_message_query");
            jdbc.update("DELETE FROM rmq_instance WHERE id IN (1, 2)");
        }
    }

    private boolean saveAfterSignal(CountDownLatch start, String name) throws InterruptedException {
        start.await();
        try {
            service.save(draft("alpha", name));
            return true;
        } catch (BusinessException full) {
            return false;
        }
    }
}
