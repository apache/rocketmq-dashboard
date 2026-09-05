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
package org.apache.rocketmq.studio.persistence.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RmqCloudCredential}: the persisted credential row must never leak
 * its access/secret keys through {@code toString}, while still comparing equal on them.
 */
class RmqCloudCredentialTest {

    @Test
    void toStringRedactsTheCredentialKeys() {
        RmqCloudCredential credential = new RmqCloudCredential();
        credential.setId(1L);
        credential.setName("production");
        credential.setVendor("aliyun");
        credential.setAccessKey("cloud-access-key");
        credential.setSecretKey("cloud-secret-key");

        String value = credential.toString();

        assertThat(value).contains("name=production").contains("vendor=aliyun");
        assertThat(value).doesNotContain("accessKey").doesNotContain("secretKey");
        assertThat(value).doesNotContain("cloud-access-key").doesNotContain("cloud-secret-key");
    }

    @Test
    void dataEqualityCoversTheCredentialKeys() {
        RmqCloudCredential first = new RmqCloudCredential();
        first.setId(1L);
        first.setAccessKey("ak-1");
        first.setSecretKey("sk-1");

        RmqCloudCredential same = new RmqCloudCredential();
        same.setId(1L);
        same.setAccessKey("ak-1");
        same.setSecretKey("sk-1");

        RmqCloudCredential rotated = new RmqCloudCredential();
        rotated.setId(1L);
        rotated.setAccessKey("ak-1");
        rotated.setSecretKey("sk-2");

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(rotated);
    }
}
