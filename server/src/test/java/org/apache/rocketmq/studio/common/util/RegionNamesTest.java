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

package org.apache.rocketmq.studio.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegionNamesTest {

    private RegionNames regionNames;

    @BeforeEach
    void setUp() {
        regionNames = new RegionNames();
        regionNames.load();
    }

    @Test
    void resolveShouldReturnBundledDisplayNameTest() {
        assertThat(regionNames.resolve("cn-hangzhou")).isEqualTo("\u534e\u4e1c1\uff08\u676d\u5dde\uff09");
        assertThat(regionNames.resolve("cn-shanghai-cloudspe"))
                .isEqualTo("\u4e0a\u6d77\u4e91\u9884\u53d1\u6f14\u7ec3\u73af\u5883");
        assertThat(regionNames.resolve("cn-zhengzhou-jva"))
                .isEqualTo("\u90d1\u5dde\uff08\u8054\u901a\u5408\u8425\uff09");
        assertThat(regionNames.resolve("ap-southeast-8"))
                .isEqualTo("\u9a6c\u6765\u897f\u4e9a\uff08\u67d4\u4f5b\u5dde\uff09");
        assertThat(regionNames.resolve("cn-qingdao-acdr-ut-1"))
                .isEqualTo("\u9752\u5c9b\u6d77\u5c14\u4e13\u5c5e\u533a\u57df");
        assertThat(regionNames.resolve("cn-wulanchabu-gic-1"))
                .isEqualTo("\u534e\u53176\uff08\u4e4c\u5170\u5bdf\u5e03\uff09\u901a\u7528\u884c\u4e1a\u4e91");
    }

    @Test
    void resolveShouldFallBackToRawIdForUnknownRegionsTest() {
        assertThat(regionNames.resolve("mars-north-1")).isEqualTo("mars-north-1");
        assertThat(regionNames.resolve(" cn-hangzhou ")).isEqualTo("\u534e\u4e1c1\uff08\u676d\u5dde\uff09");
    }

    @Test
    void resolveShouldPassThroughBlankValuesTest() {
        assertThat(regionNames.resolve(null)).isNull();
        assertThat(regionNames.resolve("")).isEmpty();
    }
}
