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
package org.apache.rocketmq.studio.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertState;

import java.time.LocalDateTime;

public interface RmqAlertStateMapper extends BaseMapper<RmqAlertState> {
    @Update("UPDATE rmq_alert_state SET status = #{state.status}, consecutive_hits = #{state.consecutiveHits}, "
            + "current_value = #{state.currentValue}, first_pending_at = #{state.firstPendingAt}, "
            + "fired_at = #{state.firedAt}, last_notified_at = #{state.lastNotifiedAt}, resolved_at = #{state.resolvedAt}, "
            + "gmt_modified = #{state.gmtModified}, version = version + 1 "
            + "WHERE id = #{state.id} AND version = #{expectedVersion}")
    int updateIfVersion(@Param("state") RmqAlertState state, @Param("expectedVersion") int expectedVersion);

    @Update("UPDATE rmq_alert_state SET status = 'ACKED', gmt_modified = #{now}, version = version + 1 "
            + "WHERE rule_id = #{ruleId} AND fingerprint = #{fingerprint} AND status = 'FIRING' "
            + "AND fired_at = #{firedAt}")
    int acknowledgeFiring(@Param("ruleId") Long ruleId, @Param("fingerprint") String fingerprint,
            @Param("firedAt") LocalDateTime firedAt,
            @Param("now") LocalDateTime now);
}
