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

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MqResponseCodesTest {

    @Test
    void matchesClientExceptionResponseCodeTest() {
        MQClientException error = new MQClientException(ResponseCode.NO_MESSAGE, "no message");
        assertThat(MqResponseCodes.hasResponseCode(error,
                ResponseCode.TOPIC_NOT_EXIST, ResponseCode.NO_MESSAGE)).isTrue();
    }

    @Test
    void matchesBrokerExceptionResponseCodeTest() {
        MQBrokerException error = new MQBrokerException(ResponseCode.CONSUMER_NOT_ONLINE, "not online");
        assertThat(MqResponseCodes.hasResponseCode(error, ResponseCode.CONSUMER_NOT_ONLINE)).isTrue();
    }

    @Test
    void matchesResponseCodeOnNestedCauseTest() {
        MQClientException cause = new MQClientException(ResponseCode.TOPIC_NOT_EXIST, "no route");
        RuntimeException wrapper = new RuntimeException("wrapper", cause);
        assertThat(MqResponseCodes.hasResponseCode(wrapper, ResponseCode.TOPIC_NOT_EXIST)).isTrue();
    }

    @Test
    void returnsFalseWhenResponseCodeAbsentTest() {
        MQClientException error = new MQClientException(ResponseCode.TOPIC_NOT_EXIST, "no route");
        assertThat(MqResponseCodes.hasResponseCode(error, ResponseCode.NO_MESSAGE)).isFalse();
    }

    @Test
    void returnsFalseForNullExceptionTest() {
        assertThat(MqResponseCodes.hasResponseCode(null, ResponseCode.NO_MESSAGE)).isFalse();
    }

    @Test
    void terminatesOnSelfReferentialCauseTest() {
        Throwable selfCaused = new Throwable("self") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };
        assertThat(MqResponseCodes.hasResponseCode(selfCaused, ResponseCode.NO_MESSAGE)).isFalse();
    }
}
