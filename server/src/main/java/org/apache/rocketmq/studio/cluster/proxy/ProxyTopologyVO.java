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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Health/topology view of a single 5.0 Proxy node. {@code proxyAddr} is the
 * registered {@code host:grpcPort} address; the remoting port is derived with the
 * 5.0 default layout ({@code grpcPort - 1}) when it looks like the well-known
 * {@code 8080/8081} pair, otherwise the probe only reports the gRPC side.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxyTopologyVO {

    /** Registered proxy address in {@code host:grpcPort} form. */
    private String proxyAddr;

    /** UP (gRPC reachable) / PARTIAL (gRPC down, remoting up) / DOWN. */
    private String status;

    /** gRPC port of the proxy. */
    private int grpcPort;

    /** Derived remoting port ({@code grpcPort - 1}), {@code null} when not derivable. */
    private Integer remotingPort;

    /** Whether the gRPC port accepted a TCP connection. */
    private boolean grpcReachable;

    /** Whether the derived remoting port accepted a TCP connection (null-safe). */
    private boolean remotingReachable;

    /** Round-trip latency of the successful probe in milliseconds, -1 when unreachable. */
    private long latencyMs;
}
