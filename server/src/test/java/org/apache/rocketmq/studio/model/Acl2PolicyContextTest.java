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
package org.apache.rocketmq.studio.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Acl2PolicyContext}: the inter-layer policy context must never leak
 * its access/secret keys through {@code toString} while still comparing equal on them.
 */
class Acl2PolicyContextTest {

    @Test
    void toStringRedactsTheCredentialKeys() {
        Acl2PolicyContext context = new Acl2PolicyContext();
        context.setAccessKey("rocketmq-access-key");
        context.setSecretKey("rocketmq-secret-key");
        context.setPolicyName("orders-policy");
        context.setBoundType("USER");

        String value = context.toString();

        assertThat(value).contains("policyName=orders-policy").contains("boundType=USER");
        assertThat(value).doesNotContain("accessKey").doesNotContain("secretKey");
        assertThat(value).doesNotContain("rocketmq-access-key").doesNotContain("rocketmq-secret-key");
    }

    @Test
    void dataEqualityCoversTheCredentialKeys() {
        Acl2PolicyContext first = new Acl2PolicyContext();
        first.setAccessKey("ak-1");
        first.setSecretKey("sk-1");
        first.setPolicyName("orders-policy");

        Acl2PolicyContext same = new Acl2PolicyContext();
        same.setAccessKey("ak-1");
        same.setSecretKey("sk-1");
        same.setPolicyName("orders-policy");

        Acl2PolicyContext rotated = new Acl2PolicyContext();
        rotated.setAccessKey("ak-1");
        rotated.setSecretKey("sk-2");
        rotated.setPolicyName("orders-policy");

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(rotated);
    }
}
