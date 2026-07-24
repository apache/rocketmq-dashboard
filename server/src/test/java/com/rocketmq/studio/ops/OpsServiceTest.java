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

package com.rocketmq.studio.ops;

import com.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpsServiceTest {

    private final OpsService opsService = new OpsService();

    @Test
    void getHomePageShouldReturnDefaultSettings() {
        OpsHomeVO home = opsService.getHomePage();

        assertThat(home.getNamesvrAddrList()).containsExactly("127.0.0.1:9876");
        assertThat(home.getCurrentNamesrv()).isEqualTo("127.0.0.1:9876");
        assertThat(home.isUseVIPChannel()).isTrue();
        assertThat(home.isUseTLS()).isFalse();
    }

    @Test
    void addAndUpdateNameServerShouldMaintainUniqueAddressList() {
        opsService.addNameServer(" 10.0.0.1:9876 ");
        opsService.addNameServer("10.0.0.1:9876");
        opsService.updateNameServer("10.0.0.2:9876");

        OpsHomeVO home = opsService.getHomePage();
        assertThat(home.getNamesvrAddrList())
                .containsExactly("127.0.0.1:9876", "10.0.0.1:9876", "10.0.0.2:9876");
        assertThat(home.getCurrentNamesrv()).isEqualTo("10.0.0.2:9876");
    }

    @Test
    void togglesShouldUpdateHomePageSettings() {
        opsService.updateVipChannel(false);
        opsService.updateUseTLS(true);

        OpsHomeVO home = opsService.getHomePage();
        assertThat(home.isUseVIPChannel()).isFalse();
        assertThat(home.isUseTLS()).isTrue();
    }

    @Test
    void nameServerOperationsShouldRejectBlankAddress() {
        assertThatThrownBy(() -> opsService.addNameServer(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("namesrvAddr is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> opsService.updateNameServer(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("namesrvAddr is required");
    }
}
