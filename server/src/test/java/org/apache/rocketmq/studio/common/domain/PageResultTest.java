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
package org.apache.rocketmq.studio.common.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageResultTest {

    @Test
    void ofShouldNormalizeNullItemsToAnEmptyList() {
        PageResult<String> result = PageResult.of(null, 0, 1, 20);

        assertThat(result.getItems()).isNotNull().isEmpty();
    }

    @Test
    void ofShouldIsolateItemsFromTheSourceList() {
        List<String> source = new ArrayList<>(List.of("first"));

        PageResult<String> result = PageResult.of(source, 1, 1, 20);
        source.add("later");

        assertThat(result.getItems()).containsExactly("first");
        assertThatThrownBy(() -> result.getItems().add("mutated"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void emptyFactoryBuildsAnEmptyPageWithTheRequestedWindowTest() {
        PageResult<String> result = PageResult.empty(3, 50);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isZero();
        assertThat(result.getPage()).isEqualTo(3);
        assertThat(result.getSize()).isEqualTo(50);
    }

    @Test
    void ofCarriesTheWindowAndKeepsItemOrderTest() {
        PageResult<String> result = PageResult.of(List.of("first", "second"), 42L, 2, 10);

        assertThat(result.getItems()).containsExactly("first", "second");
        assertThat(result.getTotal()).isEqualTo(42L);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(10);
    }
}
