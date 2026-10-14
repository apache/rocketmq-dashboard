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
import translations from '../../../i18n/translations';
import {
  deliveryChannels,
  formatDeliveryChannel,
  formatDeliveryStatus,
} from '../alertDeliveryPresentation';

const resolve = (lang: 'zh' | 'en') => (key: string) => translations[key]?.[lang] ?? key;

describe('alert delivery presentation', () => {
  it('names every outbox status and channel in both locales', () => {
    const statuses = ['PENDING', 'SENDING', 'DELIVERED', 'RETRY_WAIT', 'FAILED'];

    expect(statuses.map((status) => formatDeliveryStatus(status, resolve('zh')))).toEqual([
      '待投递',
      '投递中',
      '已投递',
      '等待重试',
      '投递失败',
    ]);
    expect(statuses.map((status) => formatDeliveryStatus(status, resolve('en')))).toEqual([
      'Pending',
      'Sending',
      'Delivered',
      'Retry pending',
      'Failed',
    ]);
    expect(
      deliveryChannels.map((channel) => formatDeliveryChannel(channel, resolve('zh'))),
    ).toEqual(['钉钉', '邮件', '短信']);
    expect(
      deliveryChannels.map((channel) => formatDeliveryChannel(channel, resolve('en'))),
    ).toEqual(['DingTalk', 'Email', 'SMS']);
  });

  it('keeps a code that has no label visible instead of hiding it', () => {
    expect(formatDeliveryStatus('READY', resolve('zh'))).toBe('READY');
    expect(formatDeliveryChannel('teams', resolve('zh'))).toBe('teams');
  });
});
