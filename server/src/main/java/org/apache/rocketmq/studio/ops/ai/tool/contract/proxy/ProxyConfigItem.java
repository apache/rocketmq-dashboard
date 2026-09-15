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
package org.apache.rocketmq.studio.ops.ai.tool.contract.proxy;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.rocketmq.studio.cluster.proxy.ProxyTopologyVO;

/**
 * Read-only Proxy configuration/reachability snapshot (decision 15). Maps a
 * {@link ProxyTopologyVO} probe result; {@code connections}/{@code version} are reserved for a
 * future proxy read endpoint and stay absent until then.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProxyConfigItem(
        String addr,
        String status,
        Integer connections,
        Integer grpcPort,
        Integer remotingPort,
        boolean grpcReachable,
        boolean remotingReachable,
        String version) {

    public static ProxyConfigItem from(ProxyTopologyVO topology) {
        return new ProxyConfigItem(
                topology.getProxyAddr(),
                topology.getStatus(),
                null,
                topology.getGrpcPort(),
                topology.getRemotingPort(),
                topology.isGrpcReachable(),
                topology.isRemotingReachable(),
                null);
    }
}
