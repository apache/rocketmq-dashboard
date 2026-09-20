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

/**
 * Client-side generation-speed measurement for one streaming run.
 *
 * The event contract carries no token usage, so the speed shown on the assistant bubble is an
 * estimate computed from the `text_delta` frames: CJK characters count as one token each and
 * every other four characters as one token (the usual BPE ball park), divided by the wall time
 * between the first and the last delta. Tool round-trips therefore slow the reported number
 * down exactly as the user experiences them.
 */

const CJK_TOKEN_PATTERN = /[\u3000-\u9fff\uf900-\ufaff\uff00-\uffef]/g;

/** Rough token count of a delta: one token per CJK character, one per four other characters. */
export function estimateTokens(text: string): number {
  const cjkChars = text.match(CJK_TOKEN_PATTERN)?.length ?? 0;
  return cjkChars + Math.max(0, text.length - cjkChars) / 4;
}

export interface StreamSpeedTracker {
  /** Fold one text delta into the measurement. `now` is injectable for tests. */
  recordDelta: (text: string, now?: number) => void;
  /** Estimated tokens per second, or null while there is nothing meaningful to measure. */
  tokensPerSecond: () => number | null;
  /** Estimated total tokens generated so far. */
  tokens: () => number;
}

/** Never report a speed from fewer than two seconds of streaming: the number is pure noise there. */
const MIN_SAMPLE_MILLIS = 2000;

export function createStreamSpeedTracker(): StreamSpeedTracker {
  let firstDeltaAt: number | null = null;
  let lastDeltaAt: number | null = null;
  let totalTokens = 0;

  return {
    recordDelta(text: string, now: number = Date.now()) {
      if (firstDeltaAt === null) firstDeltaAt = now;
      lastDeltaAt = now;
      totalTokens += estimateTokens(text);
    },
    tokensPerSecond() {
      if (firstDeltaAt === null || lastDeltaAt === null) return null;
      const elapsedMillis = lastDeltaAt - firstDeltaAt;
      if (elapsedMillis < MIN_SAMPLE_MILLIS || totalTokens === 0) return null;
      return totalTokens / (elapsedMillis / 1000);
    },
    tokens() {
      return totalTokens;
    },
  };
}
