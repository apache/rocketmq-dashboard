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

/**
 * TCP reachability probe used by {@link ProxyAddressService#buildTopology()} to decide
 * whether a proxy node is up. Abstracted so tests can simulate reachable/unreachable
 * ports without opening sockets.
 */
public interface ProxyHealthProbe {

    /**
     * Attempts a TCP connection to {@code host:port} within {@code timeoutMillis}.
     *
     * @return the probe outcome with the measured latency (or -1 when unreachable)
     */
    ProbeResult probe(String host, int port, int timeoutMillis);

    /** Outcome of a single probe. */
    record ProbeResult(boolean reachable, long latencyMs) {

        public static ProbeResult unreachable() {
            return new ProbeResult(false, -1L);
        }

        public static ProbeResult reachable(long latencyMs) {
            return new ProbeResult(true, Math.max(0L, latencyMs));
        }
    }
}
