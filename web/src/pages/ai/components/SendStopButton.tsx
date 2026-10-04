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

import { ArrowUp, CircleNotch, Stop } from '@phosphor-icons/react';
import { useLang } from '../../../i18n/LangContext';

/**
 * The single send/stop slot of the composer.
 *
 * ONE `<button>` that morphs between three states, always mounted, always the same 32x32 circular
 * box. The page it replaces mounted a SECOND antd `<Button danger>停止生成</Button>` next to the send
 * button while a run streamed, which pushed the rest of the toolbar sideways on every turn — the
 * regression `SendStopButton.test.tsx` guards with "exactly one button in all three states".
 *
 * State comes from the caller (`Composer`) rather than being derived here, using the formula the
 * hub uses:
 *
 * ```ts
 * const generating = isStreaming && !stopRequested;
 * state = stopRequested ? 'stopping' : generating ? 'stop' : 'send';
 * ```
 *
 * `stopping` exists because stopping is a server round-trip, not a local flag: `onStop` POSTs
 * `/api/ai/runs/{runId}/stop` and then KEEPS READING the stream, which is what delivers the terminal
 * `run_status` frame and `done`. Aborting the fetch instead would leave this button spinning forever
 * and the transcript without its 已停止 marker.
 *
 * Every string is translated — the old button carried two hard-coded `停止生成` literals, the only
 * i18n violations on the page.
 */

export type SendStopState = 'send' | 'stop' | 'stopping';

export interface SendStopButtonProps {
  state: SendStopState;
  /**
   * Whether a send would be accepted right now (non-empty draft, provider ready, no run in flight).
   * Only consulted in the `send` state: `stop` is always clickable and `stopping` always disabled.
   */
  canSend: boolean;
  onSend: () => void;
  onStop: () => void;
}

const SendStopButton = ({ state, canSend, onSend, onStop }: SendStopButtonProps) => {
  const { t } = useLang();

  const disabled = state === 'stopping' || (state === 'send' && !canSend);
  const title =
    state === 'send'
      ? t('ai.composer.sendHint')
      : state === 'stop'
        ? t('ai.composer.stopHint')
        : t('ai.composer.stopping');
  const ariaLabel =
    state === 'send' ? t('ai.composer.send') : state === 'stop' ? t('ai.composer.stop') : title;

  return (
    <button
      type="button"
      className="ai-send-button flex items-center justify-center text-white shadow-lg hover:shadow-xl hover:scale-105 transition-all"
      data-state={state}
      data-testid="ai-send-stop-button"
      disabled={disabled}
      title={title}
      aria-label={ariaLabel}
      onClick={() => {
        if (state === 'stop') onStop();
        else if (state === 'send') onSend();
        // `stopping` is disabled, so the click cannot reach this branch.
      }}
    >
      {state === 'send' && <ArrowUp size={17} weight="bold" />}
      {state === 'stop' && <Stop size={14} weight="fill" />}
      {state === 'stopping' && <CircleNotch size={16} weight="bold" className="ai-icon-spin" />}
    </button>
  );
};

export default SendStopButton;
