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
package org.apache.rocketmq.studio.cluster.nameserver;

import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the NameServer row of {@link NameServerVO}: the address and its health status - the
 * minimal pair the cluster view lists (no fabricated metrics; the connections column was
 * removed precisely because this VO has no such source).
 */
class NameServerVOTest {

    @Test
    void theAddressAndStatusRoundTripThroughTheBuilder() {
        NameServerVO vo = NameServerVO.builder()
                .addr("10.0.0.1:9876")
                .status(ClusterStatus.healthy)
                .build();

        assertThat(vo.getAddr()).isEqualTo("10.0.0.1:9876");
        assertThat(vo.getStatus()).isEqualTo(ClusterStatus.healthy);
    }

    @Test
    void aFreshRowCarriesNeither() {
        NameServerVO vo = new NameServerVO();
        assertThat(vo.getAddr()).isNull();
        assertThat(vo.getStatus()).isNull();
    }
}
