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

import { useEffect, useRef } from 'react';

export function useVisiblePolling(
  enabled: boolean,
  intervalMs: number,
  poll: () => void | Promise<void>,
): void {
  // Track the latest callback in a ref so a caller passing an inline (non-memoized) callback
  // does not re-subscribe the interval on every render — re-subscribing resets the timer and
  // polling can effectively never fire under frequent re-renders.
  const pollRef = useRef(poll);
  useEffect(() => {
    pollRef.current = poll;
  });

  useEffect(() => {
    if (!enabled) return;
    // A non-finite or non-positive interval would make setInterval fire as fast as the
    // event loop allows, hammering the poll callback.
    if (!Number.isFinite(intervalMs) || intervalMs <= 0) return;

    let pollInFlight = false;
    const pollWhenVisible = () => {
      if (document.visibilityState !== 'visible' || pollInFlight) return;

      pollInFlight = true;
      void Promise.resolve()
        .then(() => pollRef.current())
        .catch(() => undefined)
        .finally(() => {
          pollInFlight = false;
        });
    };
    const intervalId = window.setInterval(pollWhenVisible, intervalMs);
    document.addEventListener('visibilitychange', pollWhenVisible);

    return () => {
      window.clearInterval(intervalId);
      document.removeEventListener('visibilitychange', pollWhenVisible);
    };
  }, [enabled, intervalMs]);
}
