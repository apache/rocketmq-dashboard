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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertState;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertStateMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"studio.auth.login-required=false"})
class RmqAlertStateMapperIntegrationTest {
    private static final long RULE_ID_BASE = 2749000L;

    @Autowired
    private RmqAlertStateMapper mapper;

    @Test
    void acknowledgeFiringShouldAcceptReminderTimesOfTheCurrentEpisodeTest() {
        long ruleId = RULE_ID_BASE + 1;
        LocalDateTime firedAt = LocalDateTime.of(2026, 9, 1, 12, 0);
        insertFiringState(ruleId, firedAt);
        try {
            LocalDateTime reminderTime = firedAt.plusMinutes(30);

            int updated = mapper.acknowledgeFiring(ruleId, "fingerprint", reminderTime,
                    LocalDateTime.now());

            assertThat(updated).isEqualTo(1);
            assertThat(mapper.selectList(new QueryWrapper<RmqAlertState>()
                    .eq("rule_id", ruleId)).stream().map(RmqAlertState::getStatus))
                    .containsExactly("ACKED");
        } finally {
            cleanup(ruleId);
        }
    }

    @Test
    void acknowledgeFiringShouldRejectEventsOlderThanTheCurrentEpisodeTest() {
        long ruleId = RULE_ID_BASE + 2;
        LocalDateTime firedAt = LocalDateTime.of(2026, 9, 1, 12, 0);
        insertFiringState(ruleId, firedAt);
        try {
            LocalDateTime staleEpisodeEventTime = firedAt.minusHours(1);

            int updated = mapper.acknowledgeFiring(ruleId, "fingerprint", staleEpisodeEventTime,
                    LocalDateTime.now());

            assertThat(updated).isEqualTo(0);
            assertThat(mapper.selectList(new QueryWrapper<RmqAlertState>()
                    .eq("rule_id", ruleId)).stream().map(RmqAlertState::getStatus))
                    .containsExactly("FIRING");
        } finally {
            cleanup(ruleId);
        }
    }

    private void insertFiringState(long ruleId, LocalDateTime firedAt) {
        cleanup(ruleId);
        RmqAlertState state = new RmqAlertState();
        state.setRuleId(ruleId);
        state.setFingerprint("fingerprint");
        state.setStatus("FIRING");
        state.setConsecutiveHits(3);
        state.setCurrentValue(90.0);
        state.setFiredAt(firedAt);
        state.setVersion(0);
        mapper.insert(state);
    }

    private void cleanup(long ruleId) {
        mapper.delete(new QueryWrapper<RmqAlertState>().eq("rule_id", ruleId));
    }
}
