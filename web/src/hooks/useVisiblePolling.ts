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
  const pollInFlightRef = useRef<Promise<void> | null>(null);

  useEffect(() => {
    if (!enabled) return;
    // The cleanup clears the timer and the listener, but it cannot cancel a poll already queued as
    // a microtask: disabling live refresh, unmounting or hiding the page between the trigger and
    // that microtask used to start an obsolete request anyway. The queue therefore re-checks the
    // effect's liveness and the visibility right before invoking poll, while a request that has
    // already started keeps holding the shared in-flight guard.
    let active = true;

    const pollWhenVisible = () => {
      if (document.visibilityState !== 'visible' || pollInFlightRef.current) return;

      const inFlight = Promise.resolve()
        .then(() => {
          if (active && document.visibilityState === 'visible') return poll();
        })
        .catch(() => undefined)
        .finally(() => {
          if (pollInFlightRef.current === inFlight) {
            pollInFlightRef.current = null;
          }
        });
      pollInFlightRef.current = inFlight;
    };
    const intervalId = window.setInterval(pollWhenVisible, intervalMs);
    document.addEventListener('visibilitychange', pollWhenVisible);

    return () => {
      active = false;
      window.clearInterval(intervalId);
      document.removeEventListener('visibilitychange', pollWhenVisible);
    };
  }, [enabled, intervalMs, poll]);
}
