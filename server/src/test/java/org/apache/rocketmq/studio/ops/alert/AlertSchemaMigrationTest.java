/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class AlertSchemaMigrationTest {
    @Test
    void upgradesExistingAlertTablesWithoutChangingFreshSchemaBehaviorTest() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:alert-schema-migration;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE rmq_alert_rule (id BIGINT PRIMARY KEY, name VARCHAR(128))");
            statement.execute("CREATE TABLE rmq_system_alert (id BIGINT PRIMARY KEY, time TIMESTAMP)");
            statement.execute("CREATE TABLE rmq_alert_notification_outbox (id BIGINT PRIMARY KEY, alert_id BIGINT, "
                    + "channel VARCHAR(32), status VARCHAR(16), next_attempt_at TIMESTAMP)");
        }

        AlertSchemaMigration migration = new AlertSchemaMigration(dataSource);
        migration.run(new DefaultApplicationArguments());
        migration.run(new DefaultApplicationArguments());

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'rmq_alert_rule' AND column_name IN "
                        + "('aggregation', 'window_seconds', 'domain', 'instance_id', 'consumer_group', 'topic', "
                        + "'consecutive_samples', 'notification_template')")) {
            result.next();
            assertThat(result.getInt(1)).isEqualTo(8);
        }

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_name IN ('rmq_metric_snapshot', 'rmq_alert_collection_lease', "
                        + "'rmq_alert_state', 'rmq_alert_silence', 'rmq_alert_notification_outbox')")) {
            result.next();
            assertThat(result.getInt(1)).isEqualTo(5);
        }

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'rmq_alert_notification_outbox' AND column_name IN "
                        + "('sending_started_at', 'claim_token', 'message_content')")) {
            result.next();
            assertThat(result.getInt(1)).isEqualTo(3);
        }
    }
}
