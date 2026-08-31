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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Default {@link ProxyHealthProbe} backed by a plain TCP connect. A connect succeeds as
 * soon as the remote end accepts the handshake, so this also works for proxies that only
 * listen on gRPC without exposing an HTTP health endpoint.
 *
 * <p>The whole probe (name resolution + connect) is bounded by {@code timeoutMillis}. For IP
 * literals the resolution is instant; for hostnames it may block on DNS, which the socket
 * connect timeout does not cover, so a slow DNS degrades the probe to unreachable instead of
 * holding a probe thread past its share of the topology budget.
 */
@Slf4j
@Component
public class SocketProxyHealthProbe implements ProxyHealthProbe {

    @Override
    public ProbeResult probe(String host, int port, int timeoutMillis) {
        long start = System.nanoTime();
        Socket socket = null;
        try {
            socket = new Socket();
            InetSocketAddress address = resolveAddress(host, port, start, timeoutMillis);
            if (address == null) {
                // Name resolution did not finish within the probe budget.
                return ProbeResult.unreachable();
            }
            long elapsedMillis = millisSince(start);
            int remainingMillis = Math.max(1, timeoutMillis - (int) Math.min(elapsedMillis, Integer.MAX_VALUE));
            socket.connect(address, remainingMillis);
            return ProbeResult.reachable(millisSince(start));
        } catch (IOException exception) {
            return ProbeResult.unreachable();
        } finally {
            closeQuietly(socket);
        }
    }

    /**
     * Resolves {@code host:port} to a socket address within the remaining probe budget.
     *
     * @return the resolved address, or {@code null} when a hostname's DNS lookup does not finish
     *         within the remaining budget
     */
    InetSocketAddress resolveAddress(String host, int port, long startNanos, int timeoutMillis) {
        if (isIpLiteral(host)) {
            return new InetSocketAddress(host, port);
        }
        CompletableFuture<InetSocketAddress> resolution =
                CompletableFuture.supplyAsync(() -> doResolve(host, port));
        try {
            long remainingMillis = timeoutMillis - millisSince(startNanos);
            return resolution.get(Math.max(1, remainingMillis), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            resolution.cancel(true);
            return null;
        }
    }

    /** Package-private seam so tests can simulate slow or failing name resolution. */
    InetSocketAddress doResolve(String host, int port) {
        return new InetSocketAddress(host, port);
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static boolean isIpLiteral(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            return true; // bracketed IPv6 literal
        }
        int dots = 0;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c == '.') {
                dots++;
            } else if (c < '0' || c > '9') {
                return false;
            }
        }
        return dots == 3;
    }

    private static void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }
}
