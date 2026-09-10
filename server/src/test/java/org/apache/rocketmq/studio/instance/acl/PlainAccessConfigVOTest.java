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
package org.apache.rocketmq.studio.instance.acl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PlainAccessConfigVO}: the plain-access view must never leak its
 * access/secret keys through {@code toString} while still comparing equal on them.
 */
class PlainAccessConfigVOTest {

    @Test
    void toStringRedactsTheCredentialKeys() {
        PlainAccessConfigVO config = PlainAccessConfigVO.builder()
                .accessKey("rocketmq-access-key")
                .secretKey("rocketmq-secret-key")
                .whiteRemoteAddress("10.0.0.0/8")
                .admin(true)
                .defaultTopicPerm("DENY")
                .topicPerms(List.of("order-*=PUB"))
                .build();

        String value = config.toString();

        assertThat(value).contains("whiteRemoteAddress=10.0.0.0/8").contains("defaultTopicPerm=DENY");
        assertThat(value).doesNotContain("accessKey").doesNotContain("secretKey");
        assertThat(value).doesNotContain("rocketmq-access-key").doesNotContain("rocketmq-secret-key");
    }

    @Test
    void dataEqualityCoversTheCredentialKeys() {
        PlainAccessConfigVO first = PlainAccessConfigVO.builder()
                .accessKey("ak-1").secretKey("sk-1").build();
        PlainAccessConfigVO same = PlainAccessConfigVO.builder()
                .accessKey("ak-1").secretKey("sk-1").build();
        PlainAccessConfigVO rotated = PlainAccessConfigVO.builder()
                .accessKey("ak-1").secretKey("sk-2").build();

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(rotated);
    }
}
