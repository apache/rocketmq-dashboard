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
package org.apache.rocketmq.studio.cluster.proxy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Default {@link ProxyHealthProbe} backed by a plain TCP connect. A connect succeeds as
 * soon as the remote end accepts the handshake, so this also works for proxies that only
 * listen on gRPC without exposing an HTTP health endpoint.
 */
@Slf4j
@Component
public class SocketProxyHealthProbe implements ProxyHealthProbe {

    @Override
    public ProbeResult probe(String host, int port, int timeoutMillis) {
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            return ProbeResult.reachable(latencyMs);
        } catch (IOException exception) {
            return ProbeResult.unreachable();
        }
    }
}
