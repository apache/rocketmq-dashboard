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
package org.apache.rocketmq.studio.ops.ai.tool.contract.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the paging defaults of {@link PageRequest}: the tools' shared paging input falls back to
 * page 1 and page size 20 when a model omits them or sends zero or negative values, and passes
 * positive values through untouched.
 */
class PageRequestTest {

    @Test
    void aMissingPageFallsBackToOne() {
        assertThat(new PageRequest(0, 20).page()).isEqualTo(1);
        assertThat(new PageRequest(-3, 20).page()).isEqualTo(1);
    }

    @Test
    void aMissingPageSizeFallsBackToTwenty() {
        assertThat(new PageRequest(1, 0).pageSize()).isEqualTo(20);
        assertThat(new PageRequest(1, -50).pageSize()).isEqualTo(20);
    }

    @Test
    void positiveValuesPassThroughUntouched() {
        PageRequest request = new PageRequest(3, 50);
        assertThat(request.page()).isEqualTo(3);
        assertThat(request.pageSize()).isEqualTo(50);
    }

    @Test
    void bothFallbacksApplyTogether() {
        PageRequest request = new PageRequest(0, 0);
        assertThat(request.page()).isEqualTo(1);
        assertThat(request.pageSize()).isEqualTo(20);
    }
}
