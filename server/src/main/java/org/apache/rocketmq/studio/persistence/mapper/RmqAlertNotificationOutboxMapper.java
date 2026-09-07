/*
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0.
*/
package org.apache.rocketmq.studio.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Update;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertNotificationOutbox;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.rocketmq.studio.ops.alert.NotificationDeliveryPageVO;

public interface RmqAlertNotificationOutboxMapper extends BaseMapper<RmqAlertNotificationOutbox> {
    @Delete("DELETE FROM rmq_alert_notification_outbox WHERE alert_id IN "
            + "(SELECT id FROM rmq_system_alert WHERE acknowledged = 1)")
    int deleteForAcknowledgedAlerts();

    @Delete("DELETE FROM rmq_alert_notification_outbox WHERE id IN ("
            + "SELECT id FROM (SELECT id FROM rmq_alert_notification_outbox WHERE "
            + "(status = 'DELIVERED' AND ((delivered_at IS NOT NULL AND delivered_at < #{cutoff}) "
            + "OR (delivered_at IS NULL AND gmt_modified < #{cutoff}))) "
            + "OR (status = 'FAILED' AND gmt_modified < #{cutoff}) "
            + "ORDER BY id LIMIT #{limit}) expired_terminal_deliveries)")
    int deleteTerminalBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    @Select("SELECT * FROM rmq_alert_notification_outbox WHERE "
            + "(status IN ('PENDING', 'RETRY_WAIT') AND next_attempt_at <= #{now}) "
            + "OR (status = 'SENDING' AND (sending_started_at IS NULL OR sending_started_at <= #{staleBefore})) "
            + "ORDER BY id LIMIT #{limit}")
    List<RmqAlertNotificationOutbox> findDispatchable(@Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore, @Param("limit") int limit);

    @Update("UPDATE rmq_alert_notification_outbox SET status = 'SENDING', sending_started_at = #{claimedAt}, "
            + "claim_token = #{claimToken} "
            + "WHERE id = #{id} AND ((status IN ('PENDING', 'RETRY_WAIT') AND next_attempt_at <= #{now}) "
            + "OR (status = 'SENDING' AND (sending_started_at IS NULL OR sending_started_at <= #{staleBefore})))")
    int claimForDispatch(@Param("id") Long id, @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore, @Param("claimedAt") LocalDateTime claimedAt,
            @Param("claimToken") String claimToken);

    @Update("UPDATE rmq_alert_notification_outbox SET sending_started_at = #{renewedAt} "
            + "WHERE id = #{id} AND status = 'SENDING' AND claim_token = #{claimToken}")
    int renewClaim(@Param("id") Long id, @Param("claimToken") String claimToken,
            @Param("renewedAt") LocalDateTime renewedAt);

    @Select("<script>"
            + "SELECT o.id, o.alert_id AS alertId, o.channel, o.status, o.attempt_count AS attemptCount, "
            + "o.next_attempt_at AS nextAttemptAt, o.last_error AS lastError, o.delivered_at AS deliveredAt, "
            + "o.gmt_create AS createdAt, o.message_content AS messageContent, a.title AS alertTitle, a.domain AS alertDomain, "
            + "a.transition, a.instance_id AS instanceId "
            + "FROM rmq_alert_notification_outbox o "
            + "JOIN rmq_system_alert a ON a.id = o.alert_id "
            + "<where>"
            + "<if test='channel != null and channel != \"\"'> AND o.channel = #{channel}</if>"
            + "<if test='status != null and status != \"\"'> AND o.status = #{status}</if>"
            + "<if test='instanceId != null and instanceId != \"\"'> AND a.instance_id = #{instanceId}</if>"
            + "</where> ORDER BY o.id DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<NotificationDeliveryPageVO> findPage(@Param("channel") String channel, @Param("status") String status,
            @Param("instanceId") String instanceId, @Param("limit") int limit, @Param("offset") long offset);

    @Select("<script>"
            + "SELECT COUNT(*) FROM rmq_alert_notification_outbox o "
            + "JOIN rmq_system_alert a ON a.id = o.alert_id "
            + "<where>"
            + "<if test='channel != null and channel != \"\"'> AND o.channel = #{channel}</if>"
            + "<if test='status != null and status != \"\"'> AND o.status = #{status}</if>"
            + "<if test='instanceId != null and instanceId != \"\"'> AND a.instance_id = #{instanceId}</if>"
            + "</where>"
            + "</script>")
    long countPage(@Param("channel") String channel, @Param("status") String status,
            @Param("instanceId") String instanceId);
}
