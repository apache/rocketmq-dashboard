/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.rocketmq.studio.auth;

import org.apache.rocketmq.studio.persistence.entity.RmqStudioUser;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioSessionMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "studio.auth.login-required=true",
    "studio.auth.users[0].username=bootstrap-admin",
    "studio.auth.users[0].password=password-123",
    "studio.auth.users[0].admin=true",
    "spring.datasource.url=jdbc:h2:mem:auth_upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.sql.init.mode=never"
})
class AuthVolumeUpgradeIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthService authService;

    @Autowired
    private RmqStudioUserMapper userMapper;

    @Autowired
    private RmqStudioSessionMapper sessionMapper;

    @BeforeEach
    void resetLegacySchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS rmq_studio_session");
        jdbcTemplate.execute("DROP TABLE IF EXISTS rmq_studio_user");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS rmq_settings (
                  id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
                  gmt_create datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  gmt_modified datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  json text NOT NULL,
                  PRIMARY KEY (id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS rmq_instance_message (
                  id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
                  gmt_create datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS rmq_instance_trace (
                  id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
                  gmt_create datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id)
                )
                """);
        jdbcTemplate.update("DELETE FROM rmq_settings");
        jdbcTemplate.update("DELETE FROM rmq_instance_message");
        jdbcTemplate.update("DELETE FROM rmq_instance_trace");
    }

    @Test
    void upgradeScriptCreatesAuthTablesIdempotentlyTest() {
        applyUpgradeScript();
        jdbcTemplate.update("""
                INSERT INTO rmq_studio_user
                    (username, password_hash, admin, enabled, password_changed_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, "existing-admin", "pbkdf2$test", 1, 1);
        applyUpgradeScript();

        assertThat(tableExists("rmq_studio_user")).isTrue();
        assertThat(tableExists("rmq_studio_session")).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rmq_studio_user WHERE username = ?",
                Integer.class,
                "existing-admin")).isEqualTo(1);
    }

    @Test
    void upgradedLegacySchemaSupportsBootstrapLoginTest() {
        applyUpgradeScript();

        LoginDTO request = new LoginDTO();
        request.setUsername("bootstrap-admin");
        request.setPassword("password-123");

        LoginVO login = authService.login(request);

        assertThat(login.getUser().getUserId()).isNotNull();
        assertThat(login.getUser().getUsername()).isEqualTo("bootstrap-admin");
        assertThat(login.getUser().isAdmin()).isTrue();

        RmqStudioUser persisted = userMapper.selectById(login.getUser().getUserId());
        assertThat(persisted).isNotNull();
        assertThat(persisted.getUsername()).isEqualTo("bootstrap-admin");
        assertThat(sessionMapper.selectCount(null)).isEqualTo(1);
        assertThat(authService.isAuthenticated("Bearer " + login.getToken())).isTrue();
    }

    private void applyUpgradeScript() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new FileSystemResource(upgradeScriptPath()));
        populator.execute(dataSource);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE lower(table_name) = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    private String upgradeScriptPath() {
        return Path.of("..", "deploy", "mysql", "upgrade-auth-tables.sql").toAbsolutePath().toString();
    }
}
