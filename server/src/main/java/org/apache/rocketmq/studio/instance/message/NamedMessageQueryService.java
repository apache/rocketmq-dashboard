/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.message;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NamedMessageQueryService {
    private static final int LIMIT = 50;
    private final JdbcTemplate jdbc;
    private final InstanceService instanceService;

    public record Draft(String instanceId, String name, String mode, String topic, String key,
                        String msgId, Long startTime, Long endTime) {
    }

    public record NamedQuery(String id, String instanceId, String name, String mode, String topic,
                             String key, String msgId, Long startTime, Long endTime,
                             long createdAt, long updatedAt, String createdBy) {
    }

    public List<NamedQuery> list(String instanceId) {
        long instance = instanceService.resolveInstanceId(required(instanceId, 128));
        return jdbc.query("SELECT * FROM rmq_named_message_query WHERE instance_id = ? "
                        + "ORDER BY updated_at DESC, id", (rs, row) -> new NamedQuery(
                        rs.getString("id"), instanceId, rs.getString("name"), rs.getString("mode"),
                        rs.getString("topic"), rs.getString("message_key"), rs.getString("msg_id"),
                        rs.getObject("start_time", Long.class), rs.getObject("end_time", Long.class),
                        rs.getLong("created_at"), rs.getLong("updated_at"), rs.getString("created_by")), instance);
    }

    @Transactional
    public void save(Draft draft) {
        String name = required(draft.name(), 80);
        String topic = required(draft.topic(), 1024);
        String mode = required(draft.mode(), 10);
        String key = null;
        String msgId = null;
        Long start = null;
        Long end = null;
        switch (mode) {
            case "key" -> key = required(draft.key(), 1024);
            case "msgid" -> msgId = required(draft.msgId(), 1024);
            case "topic" -> {
                start = draft.startTime();
                end = draft.endTime();
                if (start == null || end == null || start < 0 || end < start || end > 8_640_000_000_000_000L) {
                    throw new BusinessException(400, "\u67e5\u8be2\u65f6\u95f4\u8303\u56f4\u65e0\u6548");
                }
            }
            default -> throw new BusinessException(400, "\u67e5\u8be2\u6a21\u5f0f\u65e0\u6548");
        }
        long instance = lockInstance(draft.instanceId());
        int count = jdbc.queryForList("SELECT id FROM rmq_named_message_query WHERE instance_id = ? FOR UPDATE",
                String.class, instance).size();
        if (count >= LIMIT) {
            throw new BusinessException(409, "\u5f53\u524d\u5b9e\u4f8b\u5df2\u8fbe\u5230 50 \u6761\u547d\u540d\u67e5\u8be2\u4e0a\u9650\uff0c\u8bf7\u5148\u5220\u9664\u4e0d\u518d\u4f7f\u7528\u7684\u67e5\u8be2");
        }
        long now = System.currentTimeMillis();
        try {
            jdbc.update("INSERT INTO rmq_named_message_query "
                            + "(id, instance_id, name, normalized_name, mode, topic, message_key, msg_id, "
                            + "start_time, end_time, created_at, updated_at, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(), instance, name, name.toLowerCase(Locale.ROOT), mode, topic, key, msgId,
                    start, end, now, now, AuthenticatedUserContext.currentUsernameOrSystem());
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(409, "\u5f53\u524d\u5b9e\u4f8b\u5df2\u5b58\u5728\u540c\u540d\u67e5\u8be2");
        }
    }

    @Transactional
    public void rename(String instanceId, String id, String value) {
        String name = required(value, 80);
        long instance = lockInstance(instanceId);
        try {
            requireChanged(jdbc.update("UPDATE rmq_named_message_query SET name = ?, normalized_name = ?, updated_at = ? "
                            + "WHERE instance_id = ? AND id = ?", name, name.toLowerCase(Locale.ROOT),
                    System.currentTimeMillis(), instance, id));
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(409, "\u5f53\u524d\u5b9e\u4f8b\u5df2\u5b58\u5728\u540c\u540d\u67e5\u8be2");
        }
    }

    @Transactional
    public void delete(String instanceId, String id) {
        long instance = lockInstance(instanceId);
        requireChanged(jdbc.update("DELETE FROM rmq_named_message_query WHERE instance_id = ? AND id = ?", instance, id));
    }

    private long lockInstance(String identifier) {
        long instance = instanceService.resolveInstanceId(required(identifier, 128));
        if (jdbc.queryForList("SELECT id FROM rmq_instance WHERE id = ? FOR UPDATE", Long.class, instance).isEmpty()) {
            throw new BusinessException(404, "\u5b9e\u4f8b\u4e0d\u5b58\u5728");
        }
        return instance;
    }

    private static String required(String value, int max) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > max) {
            throw new BusinessException(400, "\u67e5\u8be2\u540d\u79f0\u6216\u6761\u4ef6\u4e3a\u7a7a\u6216\u8d85\u51fa\u957f\u5ea6\u9650\u5236");
        }
        return value.trim();
    }

    private static void requireChanged(int count) {
        if (count == 0) {
            throw new BusinessException(404, "\u547d\u540d\u67e5\u8be2\u4e0d\u5b58\u5728\uff0c\u8bf7\u5237\u65b0\u5217\u8868");
        }
    }
}
