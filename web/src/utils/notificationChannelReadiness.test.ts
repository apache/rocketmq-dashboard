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

import { describe, expect, it, vi } from 'vitest';
import type { GeneralSettings } from '../api/settings';
import {
  buildNotificationReadiness,
  testReadyNotificationChannels,
} from './notificationChannelReadiness';

const settings = (overrides: Partial<GeneralSettings> = {}): GeneralSettings => ({
  theme: 'system',
  compact: false,
  desktopNotify: false,
  notifySound: false,
  sessionTimeout: 30,
  requireLogin: true,
  llmProvider: 'openai',
  apiKeyConfigured: false,
  model: 'gpt-5',
  baseUrl: 'https://api.example.test/v1',
  ...overrides,
});

describe('notification channel readiness', () => {
  it('always builds one row for each supported channel', () => {
    const report = buildNotificationReadiness(settings());

    expect(report.results.map((row) => row.channel)).toEqual(['dingtalk', 'email', 'sms']);
    expect(report.summary).toEqual({
      channels: 3,
      configured: 0,
      unconfigured: 3,
      tested: 0,
      succeeded: 0,
      failed: 0,
      recipients: 0,
      averageLatencyMs: null,
    });
  });

  it('uses server configuration flags when masked webhook values are returned', () => {
    const report = buildNotificationReadiness(
      settings({
        dingtalkWebhook: '******',
        dingtalkWebhookConfigured: true,
        smsWebhook: '******',
        smsWebhookConfigured: true,
      }),
    );

    expect(report.results[0]).toMatchObject({
      channel: 'dingtalk',
      status: 'READY',
      destination: 'configured endpoint',
    });
    expect(report.results[2]).toMatchObject({
      channel: 'sms',
      status: 'READY',
      destination: 'configured endpoint',
    });
    expect(report.summary.configured).toBe(2);
  });

  it('shows only webhook host names and never exposes paths, queries, or tokens', () => {
    const secret = 'access_token=top-secret-value';
    const report = buildNotificationReadiness(
      settings({
        dingtalkWebhook: `https://oapi.dingtalk.com/robot/send?${secret}`,
        smsWebhook: 'https://sms.example.test/private/send?key=another-secret',
      }),
    );

    expect(report.results[0].destination).toBe('oapi.dingtalk.com');
    expect(report.results[2].destination).toBe('sms.example.test');
    expect(JSON.stringify(report)).not.toContain(secret);
    expect(JSON.stringify(report)).not.toContain('another-secret');
    expect(JSON.stringify(report)).not.toContain('/private/send');
  });

  it('normalizes comma, semicolon, and line-separated email recipients', () => {
    const report = buildNotificationReadiness(
      settings({
        emailRecipients: 'ops@example.test; oncall@example.test,\n owner@example.test ,, ',
      }),
    );

    expect(report.results[1]).toMatchObject({
      channel: 'email',
      status: 'READY',
      destination: '3 recipients',
      recipientCount: 3,
    });
    expect(report.summary.recipients).toBe(3);
  });

  it('reports DingTalk signing readiness without returning the signing secret', () => {
    const report = buildNotificationReadiness(
      settings({
        dingtalkWebhookConfigured: true,
        dingtalkSigningSecret: 'SEC-private',
        dingtalkSigningSecretConfigured: true,
      }),
    );

    expect(report.results[0].signingEnabled).toBe(true);
    expect(JSON.stringify(report)).not.toContain('SEC-private');
  });

  it('does not call the API for channels that are not configured', async () => {
    const test = vi.fn().mockResolvedValue(undefined);
    const report = buildNotificationReadiness(settings({ emailRecipients: 'ops@example.test' }));

    const tested = await testReadyNotificationChannels(report, test);

    expect(test).toHaveBeenCalledTimes(1);
    expect(test).toHaveBeenCalledWith('email');
    expect(tested.results[0].status).toBe('NOT_CONFIGURED');
    expect(tested.results[1].status).toBe('SUCCESS');
    expect(tested.results[2].status).toBe('NOT_CONFIGURED');
    expect(tested.summary.tested).toBe(1);
  });

  it('continues testing remaining channels after an individual failure', async () => {
    const test = vi.fn(async (channel: string) => {
      if (channel === 'dingtalk') throw new Error('gateway rejected the request');
    });
    const report = buildNotificationReadiness(
      settings({
        dingtalkWebhookConfigured: true,
        emailRecipients: 'ops@example.test',
        smsWebhookConfigured: true,
      }),
    );

    const tested = await testReadyNotificationChannels(report, test, 1);

    expect(test).toHaveBeenCalledTimes(3);
    expect(tested.results.map((row) => row.status)).toEqual(['FAILED', 'SUCCESS', 'SUCCESS']);
    expect(tested.results[0].message).toBe('gateway rejected the request');
    expect(tested.summary).toMatchObject({ tested: 3, succeeded: 2, failed: 1 });
  });

  it('uses a safe fallback message for non-Error failures', async () => {
    const test = vi.fn().mockRejectedValue({ code: 500 });
    const report = buildNotificationReadiness(settings({ dingtalkWebhookConfigured: true }));

    const tested = await testReadyNotificationChannels(report, test);

    expect(tested.results[0]).toMatchObject({ status: 'FAILED', message: 'Test failed' });
  });

  it('uses the injected clock and calculates average latency across tested channels', async () => {
    const clockValues = [100, 125, 200, 275];
    const clock = vi.fn(() => clockValues.shift() ?? 275);
    const report = buildNotificationReadiness(
      settings({ dingtalkWebhookConfigured: true, emailRecipients: 'ops@example.test' }),
    );

    const tested = await testReadyNotificationChannels(
      report,
      vi.fn().mockResolvedValue(undefined),
      1,
      clock,
    );

    expect(tested.results[0].latencyMs).toBe(25);
    expect(tested.results[1].latencyMs).toBe(75);
    expect(tested.summary.averageLatencyMs).toBe(50);
  });

  it('clamps a backwards clock to zero milliseconds', async () => {
    const clockValues = [100, 90];
    const report = buildNotificationReadiness(settings({ smsWebhookConfigured: true }));

    const tested = await testReadyNotificationChannels(
      report,
      vi.fn().mockResolvedValue(undefined),
      1,
      () => clockValues.shift() ?? 90,
    );

    expect(tested.results[2].latencyMs).toBe(0);
    expect(tested.summary.averageLatencyMs).toBe(0);
  });

  it('limits simultaneous notification tests to the requested concurrency', async () => {
    let active = 0;
    let maximum = 0;
    const releases: Array<() => void> = [];
    const test = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          active += 1;
          maximum = Math.max(maximum, active);
          releases.push(() => {
            active -= 1;
            resolve();
          });
        }),
    );
    const report = buildNotificationReadiness(
      settings({
        dingtalkWebhookConfigured: true,
        emailRecipients: 'ops@example.test',
        smsWebhookConfigured: true,
      }),
    );

    const promise = testReadyNotificationChannels(report, test, 2);
    await vi.waitFor(() => expect(test).toHaveBeenCalledTimes(2));
    expect(maximum).toBe(2);
    releases.shift()?.();
    await vi.waitFor(() => expect(test).toHaveBeenCalledTimes(3));
    expect(maximum).toBe(2);
    releases.splice(0).forEach((release) => release());

    await expect(promise).resolves.toMatchObject({
      summary: { tested: 3, succeeded: 3, failed: 0 },
    });
  });

  it('coerces invalid concurrency values to a single worker', async () => {
    const calls: string[] = [];
    const report = buildNotificationReadiness(
      settings({ dingtalkWebhookConfigured: true, emailRecipients: 'ops@example.test' }),
    );

    const tested = await testReadyNotificationChannels(
      report,
      async (channel) => {
        calls.push(channel);
      },
      Number.NaN,
    );

    expect(calls).toEqual(['dingtalk', 'email']);
    expect(tested.summary.succeeded).toBe(2);
  });

  it('returns a new report without mutating the readiness snapshot', async () => {
    const report = buildNotificationReadiness(settings({ emailRecipients: 'ops@example.test' }));

    const tested = await testReadyNotificationChannels(
      report,
      vi.fn().mockResolvedValue(undefined),
    );

    expect(tested).not.toBe(report);
    expect(tested.results).not.toBe(report.results);
    expect(report.results[1]).toMatchObject({ status: 'READY', latencyMs: null });
    expect(tested.results[1].status).toBe('SUCCESS');
  });
});
