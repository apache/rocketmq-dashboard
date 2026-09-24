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

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Global ownership with database row locks; a committed claim is never released by remote operation failures. */
@Component
@DependsOn("resourceOwnershipSchemaMigration")
@RequiredArgsConstructor
public class ResourceOwnershipGuard {
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;
    private final InstanceResolver instanceResolver;

    @Getter
    @RequiredArgsConstructor
    public enum Kind {
        TOPIC("rmq_instance_topic"), GROUP("rmq_instance_group");
        private final String table;
    }

    public record Resource(Kind kind, String name) { }

    public record Ownership(long id, String name, String instanceId, String clusterId, String topicType) { }

    public InstanceVO requireInstance(String identifier) {
        String id = requireText(identifier, "instanceId");
        return instanceResolver.findByIdentifier(id)
                .orElseThrow(() -> new BusinessException(404, "Instance not found: " + id));
    }

    public static String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(400, field + " is required");
        }
        return value.trim();
    }

    /** Derived topics borrow the group ownership only; no regular topic registration is created. */
    public Resource topicResource(String name) {
        String topic = requireText(name, "topicName");
        for (String prefix : List.of("%DLQ%", "%RETRY%")) {
            if (topic.startsWith(prefix)) {
                String group = topic.substring(prefix.length());
                // POP retry topic names have ambiguous separators, so the owning group cannot be guessed.
                if (!StringUtils.hasText(group) || group.contains("+")) {
                    throw new BusinessException(409, "Cannot determine the owning group of derived topic: " + topic);
                }
                return new Resource(Kind.GROUP, group);
            }
        }
        return new Resource(Kind.TOPIC, topic);
    }

    public Ownership check(InstanceVO instance, Resource resource, boolean required) {
        return checkOwner(instance, resource, read(resource, false), required);
    }

    /** Cloud resources are not registered globally yet; reject writes instead of treating OpenAPI visibility as ownership. */
    public void requireSupportedProvider(InstanceVO instance) {
        if (instance.getVendor() != null && instance.getVendor() != InstanceVendor.APACHE) {
            throw new BusinessException(501, "Cloud resources are not registered for global ownership yet; resource writes are not allowed");
        }
    }

    /** Direct cloud provider entries also check global conflicts first, then reject unregistered writes. */
    public void checkProviderWrite(String identifier, String topic, String group) {
        InstanceVO instance = requireInstance(identifier);
        if (topic == null && group == null) {
            throw new BusinessException(400, "resourceName is required");
        }
        if (topic != null) {
            check(instance, topicResource(topic), false);
        }
        if (group != null) {
            check(instance, new Resource(Kind.GROUP, requireText(group, "groupName")), false);
        }
        throw new BusinessException(501, "Cloud resources are not registered for global ownership yet; resource writes are not allowed");
    }

    public <T> T write(InstanceVO instance, Resource resource, String cluster, boolean create,
                       String topicType, Supplier<T> action) {
        requireSupportedProvider(instance);
        // Commit the claim first; later RPC, DB updates or process crashes must not let other instances take over.
        try {
            newTransaction().executeWithoutResult(status -> {
                lockInstance(instance);
                Ownership existing = checkOwner(instance, resource, read(resource, true), !create);
                if (existing == null) {
                    if (resource.kind() == Kind.TOPIC) {
                        jdbc.update("INSERT INTO rmq_instance_topic (name, instance_id, cluster_id, status, topic_type)"
                                        + " VALUES (?, ?, ?, 'PENDING', ?)", resource.name(), instance.getName(), cluster,
                                topicType == null ? "NORMAL" : topicType);
                    } else {
                        jdbc.update("INSERT INTO rmq_instance_group (name, instance_id, cluster_id, status)"
                                        + " VALUES (?, ?, ?, 'PENDING')", resource.name(), instance.getName(), cluster);
                    }
                } else {
                    requireCluster(existing, cluster);
                    requireTopicType(existing, topicType);
                }
            });
        } catch (DuplicateKeyException conflict) {
            // The unique-key race happens before any RPC; never continue remote writes after a unique-key failure.
            check(instance, resource, true);
            throw new BusinessException(409, "Resource is being registered concurrently, please retry: " + resource.name());
        } catch (PessimisticLockingFailureException conflict) {
            throw new BusinessException(409, "Resource registration lock conflict, please retry: " + resource.name());
        }
        return newTransaction().execute(status -> {
            lockInstance(instance);
            Ownership existing = checkOwner(instance, resource, read(resource, true), true);
            requireCluster(existing, cluster);
            requireTopicType(existing, topicType);
            return action.get();
        });
    }

    /** Multi-resource operations lock in a fixed order so the guard can be reused within one transaction. */
    public <T> T withOwned(InstanceVO instance, List<Resource> resources, Supplier<T> action) {
        requireSupportedProvider(instance);
        return new TransactionTemplate(transactionManager).execute(status -> {
            lockInstance(instance);
            resources.stream().distinct().sorted(Comparator.comparing((Resource r) -> r.kind().name())
                    .thenComparing(Resource::name)).forEach(resource ->
                    checkOwner(instance, resource, read(resource, true), true));
            return action.get();
        });
    }

    /** Must lock the instance inside the deletion transaction first, then re-read claims in all states. */
    public void lockForInstanceDeletion(Long id) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Instance deletion must run inside a database transaction");
        }
        List<String> names = jdbc.query("SELECT name FROM rmq_instance WHERE id = ?",
                (rs, row) -> rs.getString(1), id);
        if (names.isEmpty()) {
            throw new BusinessException(404, "Instance not found: " + id);
        }
        lockIdentity(names.getFirst());
        if (jdbc.query("SELECT id FROM rmq_instance WHERE id = ? FOR UPDATE",
                (rs, row) -> rs.getLong(1), id).isEmpty()) {
            throw new BusinessException(404, "Instance not found: " + id);
        }
        requireUnoccupiedInstance(names.getFirst());
    }

    /** Connection changes are serialized with resource writes; occupied instances cannot point to another endpoint. */
    public void lockForInstanceUpdate(InstanceVO existing, InstanceVO updated) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Instance update must run inside a database transaction");
        }
        lockInstance(existing);
        if (!Objects.equals(existing.getEndpoint(), updated.getEndpoint())
                || !Objects.equals(existing.getType(), updated.getType())) {
            lockForInstanceDeletion(existing.getId());
        }
    }

    /** Registration and virtual instance claiming share one mutex row; gap locks on absent rows are not reliable. */
    public <T> T withInstanceRegistration(String name, Supplier<T> registration) {
        String identifier = requireText(name, "instanceId");
        return new TransactionTemplate(transactionManager).execute(status -> {
            lockIdentity(identifier);
            requireUnoccupiedInstance(identifier);
            return registration.get();
        });
    }

    private void requireUnoccupiedInstance(String name) {
        for (Kind kind : Kind.values()) {
            if (!jdbc.query("SELECT id FROM " + kind.table
                            + " WHERE instance_id = ? OR ((instance_id IS NULL OR TRIM(instance_id) = '')"
                            + " AND cluster_id = ?) FOR UPDATE", (rs, row) -> rs.getLong(1), name, name).isEmpty()) {
                throw new BusinessException(409, "Instance name still holds resources or unfinished claims; cannot register, delete or change connection: " + name);
            }
        }
    }

    private void lockIdentity(String name) {
        jdbc.update("INSERT INTO rmq_instance_ownership_lock (name) VALUES (?) ON DUPLICATE KEY UPDATE name = name", name);
        jdbc.queryForObject("SELECT id FROM rmq_instance_ownership_lock WHERE name = ? FOR UPDATE", Long.class, name);
    }

    private void lockInstance(InstanceVO instance) {
        lockIdentity(instance.getName());
        if (instance.getId() == null) {
            // A virtual instance must not be silently replaced by a registered instance with the same name.
            if (!jdbc.query("SELECT id FROM rmq_instance WHERE name = ? FOR UPDATE",
                    (rs, row) -> rs.getLong(1), instance.getName()).isEmpty()) {
                throw new BusinessException(409, "Virtual instance is shadowed by a registered instance; please re-confirm ownership");
            }
            return;
        }
        List<Boolean> matches = jdbc.query("SELECT name, endpoint, admin_credential_ref, type, vendor FROM rmq_instance"
                        + " WHERE id = ? FOR UPDATE", (rs, row) -> instance.getName().equals(rs.getString(1))
                        && Objects.equals(instance.getEndpoint(), rs.getString(2))
                        && Objects.equals(instance.getAdminCredentialRef(), rs.getString(3))
                        && Objects.equals(instance.getType() == null ? null : instance.getType().name(), rs.getString(4))
                        && Objects.equals(instance.getVendor() == null ? InstanceVendor.APACHE.name() : instance.getVendor().name(),
                                StringUtils.hasText(rs.getString(5)) ? rs.getString(5) : InstanceVendor.APACHE.name()),
                instance.getId());
        if (matches.isEmpty() || !matches.getFirst()) {
            throw new BusinessException(409, "Instance was deleted or changed; please re-confirm ownership");
        }
    }

    private Ownership read(Resource resource, boolean lock) {
        String type = resource.kind() == Kind.TOPIC ? "topic_type" : "NULL";
        List<Ownership> rows = jdbc.query("SELECT id, name, instance_id, cluster_id, " + type
                        + " FROM " + resource.kind().table + " WHERE name = ?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new Ownership(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5)), resource.name());
        if (rows.size() > 1) {
            throw new BusinessException(409, "Resource has duplicate ownership records and needs manual resolution: " + resource.name());
        }
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Ownership checkOwner(InstanceVO instance, Resource resource, Ownership owner, boolean required) {
        if (owner == null) {
            if (required) {
                throw new BusinessException(404, "Resource ownership is not registered yet: " + resource.name());
            }
            return null;
        }
        boolean legacyVirtual = !StringUtils.hasText(owner.instanceId()) && instance.getId() == null
                && instance.getName().equals(owner.clusterId());
        if (!instance.getName().equals(owner.instanceId()) && !legacyVirtual) {
            throw new BusinessException(409, "Resource " + resource.name() + " belongs to instance: "
                    + (StringUtils.hasText(owner.instanceId()) ? owner.instanceId() : "unknown (legacy record, manual confirmation required)"));
        }
        return owner;
    }

    private void requireCluster(Ownership owner, String cluster) {
        if (!Objects.equals(owner.clusterId(), cluster)) {
            throw new BusinessException(409, "Resource physical cluster does not match the write target: " + owner.name());
        }
    }

    private void requireTopicType(Ownership owner, String requested) {
        if (requested != null && owner.topicType() != null && !requested.equals(owner.topicType())) {
            throw new BusinessException(400, "topic message type is immutable");
        }
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction;
    }
}
