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
import org.apache.rocketmq.studio.cluster.nameserver.NamesrvAddrParser;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Moves resource uniqueness from the bare name to the (domain, cluster, name) slot: adds the missing
 * columns on already initialized databases, resolves every instance to its domain (registering a
 * NameServer row for endpoints the registry does not know yet), backfills the domain of every resource
 * row, refuses to start on an unresolvable row or a duplicated slot, and only then swaps the unique keys.
 */
@Component
@DependsOnDatabaseInitialization
@RequiredArgsConstructor
public class ResourceOwnershipSchemaMigration implements InitializingBean {
    /** Bounded retries for the index-metadata read; see {@link #indexes(Connection, String)}. */
    private static final int INDEX_METADATA_ATTEMPTS = 5;

    private final DataSource dataSource;

    @Override
    public void afterPropertiesSet() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            ensureColumns(connection, statement);
            Map<String, String> domains = backfillInstances(connection);
            for (String kind : List.of("topic", "group")) {
                backfillResources(connection, "rmq_instance_" + kind, domains);
            }
            for (String kind : List.of("topic", "group")) {
                String table = "rmq_instance_" + kind;
                try (ResultSet rows = statement.executeQuery("SELECT name FROM " + table
                        + " GROUP BY domain, cluster_id, name HAVING COUNT(*) > 1")) {
                    if (rows.next()) {
                        throw new IllegalStateException("Resource uniqueness migration failed: the same name is registered"
                                + " twice in one slot and needs manual resolution: " + table + "/" + rows.getString(1));
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
                // Create the slot unique key before dropping the name-only one; a mid-way failure keeps existing constraints.
                ensureIndex(connection, table, "uk_" + kind + "_domain", List.of("domain", "cluster_id", "name"), true);
                ensureIndex(connection, table, "idx_" + kind + "_instance", List.of("instance_id"), false);
                ensureIndex(connection, table, "idx_" + kind + "_cluster", List.of("cluster_id"), false);
                ensureIndex(connection, table, "idx_" + kind + "_domain", List.of("domain"), false);
                dropIndex(connection, table, "uk_" + kind + "_name");
                dropIndex(connection, table, "uk_cluster_instance_" + kind);
            }
        }
    }

    private void ensureColumns(Connection connection, Statement statement) throws SQLException {
        addColumnIfMissing(connection, statement, "rmq_instance", "nameserver_id",
                "bigint(20) unsigned COMMENT 'bound rmq_nameserver.id; validated in the application layer, no foreign key'");
        for (String kind : List.of("topic", "group")) {
            addColumnIfMissing(connection, statement, "rmq_instance_" + kind, "domain",
                    "VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'uniqueness domain: ns:<nameserver_id> or cloud:<cloud_instance_id>'");
        }
    }

    private void addColumnIfMissing(Connection connection, Statement statement, String table, String column,
                                    String definition) throws SQLException {
        if (columns(connection, table).contains(column)) {
            return;
        }
        statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    private List<String> columns(Connection connection, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet rows = connection.getMetaData().getColumns(connection.getCatalog(), null, table, null)) {
            while (rows.next()) {
                columns.add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return columns;
    }

    /** Resolves every instance to its domain, registering a NameServer row for endpoints the registry does not know yet. */
    private Map<String, String> backfillInstances(Connection connection) throws SQLException {
        Map<String, String> domains = new LinkedHashMap<>();
        List<Long> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> endpoints = new ArrayList<>();
        List<String> vendors = new ArrayList<>();
        List<String> cloudIds = new ArrayList<>();
        List<Long> nameserverIds = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, name, endpoint, vendor, cloud_instance_id,"
                     + " nameserver_id FROM rmq_instance")) {
            while (rows.next()) {
                ids.add(rows.getLong(1));
                names.add(rows.getString(2));
                endpoints.add(rows.getString(3));
                vendors.add(rows.getString(4));
                cloudIds.add(rows.getString(5));
                nameserverIds.add(rows.getLong(6));
            }
        }
        for (int i = 0; i < ids.size(); i++) {
            String vendor = vendors.get(i);
            if (StringUtils.hasText(vendor) && !"APACHE".equals(vendor)) {
                if (!StringUtils.hasText(cloudIds.get(i))) {
                    throw new IllegalStateException("Cloud instance has no cloud_instance_id and cannot be placed in a"
                            + " uniqueness domain; manual resolution required: " + names.get(i));
                }
                domains.put(names.get(i), ResourceOwnershipGuard.cloudDomain(cloudIds.get(i)));
                continue;
            }
            if (nameserverIds.get(i) > 0) {
                domains.put(names.get(i), ResourceOwnershipGuard.nameserverDomain(nameserverIds.get(i)));
                continue;
            }
            long nameserverId = requireNameserverId(connection, names.get(i), endpoints.get(i));
            try (PreparedStatement update = connection.prepareStatement("UPDATE rmq_instance SET nameserver_id = ? WHERE id = ?")) {
                update.setLong(1, nameserverId);
                update.setLong(2, ids.get(i));
                update.executeUpdate();
            }
            domains.put(names.get(i), ResourceOwnershipGuard.nameserverDomain(nameserverId));
        }
        return domains;
    }

    private long requireNameserverId(Connection connection, String instanceName, String endpoint) throws SQLException {
        String normalized = NamesrvAddrParser.normalize(endpoint);
        try (PreparedStatement select = connection.prepareStatement("SELECT id FROM rmq_nameserver WHERE namesrv_addr = ?")) {
            select.setString(1, normalized);
            try (ResultSet rows = select.executeQuery()) {
                if (rows.next()) {
                    return rows.getLong(1);
                }
            }
        }
        String name = "ns-" + instanceName;
        try (PreparedStatement existing = connection.prepareStatement("SELECT COUNT(*) FROM rmq_nameserver WHERE name = ?")) {
            existing.setString(1, name);
            try (ResultSet rows = existing.executeQuery()) {
                rows.next();
                if (rows.getLong(1) > 0) {
                    name = name + "-" + System.nanoTime();
                }
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO rmq_nameserver (name, namesrv_addr, status) VALUES (?, ?, 'healthy')",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, name);
            insert.setString(2, normalized);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("NameServer registration returned no id for instance: " + instanceName);
                }
                return keys.getLong(1);
            }
        }
    }

    private void backfillResources(Connection connection, String table, Map<String, String> domains) throws SQLException {
        List<Long> ids = new ArrayList<>();
        List<String> owners = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, instance_id, cluster_id FROM " + table
                     + " WHERE domain = '' OR domain IS NULL")) {
            while (rows.next()) {
                ids.add(rows.getLong(1));
                // Legacy unclaimed rows fall back to their cluster, which used to double as the virtual instance name.
                String instanceId = rows.getString(2);
                owners.add(StringUtils.hasText(instanceId) ? instanceId : rows.getString(3));
            }
        }
        for (int i = 0; i < ids.size(); i++) {
            String domain = domains.get(owners.get(i));
            if (domain == null) {
                throw new IllegalStateException("Resource ownership cannot be determined and requires manual confirmation: "
                        + table + "/" + ids.get(i) + " owner=" + owners.get(i));
            }
            try (PreparedStatement update = connection.prepareStatement("UPDATE " + table + " SET domain = ? WHERE id = ?")) {
                update.setString(1, domain);
                update.setLong(2, ids.get(i));
                update.executeUpdate();
            }
        }
    }

    private void ensureIndex(Connection connection, String table, String name, List<String> columns, boolean unique)
            throws SQLException {
        Map<String, List<String>> indexes = indexes(connection, table);
        List<String> expected = columns.stream().map(column -> (unique ? "unique:" : "index:") + column).toList();
        if (indexes.containsKey(name)) {
            if (!indexes.get(name).equals(expected)) {
                throw new IllegalStateException("Index definition violates the ownership constraint: " + table + "." + name);
            }
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE " + (unique ? "UNIQUE " : "") + "INDEX " + name
                    + " ON " + table + " (" + String.join(", ", columns) + ")");
        } catch (SQLException failure) {
            // Concurrent startups only accept an identical index; failures caused by duplicate data must not be swallowed.
            if (!expected.equals(indexes(connection, table).get(name))) {
                throw failure;
            }
        }
    }

    private void dropIndex(Connection connection, String table, String name) throws SQLException {
        if (!indexes(connection, table).containsKey(name)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table + " DROP INDEX " + name);
        } catch (SQLException failure) {
            if (indexes(connection, table).containsKey(name)) {
                throw failure;
            }
        }
    }

    private Map<String, List<String>> indexes(Connection connection, String table) throws SQLException {
        // H2 serves getIndexInfo from a mutable metadata cache without taking a snapshot, so an index
        // created by a concurrently starting node surfaces here as a ConcurrentModificationException
        // wrapped in a SQLException. The read is idempotent and ensureIndex already tolerates a racing
        // CREATE INDEX, so retry it a bounded number of times instead of serializing every startup
        // behind one lock — a lock would also turn the concurrent-startup regression test into a
        // tautology. MySQL reads committed metadata and never takes this path.
        SQLException transientFailure = null;
        for (int attempt = 1; attempt <= INDEX_METADATA_ATTEMPTS; attempt++) {
            try {
                return readIndexes(connection, table);
            } catch (SQLException failure) {
                if (!isConcurrentMetadataRead(failure)) {
                    throw failure;
                }
                transientFailure = failure;
            }
        }
        throw transientFailure;
    }

    private Map<String, List<String>> readIndexes(Connection connection, String table) throws SQLException {
        Map<String, List<String>> indexes = new HashMap<>();
        try (ResultSet rows = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, table, false, false)) {
            while (rows.next()) {
                String name = rows.getString("INDEX_NAME");
                String column = rows.getString("COLUMN_NAME");
                if (name != null && column != null) {
                    indexes.computeIfAbsent(name.toLowerCase(Locale.ROOT), ignored -> new ArrayList<>())
                            .add((rows.getBoolean("NON_UNIQUE") ? "index:" : "unique:")
                                    + column.toLowerCase(Locale.ROOT));
                }
            }
        }
        return indexes;
    }

    private static boolean isConcurrentMetadataRead(SQLException failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof ConcurrentModificationException) {
                return true;
            }
            Throwable next = cause.getCause();
            if (next == cause) {
                break;
            }
            cause = next;
        }
        return false;
    }
}
