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

package org.apache.rocketmq.studio.ops.ai.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Pins {@link McpAuthentication}, the authenticated MCP principal the filter writes and the
 * tool controllers read: both the instance the tools are pinned to and the principal must be
 * real text — a blank one fails loudly at construction instead of haunting requests as an
 * empty principal. {@code ATTRIBUTE} is the cross-component request-attribute key, so it must
 * stay the class name.
 */
class McpAuthenticationTest {

    @Test
    void aPrincipalCarriesItsInstanceAndName() {
        McpAuthentication authentication = new McpAuthentication("instance-1", "user:ops-admin");

        assertThat(authentication.instanceId()).isEqualTo("instance-1");
        assertThat(authentication.principal()).isEqualTo("user:ops-admin");
        assertThat(authentication).isEqualTo(new McpAuthentication("instance-1", "user:ops-admin"));
    }

    @Test
    void aBlankInstanceIsRejectedAtConstruction() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new McpAuthentication("   ", "user:ops-admin"))
                .withMessage("instanceId must not be blank");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new McpAuthentication(null, "user:ops-admin"))
                .withMessage("instanceId must not be blank");
    }

    @Test
    void aBlankPrincipalIsRejectedAtConstruction() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new McpAuthentication("instance-1", "   "))
                .withMessage("principal must not be blank");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new McpAuthentication("instance-1", null))
                .withMessage("principal must not be blank");
    }

    @Test
    void theRequestAttributeKeyIsTheClassName() {
        assertThat(McpAuthentication.ATTRIBUTE)
                .isEqualTo("org.apache.rocketmq.studio.ops.ai.auth.McpAuthentication");
    }
}
