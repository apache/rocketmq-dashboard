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

import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import client from '../api/client';
import { USE_MOCK } from '../config';

// ─── Types ──────────────────────────────────────────────────────
// Mirrors org.apache.rocketmq.dashboard.model.ClusterCapability served by
// GET /api/architecture/capabilities. Drives capability-based UI rendering
// (menus / pages are shown or hidden depending on cluster features).
export interface ClusterCapability {
  namespaceSupported: boolean;
  liteTopicSupported: boolean;
  popConsumeSupported: boolean;
  aclV2Supported: boolean;
  grpcClientSupported: boolean;
  architectureVersion: string;
}

export const DEFAULT_CAPABILITY: ClusterCapability = {
  namespaceSupported: false,
  liteTopicSupported: false,
  popConsumeSupported: false,
  aclV2Supported: false,
  grpcClientSupported: false,
  architectureVersion: 'unknown',
};

// Mock mode presents a full-featured 5.0 cluster so every page is reachable.
const MOCK_CAPABILITY: ClusterCapability = {
  namespaceSupported: true,
  liteTopicSupported: true,
  popConsumeSupported: true,
  aclV2Supported: true,
  grpcClientSupported: true,
  architectureVersion: '5.0',
};

interface CapabilityContextValue {
  capability: ClusterCapability;
  loading: boolean;
  refresh: () => Promise<void>;
}

const CapabilityContext = createContext<CapabilityContextValue>({
  // In mock mode components may render without a provider (e.g. unit tests);
  // fall back to the full-featured capability so pages stay reachable.
  capability: USE_MOCK ? MOCK_CAPABILITY : DEFAULT_CAPABILITY,
  loading: false,
  refresh: async () => {},
});

/** Unwraps both raw and {status, data, errMsg} wrapped response bodies. */
function unwrapCapability(body: unknown): ClusterCapability {
  if (body && typeof body === 'object') {
    const record = body as Record<string, unknown>;
    const payload =
      'data' in record && record.data && typeof record.data === 'object' ? record.data : record;
    return { ...DEFAULT_CAPABILITY, ...(payload as Partial<ClusterCapability>) };
  }
  return DEFAULT_CAPABILITY;
}

export function CapabilityProvider({ children }: { children: ReactNode }) {
  const [capability, setCapability] = useState<ClusterCapability>(
    USE_MOCK ? MOCK_CAPABILITY : DEFAULT_CAPABILITY,
  );
  const [loading, setLoading] = useState(!USE_MOCK);

  // All setState calls happen after the await, so the mount effect below
  // never sets state synchronously (react-hooks/set-state-in-effect).
  const refresh = useCallback(async () => {
    if (USE_MOCK) return;
    setLoading(true);
    try {
      const res = await client.get('/architecture/capabilities');
      setCapability(unwrapCapability(res.data));
    } catch {
      // Keep the last known capability; pages degrade gracefully.
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // Initial loading state already starts as true outside mock mode, so no
    // synchronous setState is needed here; all updates occur after the await.
    if (USE_MOCK) return;
    let cancelled = false;
    (async () => {
      try {
        const res = await client.get('/architecture/capabilities');
        if (!cancelled) setCapability(unwrapCapability(res.data));
      } catch {
        // Keep the last known capability; pages degrade gracefully.
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <CapabilityContext.Provider value={{ capability, loading, refresh }}>
      {children}
    </CapabilityContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useCapability() {
  return useContext(CapabilityContext);
}
