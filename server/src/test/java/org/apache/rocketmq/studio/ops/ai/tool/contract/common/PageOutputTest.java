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

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link PageOutput}: the tool-side pagination envelope. The mapper must apply to every
 * item and the page metadata must survive the translation untouched.
 */
class PageOutputTest {

    @Test
    void mapsThePageMetadataAndEveryItemThroughTheMapper() {
        PageResult<String> page = PageResult.of(List.of("a", "b", "c"), 42, 2, 3);

        PageOutput<Integer> output = PageOutput.from(page, String::length);

        assertThat(output.page()).isEqualTo(2);
        assertThat(output.pageSize()).isEqualTo(3);
        assertThat(output.total()).isEqualTo(42);
        assertThat(output.items()).containsExactly(1, 1, 1);
    }

    @Test
    void anEmptyPageMapsToAnEmptyItemList() {
        PageResult<String> page = PageResult.of(List.of(), 0, 1, 20);

        PageOutput<String> output = PageOutput.from(page, s -> s);

        assertThat(output.items()).isEmpty();
        assertThat(output.total()).isZero();
    }
}
