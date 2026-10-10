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
 * Live-region mount points for the antd toast portals.
 *
 * rc-notification (antd's message and notification engine) ships no ARIA
 * semantics: a toast is inserted into a plain container and removed a few
 * seconds later, so a screen reader never learns that anything happened —
 * every async outcome in the console (exports, saves, deletes, retries,
 * background failures) is silent to assistive technology.
 *
 * The fix is the standard live-region pattern applied to the portal itself:
 * antd's `getContainer` config lets us choose the element the toasts mount
 * into, so we mount them into containers carrying `role="status"` +
 * `aria-live="polite"`. Assistive technology then announces the text of each
 * notice as it is inserted (polite queues instead of interrupting; the
 * removal when a toast expires is not announced, which is exactly the
 * desired behaviour). The containers are created once, before any toast can
 * fire, because a live region that appears in the same update as its content
 * is not reliably announced.
 *
 * Both toast paths are covered: the static `message` / `notification`
 * singletons (via `message.config` / `notification.config`) and the
 * `App.useApp()` instances (via the `message` / `notification` props on
 * antd's `<App>`). See StudioApp for the wiring.
 */

const MESSAGE_REGION_ID = 'toast-message-live-region';
const NOTIFICATION_REGION_ID = 'toast-notification-live-region';

function ensureLiveRegion(id: string): HTMLElement {
  const existing = document.getElementById(id);
  if (existing) {
    return existing;
  }
  const region = document.createElement('div');
  region.id = id;
  // role=status implies aria-live=polite; stating both keeps the intent explicit.
  region.setAttribute('role', 'status');
  region.setAttribute('aria-live', 'polite');
  document.body.appendChild(region);
  return region;
}

/** Mount point for message toasts; antd positions the notices itself. */
export function getMessageLiveRegion(): HTMLElement {
  return ensureLiveRegion(MESSAGE_REGION_ID);
}

/** Mount point for notification toasts; antd positions the notices itself. */
export function getNotificationLiveRegion(): HTMLElement {
  return ensureLiveRegion(NOTIFICATION_REGION_ID);
}
