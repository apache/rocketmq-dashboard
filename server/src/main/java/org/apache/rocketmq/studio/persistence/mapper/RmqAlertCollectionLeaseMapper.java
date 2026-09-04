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
import org.apache.rocketmq.studio.persistence.entity.RmqAlertCollectionLease;

import java.time.LocalDateTime;

public interface RmqAlertCollectionLeaseMapper extends BaseMapper<RmqAlertCollectionLease> {
    @Update("UPDATE rmq_alert_collection_lease SET holder_id = #{holderId}, expires_at = #{expiresAt}, "
            + "gmt_modified = #{now} WHERE lease_name = #{leaseName} "
            + "AND (expires_at <= #{now} OR holder_id = #{holderId})")
    int acquire(@Param("leaseName") String leaseName, @Param("holderId") String holderId,
            @Param("now") LocalDateTime now, @Param("expiresAt") LocalDateTime expiresAt);

    @Update("UPDATE rmq_alert_collection_lease SET expires_at = #{expiresAt}, gmt_modified = #{now} "
            + "WHERE lease_name = #{leaseName} AND holder_id = #{holderId} AND expires_at > #{now}")
    int renew(@Param("leaseName") String leaseName, @Param("holderId") String holderId,
            @Param("now") LocalDateTime now, @Param("expiresAt") LocalDateTime expiresAt);
}
