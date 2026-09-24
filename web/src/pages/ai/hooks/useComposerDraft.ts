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

import { useCallback, useState } from 'react';

/**
 * The composer draft: the ONE piece of AI-page state that stays on the client.
 *
 * Everything else moved to the server — history, transcripts, run status — and the `sessionStorage`
 * store that used to hold them is gone. Unsent text is different: it never reached anybody, so there
 * is nothing to persist it to, and losing it because the operator clicked over to a topic list and
 * back is the kind of small cruelty a console should not inflict. Hence one `useState` plus one
 * `sessionStorage` key, deliberately NOT a Zustand store: there is no second consumer, no
 * cross-component subscription and no partitioning by data mode, which is what made the old store
 * 308 lines.
 *
 * `sessionStorage` (per tab) rather than `localStorage`: a draft is scratch for the conversation you
 * are having now, and a tab reopened next week should start clean.
 */

export const COMPOSER_DRAFT_STORAGE_KEY = 'rocketmq-studio-ai-composer-draft';

/**
 * `AiMessageDTO.message` is `@NotBlank @Size(max = 8192)`. A restored draft is clamped to that bound
 * so a hand-edited or corrupt storage value cannot produce a message the server will reject; text
 * being typed is left alone and the server stays the authority on the send path.
 */
export const MAX_COMPOSER_DRAFT_CHARS = 8192;

function readDraft(): string {
  try {
    const stored = sessionStorage.getItem(COMPOSER_DRAFT_STORAGE_KEY);
    return typeof stored === 'string' ? stored.slice(0, MAX_COMPOSER_DRAFT_CHARS) : '';
  } catch {
    return '';
  }
}

function writeDraft(value: string): void {
  try {
    if (value) sessionStorage.setItem(COMPOSER_DRAFT_STORAGE_KEY, value);
    else sessionStorage.removeItem(COMPOSER_DRAFT_STORAGE_KEY);
  } catch {
    // Storage refused (private mode, quota): the draft survives in memory for this mount and is
    // simply not carried across navigation. Not worth surfacing to the operator.
  }
}

export type ComposerDraft = [value: string, setValue: (value: string) => void];

/** Draft text plus its persistence. `setValue('')` clears both the state and the storage key. */
export function useComposerDraft(): ComposerDraft {
  const [value, setValueState] = useState<string>(readDraft);

  const setValue = useCallback((next: string) => {
    setValueState(next);
    writeDraft(next);
  }, []);

  return [value, setValue];
}
