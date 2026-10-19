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

import { describe, expect, it } from 'vitest';
import { localizeDeliveryError } from './deliveryResult';

const t = (key: string) => `key:${key}`;

describe('localizeDeliveryError', () => {
  it('localizes the SMTP and recipient failures the outbox records itself', () => {
    // Both strings come from NotificationOutboxService and are what an operator sees whenever the
    // email channel is not usable; the helper used to leave them as raw English.
    expect(localizeDeliveryError('SMTP is not configured', t)).toBe(
      'key:deliveries.errSmtpNotConfigured',
    );
    expect(localizeDeliveryError('No valid email recipients', t)).toBe(
      'key:deliveries.errInvalidEmailRecipients',
    );
  });

  it('keeps an unmapped failure unchanged', () => {
    expect(localizeDeliveryError('Connection refused', t)).toBe('Connection refused');
    expect(localizeDeliveryError('', t)).toBe('');
    expect(localizeDeliveryError(null, t)).toBe('');
  });
});
