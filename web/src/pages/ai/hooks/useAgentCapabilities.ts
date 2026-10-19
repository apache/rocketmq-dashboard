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
 * The capabilities the server probes on startup, as the AI page needs them.
 *
 * Every flag defaults to `true` so nothing flashes on page load before the probe answers, and a
 * FAILED probe keeps them `true` as well: an endpoint that cannot be reached says nothing about the
 * binary or the switch behind it, and claiming "unavailable" off a transient network blip would be
 * a lie the operator cannot distinguish from the real thing. Only an explicit `false` is reported.
 *
 * `enabled` is false in mock mode, where the AI page does not inspect the runtime at all.
 */
export interface AiAgentCapabilities {
  rmqctlAvailable: boolean;
  claudeAvailable: boolean;
  qoderAvailable: boolean;
  mcpEnabled: boolean;
  l3ToolsAllowed: boolean;
}

const UNKNOWN_CAPABILITIES: AiAgentCapabilities = {
  rmqctlAvailable: true,
  claudeAvailable: true,
  qoderAvailable: true,
  mcpEnabled: true,
  l3ToolsAllowed: true,
};

export function useAgentCapabilities(enabled: boolean): AiAgentCapabilities {
  const [capabilities, setCapabilities] = useState<AiAgentCapabilities>(UNKNOWN_CAPABILITIES);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    // A new probe makes the previous explicit answer stale. Until this request answers, follow the
    // documented unknown-state fallback instead of continuing to claim a capability is missing.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setCapabilities(UNKNOWN_CAPABILITIES);
    getAgentCapabilities()
      .then((probed) => {
        if (!cancelled) {
          setCapabilities({
            rmqctlAvailable: probed.rmqctlAvailable,
            claudeAvailable: probed.claudeAvailable,
            qoderAvailable: probed.qoderAvailable,
            mcpEnabled: probed.mcpEnabled,
            l3ToolsAllowed: probed.l3ToolsAllowed,
          });
        }
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [enabled]);

  return capabilities;
}
