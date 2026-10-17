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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the batch-delete answer of {@link BatchDeleteResultVO}: the instances page reports
 * "deleted N" from the counter and lists the survivors from {@code failed}, so the counter and
 * the survivor list must round-trip untouched — and the no-args path leaves {@code failed}
 * null, which every consumer has to be ready for.
 */
class BatchDeleteResultVOTest {

    @Test
    void theCounterAndTheSurvivorsRoundTrip() throws Exception {
        BatchDeleteResultVO result = BatchDeleteResultVO.builder()
                .deleted(3)
                .failed(List.of("instance-a", "instance-b"))
                .build();

        String json = new ObjectMapper().writeValueAsString(result);
        BatchDeleteResultVO back = new ObjectMapper().readValue(json, BatchDeleteResultVO.class);

        assertThat(back.getDeleted()).isEqualTo(3);
        assertThat(back.getFailed()).containsExactly("instance-a", "instance-b");
        assertThat(back).isEqualTo(result);
    }

    @Test
    void theNoArgsPathLeavesTheSurvivorListNull() {
        BatchDeleteResultVO result = new BatchDeleteResultVO();

        assertThat(result.getDeleted()).isZero();
        assertThat(result.getFailed()).isNull();

        result.setDeleted(1);
        result.setFailed(List.of("instance-z"));
        assertThat(result).isEqualTo(BatchDeleteResultVO.builder()
                .deleted(1)
                .failed(List.of("instance-z"))
                .build());
    }
}
