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
package org.apache.rocketmq.studio.instance;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.provider.apache.RocketMQDefaultClusterResolver;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Validates the migration after SQL initialization and before write services start; never deduplicates or rewrites ownership. */
@Component
@DependsOnDatabaseInitialization
@RequiredArgsConstructor
public class ResourceOwnershipSchemaMigration implements InitializingBean {
    private final DataSource dataSource;
    private final RocketMQDefaultClusterResolver defaultClusters;

    @Override
    public void afterPropertiesSet() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            // Both tables are pre-checked; no DDL runs on any conflict and no record is discarded.
            for (String kind : List.of("topic", "group")) {
                String table = "rmq_instance_" + kind;
                try (ResultSet rows = statement.executeQuery("SELECT name, COUNT(*) FROM " + table
                        + " GROUP BY name HAVING COUNT(*) > 1")) {
                    if (rows.next()) {
                        throw new IllegalStateException("Global resource uniqueness migration failed: duplicate name requires manual resolution: "
                                + table + "/" + rows.getString(1));
                    }
                }
            }
            List<String> virtualNames = null;
            for (String kind : List.of("topic", "group")) {
                try (ResultSet rows = statement.executeQuery("SELECT r.name, r.instance_id, r.cluster_id, i.name, owner.name"
                        + " FROM rmq_instance_" + kind + " r LEFT JOIN rmq_instance i ON i.name = r.cluster_id"
                        + " LEFT JOIN rmq_instance owner ON owner.name = r.instance_id")) {
                    while (rows.next()) {
                        if (!StringUtils.hasText(rows.getString(1)) || !StringUtils.hasText(rows.getString(3))
                                || !rows.getString(1).equals(rows.getString(1).trim())
                                || !rows.getString(3).equals(rows.getString(3).trim())) {
                            throw new IllegalStateException("Resource ownership cannot be determined and requires manual confirmation: " + rows.getString(1));
                        }
                        if (!StringUtils.hasText(rows.getString(2))) {
                            if (virtualNames == null) {
                                virtualNames = defaultClusters.names();
                            }
                            if (rows.getString(4) != null || !virtualNames.contains(rows.getString(3))) {
                                throw new IllegalStateException("Legacy empty instance_id has no trusted virtual cluster mapping and requires manual confirmation: "
                                        + rows.getString(1));
                            }
                        } else if (!rows.getString(2).equals(rows.getString(2).trim())) {
                            throw new IllegalStateException("Instance ownership identifier is not normalized and requires manual confirmation: " + rows.getString(1));
                        } else if (rows.getString(5) == null) {
                            if (virtualNames == null) {
                                virtualNames = defaultClusters.names();
                            }
                            if (!rows.getString(2).equals(rows.getString(3))
                                    || !virtualNames.contains(rows.getString(3))) {
                                throw new IllegalStateException("Owning instance of the resource does not exist and has no trusted virtual mapping: " + rows.getString(1));
                            }
                        }
                    }
                }
            }
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rmq_instance_ownership_lock ("
                    + "id bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'primary key',"
                    + "gmt_create datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',"
                    + "gmt_modified datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'modify time',"
                    + "name VARCHAR(128) NOT NULL, PRIMARY KEY (id), UNIQUE KEY uk_ownership_instance_name (name))"
                    + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            for (String kind : List.of("topic", "group")) {
                String table = "rmq_instance_" + kind;
                ensureIndex(connection, table, "uk_" + kind + "_name", "name", true);
                ensureIndex(connection, table, "idx_" + kind + "_instance", "instance_id", false);
                ensureIndex(connection, table, "idx_" + kind + "_cluster", "cluster_id", false);
                // Create the global unique key before dropping the legacy composite index; a mid-way failure keeps existing constraints.
                String legacy = "uk_cluster_instance_" + kind;
                if (indexes(connection, table).containsKey(legacy)) {
                    try {
                        statement.executeUpdate("ALTER TABLE " + table + " DROP INDEX " + legacy);
                    } catch (SQLException failure) {
                        if (indexes(connection, table).containsKey(legacy)) {
                            throw failure;
                        }
                    }
                }
            }
        }
    }

    private void ensureIndex(Connection connection, String table, String name, String column, boolean unique)
            throws SQLException {
        Map<String, List<String>> indexes = indexes(connection, table);
        String expected = (unique ? "unique:" : "index:") + column;
        if (indexes.containsKey(name)) {
            if (!indexes.get(name).equals(List.of(expected))) {
                throw new IllegalStateException("Index definition violates the ownership constraint: " + table + "." + name);
            }
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE " + (unique ? "UNIQUE " : "") + "INDEX " + name
                    + " ON " + table + " (" + column + ")");
        } catch (SQLException failure) {
            // Concurrent startups only accept an identical index; failures caused by duplicate data must not be swallowed.
            if (!List.of(expected).equals(indexes(connection, table).get(name))) {
                throw failure;
            }
        }
    }

    private Map<String, List<String>> indexes(Connection connection, String table) throws SQLException {
        Map<String, List<String>> indexes = new HashMap<>();
        try (ResultSet rows = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, table, false, false)) {
            while (rows.next()) {
                String name = rows.getString("INDEX_NAME");
                String column = rows.getString("COLUMN_NAME");
                if (name != null && column != null) {
                    indexes.computeIfAbsent(name.toLowerCase(java.util.Locale.ROOT), ignored -> new ArrayList<>())
                            .add((rows.getBoolean("NON_UNIQUE") ? "index:" : "unique:")
                                    + column.toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        return indexes;
    }
}
