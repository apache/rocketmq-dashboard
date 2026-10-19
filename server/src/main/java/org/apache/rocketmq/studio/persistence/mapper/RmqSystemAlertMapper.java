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
import org.apache.ibatis.annotations.Select;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertState;
import org.apache.rocketmq.studio.persistence.entity.RmqSystemAlert;

import java.util.List;

public interface RmqSystemAlertMapper extends BaseMapper<RmqSystemAlert> {
    /** Return only the latest metadata per active key, without materializing old event payloads. */
    @Select("""
            <script>
            SELECT alert.rule_id, alert.fingerprint, alert.instance_id, alert.labels_json
            FROM rmq_system_alert alert
            INNER JOIN (
                SELECT id, ROW_NUMBER() OVER (
                    PARTITION BY rule_id, fingerprint ORDER BY time DESC, id DESC
                ) AS latest_rank
                FROM rmq_system_alert
                WHERE domain = #{domain} AND instance_id = #{instanceId}
                AND (
                    <foreach collection="states" item="state" separator=" OR ">
                        (rule_id = #{state.ruleId} AND fingerprint = #{state.fingerprint})
                    </foreach>
                )
            ) ranked ON alert.id = ranked.id
            WHERE ranked.latest_rank = 1
            </script>
            """)
    List<RmqSystemAlert> selectLatestMetadata(@Param("domain") String domain,
            @Param("instanceId") String instanceId, @Param("states") List<RmqAlertState> states);
}
