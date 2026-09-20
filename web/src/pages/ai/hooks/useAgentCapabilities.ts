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

import { useEffect, useState } from 'react';
import { getAgentCapabilities } from '../../../api/aiConversations';

/**
 * Whether the hosted agent's RocketMQ tool channel (`rmqctl`) is available.
 *
 * Defaults to `true` so the notice does not flash on every page load before the probe answers, and
 * a FAILED probe also keeps it `true`: an endpoint that cannot be reached says nothing about the
 * binary on the server, and claiming "tools unavailable" off a transient network blip would be a
 * lie the operator cannot distinguish from the real thing. Only an explicit `rmqctlAvailable:false`
 * renders the neutral notice.
 *
 * `enabled` is false in mock mode, where the AI page does not inspect the runtime at all.
 */
export function useAgentCapabilities(enabled: boolean): boolean {
  const [rmqctlAvailable, setRmqctlAvailable] = useState(true);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    getAgentCapabilities()
      .then((capabilities) => {
        if (!cancelled) setRmqctlAvailable(capabilities.rmqctlAvailable);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [enabled]);

  return rmqctlAvailable;
}
