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
package org.apache.rocketmq.studio.instance.topic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the lite topic view of {@link LiteTopicVO}: a topic reference carries exactly its name
 * and namespace - the minimal identity the lite-topic pages operate on.
 */
class LiteTopicVOTest {

    @Test
    void theNameAndNamespaceRoundTrip() {
        LiteTopicVO topic = new LiteTopicVO();
        topic.setName("orders");
        topic.setNamespace("ns-1");

        assertThat(topic.getName()).isEqualTo("orders");
        assertThat(topic.getNamespace()).isEqualTo("ns-1");
    }

    @Test
    void aFreshTopicCarriesNeither() {
        LiteTopicVO topic = new LiteTopicVO();
        assertThat(topic.getName()).isNull();
        assertThat(topic.getNamespace()).isNull();
    }
}
