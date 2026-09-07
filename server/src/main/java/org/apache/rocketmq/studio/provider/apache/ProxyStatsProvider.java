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

/**
 * Source of consumer lag stats when the broker cannot report them directly.
 *
 * <p>RocketMQ 5.0 gRPC consumers report offsets as {@code -1} through the broker channel, which is
 * indistinguishable from "zero lag" once clamped. A real proxy transport would implement this to
 * query lag from the proxy; for now the default {@link NoopProxyStatsProvider} reports the unknown
 * sentinel so the caller can surface {@code -1} instead of a misleading {@code 0}.
 */
public interface ProxyStatsProvider {

    /**
     * @return the authoritative consumer lag, or {@code -1} when it cannot be determined.
     */
    long queryLag();
}
