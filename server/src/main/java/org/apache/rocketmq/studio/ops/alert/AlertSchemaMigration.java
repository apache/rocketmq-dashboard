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
package org.apache.rocketmq.studio.ops.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** Adds native-alerting fields to alert tables created by earlier Studio builds. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertSchemaMigration implements ApplicationRunner {
    private static final List<Table> TABLES = List.of(
            new Table("rmq_metric_snapshot", "CREATE TABLE rmq_metric_snapshot ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "instance_id VARCHAR(128) NOT NULL, "
                    + "metric_key VARCHAR(128) NOT NULL, domain VARCHAR(16) NOT NULL, cluster_id VARCHAR(128), "
                    + "labels_hash CHAR(64) NOT NULL, labels_json TEXT NOT NULL, `value` DOUBLE, "
                    + "availability VARCHAR(16) NOT NULL, collected_at DATETIME NOT NULL)"),
            new Table("rmq_alert_collection_lease", "CREATE TABLE rmq_alert_collection_lease ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "lease_name VARCHAR(128) NOT NULL, "
                    + "holder_id VARCHAR(64) NOT NULL, expires_at DATETIME NOT NULL, "
                    + "CONSTRAINT uk_alert_collection_lease_name UNIQUE (lease_name))"),
            new Table("rmq_alert_state", "CREATE TABLE rmq_alert_state ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "rule_id BIGINT NOT NULL, fingerprint CHAR(64) NOT NULL, "
                    + "status VARCHAR(16) NOT NULL, consecutive_hits INT NOT NULL DEFAULT 0, current_value DOUBLE, "
                    + "first_pending_at DATETIME, fired_at DATETIME, last_notified_at DATETIME, resolved_at DATETIME, version INT NOT NULL DEFAULT 0, "
                    + "CONSTRAINT uk_alert_state_rule_fingerprint UNIQUE (rule_id, fingerprint))"),
            new Table("rmq_alert_silence", "CREATE TABLE rmq_alert_silence ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "domain VARCHAR(16), rule_id BIGINT, instance_id VARCHAR(128), "
                    + "labels_json TEXT, starts_at DATETIME NOT NULL, ends_at DATETIME NOT NULL, reason VARCHAR(512), "
                    + "created_by VARCHAR(128) NOT NULL)"),
            new Table("rmq_alert_notification_outbox", "CREATE TABLE rmq_alert_notification_outbox ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "alert_id BIGINT NOT NULL, channel VARCHAR(32) NOT NULL, "
                    + "status VARCHAR(16) NOT NULL, attempt_count INT NOT NULL DEFAULT 0, next_attempt_at DATETIME NOT NULL, "
                    + "sending_started_at DATETIME, claim_token VARCHAR(64), last_error VARCHAR(1000), delivered_at DATETIME, "
                    + "CONSTRAINT uk_alert_notification_outbox UNIQUE (alert_id, channel))"));
    private static final List<Column> COLUMNS = List.of(
            new Column("rmq_alert_rule", "aggregation", "VARCHAR(16) NOT NULL DEFAULT 'LAST'"),
            new Column("rmq_alert_rule", "window_seconds", "INT NOT NULL DEFAULT 0"),
            new Column("rmq_alert_rule", "domain", "VARCHAR(16) NOT NULL DEFAULT 'BUSINESS'"),
            new Column("rmq_alert_rule", "instance_id", "VARCHAR(128)"),
            new Column("rmq_alert_rule", "consumer_group", "VARCHAR(255)"),
            new Column("rmq_alert_rule", "topic", "VARCHAR(255)"),
            new Column("rmq_alert_rule", "consecutive_samples", "INT NOT NULL DEFAULT 1"),
            new Column("rmq_alert_rule", "reminder_interval", "VARCHAR(32) NOT NULL DEFAULT '30m'"),
            new Column("rmq_alert_rule", "notification_template", "TEXT"),
            new Column("rmq_alert_rule", "semantic_fingerprint", "CHAR(64)"),
            new Column("rmq_alert_state", "last_notified_at", "DATETIME"),
            new Column("rmq_system_alert", "acknowledged_by", "VARCHAR(128)"),
            new Column("rmq_system_alert", "acknowledged_at", "DATETIME"),
            new Column("rmq_system_alert", "domain", "VARCHAR(16)"),
            new Column("rmq_system_alert", "rule_id", "BIGINT"),
            new Column("rmq_system_alert", "fingerprint", "CHAR(64)"),
            new Column("rmq_system_alert", "transition", "VARCHAR(16)"),
            new Column("rmq_system_alert", "instance_id", "VARCHAR(128)"),
            new Column("rmq_system_alert", "current_value", "DOUBLE"),
            new Column("rmq_system_alert", "notification_suppressed", "TINYINT(1) NOT NULL DEFAULT 0"),
            new Column("rmq_system_alert", "suppression_cause_alert_id", "BIGINT"),
            new Column("rmq_system_alert", "suppression_reason", "VARCHAR(512)"),
            new Column("rmq_system_alert", "labels_json", "TEXT"),
            new Column("rmq_alert_notification_outbox", "gmt_create",
                    "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"),
            new Column("rmq_alert_notification_outbox", "gmt_modified",
                    "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"),
            new Column("rmq_alert_notification_outbox", "attempt_count", "INT NOT NULL DEFAULT 0"),
            new Column("rmq_alert_notification_outbox", "last_error", "VARCHAR(1000)"),
            new Column("rmq_alert_notification_outbox", "delivered_at", "DATETIME"),
            new Column("rmq_alert_notification_outbox", "sending_started_at", "DATETIME"),
            new Column("rmq_alert_notification_outbox", "claim_token", "VARCHAR(64)"),
            new Column("rmq_alert_notification_outbox", "message_content", "TEXT"),
            new Column("rmq_instance_message", "result_snapshot", "MEDIUMTEXT"));
    private static final List<Index> INDEXES = List.of(
            new Index("rmq_metric_snapshot", "idx_metric_snapshot_lookup", "instance_id, metric_key, collected_at"),
            new Index("rmq_metric_snapshot", "idx_metric_snapshot_scope_cluster",
                    "instance_id, metric_key, domain, labels_hash, cluster_id, availability, collected_at"),
            new Index("rmq_metric_snapshot", "idx_metric_snapshot_scope_global",
                    "instance_id, metric_key, domain, labels_hash, availability, collected_at"),
            new Index("rmq_metric_snapshot", "idx_metric_snapshot_retention", "collected_at"),
            new Index("rmq_alert_silence", "idx_alert_silence_active", "starts_at, ends_at"),
            new Index("rmq_alert_silence", "idx_alert_silence_expiry", "ends_at, starts_at"),
            new Index("rmq_alert_silence", "idx_alert_silence_scope", "domain, rule_id, instance_id"),
            new Index("rmq_alert_notification_outbox", "idx_alert_notification_ready", "status, next_attempt_at"),
            new Index("rmq_alert_notification_outbox", "idx_alert_notification_delivered_retention",
                    "status, delivered_at"),
            new Index("rmq_alert_notification_outbox", "idx_alert_notification_modified_retention",
                    "status, gmt_modified"),
            new Index("rmq_alert_rule", "uk_alert_rule_semantic_fingerprint", "semantic_fingerprint", true),
            new Index("rmq_system_alert", "idx_system_alert_domain_time", "domain, time"),
            new Index("rmq_system_alert", "idx_system_alert_feed", "domain, instance_id, transition, time"));

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            DatabaseMetaData metadata = connection.getMetaData();
            for (Table table : TABLES) {
                ensureTable(metadata, connection.getCatalog(), statement, table);
            }
            for (Column column : COLUMNS) {
                ensureColumn(metadata, connection.getCatalog(), statement, column);
            }
            for (Index index : INDEXES) {
                ensureIndex(metadata, connection.getCatalog(), statement, index);
            }
        }
    }

    private static void ensureTable(DatabaseMetaData metadata, String catalog, Statement statement, Table table)
            throws Exception {
        if (hasTable(metadata, catalog, table.name())) {
            return;
        }
        try {
            log.info("Creating native alerting table {}", table.name());
            statement.executeUpdate(table.definition());
        } catch (SQLException failure) {
            if (!hasTable(metadata, catalog, table.name())) {
                throw failure;
            }
        }
    }

    private static void ensureColumn(DatabaseMetaData metadata, String catalog, Statement statement, Column column)
            throws Exception {
        if (hasColumn(metadata, catalog, column.table(), column.name())) {
            return;
        }
        try {
            log.info("Adding native alerting column {}.{}", column.table(), column.name());
            statement.executeUpdate("ALTER TABLE " + column.table() + " ADD COLUMN " + column.name()
                    + " " + column.definition());
        } catch (SQLException failure) {
            if (!hasColumn(metadata, catalog, column.table(), column.name())) {
                throw failure;
            }
        }
    }

    private static void ensureIndex(DatabaseMetaData metadata, String catalog, Statement statement, Index index)
            throws Exception {
        if (hasIndex(metadata, catalog, index.table(), index.name())) {
            return;
        }
        try {
            log.info("Adding native alerting index {}.{}", index.table(), index.name());
            statement.executeUpdate("CREATE " + (index.unique() ? "UNIQUE " : "") + "INDEX " + index.name() + " ON " + index.table()
                    + " (" + index.columns() + ")");
        } catch (SQLException failure) {
            if (!hasIndex(metadata, catalog, index.table(), index.name())) {
                throw failure;
            }
        }
    }

    private static boolean hasColumn(DatabaseMetaData metadata, String catalog, String table, String column)
            throws Exception {
        try (ResultSet columns = metadata.getColumns(catalog, null, table, column)) {
            return columns.next();
        }
    }

    private static boolean hasTable(DatabaseMetaData metadata, String catalog, String table) throws Exception {
        try (ResultSet tables = metadata.getTables(catalog, null, table, new String[] {"TABLE"})) {
            return tables.next();
        }
    }

    private static boolean hasIndex(DatabaseMetaData metadata, String catalog, String table, String index)
            throws Exception {
        try (ResultSet indexes = metadata.getIndexInfo(catalog, null, table, false, false)) {
            while (indexes.next()) {
                if (index.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private record Column(String table, String name, String definition) {
    }

    private record Table(String name, String definition) {
    }

    private record Index(String table, String name, String columns, boolean unique) {
        private Index(String table, String name, String columns) {
            this(table, name, columns, false);
        }
    }
}
