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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link Result}: the REST envelope every endpoint answers with. The 200/success pair is
 * the shape every front-end interceptor branches on, and an error must never carry data (a
 * half-populated error envelope reads as a success that lost its payload).
 */
class ResultTest {

    @Test
    void okCarriesTheDataWithTheStableSuccessEnvelope() {
        Result<String> result = Result.ok("payload");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMessage()).isEqualTo("success");
        assertThat(result.getData()).isEqualTo("payload");
    }

    @Test
    void okWithoutDataStillHasTheSameEnvelope() {
        Result<Void> result = Result.ok();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMessage()).isEqualTo("success");
        assertThat(result.getData()).isNull();
    }

    @Test
    void errorCarriesTheCodeAndMessageAndNeverData() {
        Result<Void> result = Result.error(503, "RocketMQ admin not connected");

        assertThat(result.getCode()).isEqualTo(503);
        assertThat(result.getMessage()).isEqualTo("RocketMQ admin not connected");
        assertThat(result.getData()).isNull();
    }

    @Test
    void theEnvelopeIsConstructedOnlyThroughItsFactories() throws Exception {
        // the constructors are private: no endpoint can build a lopsided envelope
        assertThat(Result.class.getDeclaredConstructors()).allSatisfy(ctor -> {
            assertThat(ctor.canAccess(null) || !java.lang.reflect.Modifier.isPublic(ctor.getModifiers()))
                    .isTrue();
        });
    }
}
