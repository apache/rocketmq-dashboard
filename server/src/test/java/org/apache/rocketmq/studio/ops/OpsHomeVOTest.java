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
package org.apache.rocketmq.studio.ops;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpsHomeVOTest {


    @Test
    void builderShouldCarryConfigurationState() {
        OpsHomeVO vo = OpsHomeVO.builder()
                .configurationAvailable(true)
                .namesvrAddrList(List.of("10.0.0.1:9876"))
                .useVIPChannel(true)
                .useTLS(false)
                .currentNamesrv("10.0.0.1:9876")
                .build();

        assertThat(vo.isConfigurationAvailable()).isTrue();
        assertThat(vo.getNamesvrAddrList()).containsExactly("10.0.0.1:9876");
        assertThat(vo.isUseVIPChannel()).isTrue();
        assertThat(vo.isUseTLS()).isFalse();
        assertThat(vo.getCurrentNamesrv()).isEqualTo("10.0.0.1:9876");
    }
}
