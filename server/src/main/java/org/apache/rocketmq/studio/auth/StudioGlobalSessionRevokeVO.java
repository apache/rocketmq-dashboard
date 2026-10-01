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
package org.apache.rocketmq.studio.auth;

import lombok.Builder;
import lombok.Data;

/**
 * Result of the global session revocation. The operator's own sessions are always spared
 * (an incident responder must not log themselves out mid-response), which is why the count
 * can be lower than the active-session total on the overview.
 */
@Data
@Builder
public class StudioGlobalSessionRevokeVO {

    private int revokedSessionCount;
}
