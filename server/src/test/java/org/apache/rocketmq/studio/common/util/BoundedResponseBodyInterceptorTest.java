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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;

class BoundedResponseBodyInterceptorTest {
    private static final int LIMIT = 32;
    private final BoundedResponseBodyInterceptor interceptor = new BoundedResponseBodyInterceptor(LIMIT);

    @ParameterizedTest
    @ValueSource(ints = {200, 400, 500})
    void rejectsOversizedBodiesWithoutContentLengthAfterOneExtraByteTest(int status) throws Exception {
        TrackingStream stream = new TrackingStream(LIMIT + 100);
        MockClientHttpResponse response = spy(new MockClientHttpResponse(stream, HttpStatus.valueOf(status)));

        assertThatThrownBy(() -> intercept(response))
                .isInstanceOf(BoundedResponseBodyInterceptor.ResponseTooLargeException.class)
                .hasMessage("HTTP response body exceeds 32 bytes");

        assertThat(stream.bytesRead).isEqualTo(LIMIT + 1);
        assertThat(stream.closed).isTrue();
        verify(response, atLeastOnce()).close();
    }

    @Test
    void rejectsAdvertisedOversizeBeforeReadingTheBodyTest() {
        TrackingStream stream = new TrackingStream(LIMIT + 100);
        MockClientHttpResponse response = spy(new MockClientHttpResponse(stream, HttpStatus.BAD_GATEWAY));
        response.getHeaders().setContentLength(LIMIT + 1);

        assertThatThrownBy(() -> intercept(response))
                .isInstanceOf(BoundedResponseBodyInterceptor.ResponseTooLargeException.class);

        assertThat(stream.bytesRead).isZero();
        assertThat(stream.closed).isTrue();
        verify(response, atLeastOnce()).close();
    }

    @Test
    void doesNotTrustAnUnderstatedContentLengthTest() {
        TrackingStream stream = new TrackingStream(LIMIT + 100);
        MockClientHttpResponse response = spy(new MockClientHttpResponse(stream, HttpStatus.OK));
        response.getHeaders().setContentLength(1);

        assertThatThrownBy(() -> intercept(response))
                .isInstanceOf(BoundedResponseBodyInterceptor.ResponseTooLargeException.class);

        assertThat(stream.bytesRead).isEqualTo(LIMIT + 1);
        assertThat(stream.closed).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, LIMIT - 1, LIMIT})
    void acceptsBodiesAtOrBelowTheBoundaryAndPreservesMetadataTest(int length) throws Exception {
        TrackingStream stream = new TrackingStream(length);
        MockClientHttpResponse response = spy(new MockClientHttpResponse(stream, HttpStatus.UNPROCESSABLE_ENTITY));
        response.getHeaders().add("X-Upstream", "preserved");

        ClientHttpResponse bounded = intercept(response);

        byte[] bytes = bounded.getBody().readAllBytes();
        assertThat(bytes).hasSize(length);
        if (length > 0) {
            assertThat(bytes).containsOnly((byte) 'x');
        }
        assertThat(bounded.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(bounded.getStatusText()).isEqualTo(response.getStatusText());
        assertThat(bounded.getHeaders().getFirst("X-Upstream")).isEqualTo("preserved");
        assertThat(stream.bytesRead).isEqualTo(length);
        assertThat(stream.closed).isTrue();
        bounded.close();
        verify(response, atLeastOnce()).close();
    }

    @Test
    void propagatesReadTimeoutAndClosesTheResponseTest() {
        SocketTimeoutException timeout = new SocketTimeoutException("read timed out");
        InputStream stream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw timeout;
            }
        };
        MockClientHttpResponse response = spy(new MockClientHttpResponse(stream, HttpStatus.OK));

        assertThatThrownBy(() -> intercept(response)).isSameAs(timeout);
        verify(response, atLeastOnce()).close();
    }

    private ClientHttpResponse intercept(ClientHttpResponse response) throws IOException {
        return interceptor.intercept(mock(org.springframework.http.HttpRequest.class), new byte[0],
                (request, body) -> response);
    }

    /** Emits a few bytes at a time without allocating the entire advertised body. */
    private static final class TrackingStream extends InputStream {
        private final int length;
        private int bytesRead;
        private boolean closed;

        private TrackingStream(int length) {
            this.length = length;
        }

        @Override
        public int read() {
            if (bytesRead == length) {
                return -1;
            }
            bytesRead++;
            return 'x';
        }

        @Override
        public int read(byte[] buffer, int offset, int count) {
            if (count == 0) {
                return 0;
            }
            if (bytesRead == length) {
                return -1;
            }
            int read = Math.min(Math.min(count, 3), length - bytesRead);
            Arrays.fill(buffer, offset, offset + read, (byte) 'x');
            bytesRead += read;
            return read;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
