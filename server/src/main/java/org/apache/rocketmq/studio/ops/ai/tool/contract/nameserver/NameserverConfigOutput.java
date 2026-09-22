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
package org.apache.rocketmq.studio.ops.ai.tool.contract.nameserver;

import java.util.List;

/**
 * Output of {@code rmq.nameserver.config}. {@code items} covers only the endpoints that answered;
 * {@code resultMayBeTruncated} is true when some NameServer endpoints could not be read and
 * {@code unreachableEndpoints} names them, so the caller can weigh the partial coverage instead of
 * mistaking it for the full cluster configuration.
 */
public record NameserverConfigOutput(
        List<NameserverConfigItem> items,
        boolean resultMayBeTruncated,
        List<String> unreachableEndpoints) {

    public static NameserverConfigOutput of(List<NameserverConfigItem> items, List<String> unreachableEndpoints) {
        return new NameserverConfigOutput(
                List.copyOf(items),
                !unreachableEndpoints.isEmpty(),
                List.copyOf(unreachableEndpoints));
    }
}
