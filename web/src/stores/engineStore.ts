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

import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type AgentEngine = 'claude-code' | 'qoder' | 'http';

interface EngineState {
  engine: AgentEngine;
  setEngine: (engine: AgentEngine) => void;
}

// Per-user agent engine preference, kept in the browser (not in global settings).
export const useEngineStore = create<EngineState>()(
  persist(
    (set) => ({
      engine: 'claude-code',
      setEngine: (engine) => set({ engine }),
    }),
    {
      name: 'rocketmq-studio-agent-engine',
      // Bumped to reset stale persisted engines so Claude Code is the default selection.
      // v2 (2026-10-20): a persisted `qoder` made every run fail with PROVIDER_ERROR whenever the
      // CLI had no login state in the server image, and the failure read as a bare 「失败」 — the
      // preference is not worth that trap, so stale values fall back to the engine that works.
      version: 2,
      migrate: (persisted) => ({ ...(persisted as EngineState), engine: 'claude-code' }),
    },
  ),
);
