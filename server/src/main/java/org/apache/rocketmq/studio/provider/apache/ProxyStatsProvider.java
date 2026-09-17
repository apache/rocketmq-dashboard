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

package org.apache.rocketmq.studio.provider.apache;

import org.apache.rocketmq.common.message.MessageQueue;

/**
 * Source of consumer lag stats when the broker cannot report them directly.
 *
 * <p>RocketMQ 5.0 gRPC consumers can report offsets as {@code -1} through the broker channel, which
 * is indistinguishable from "zero lag" once clamped. Implementations query the Proxy for the
 * authoritative queue offsets; unavailable transports return the unknown sentinel so callers never
 * fabricate a zero.
 */
public interface ProxyStatsProvider {

    /**
     * @param instanceId selected Studio instance, or {@code null} for the default NameServer
     * @param consumerGroup consumer group whose offset is requested
     * @param queue queue routing context required by the Proxy remoting protocol
     * @return the authoritative consumer lag, or {@code -1} when it cannot be determined
     */
    long queryLag(String instanceId, String consumerGroup, MessageQueue queue);
}
