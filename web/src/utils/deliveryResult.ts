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

type Translator = (key: string, params?: Record<string, string | number>) => string;

const WEBHOOK_PATTERN = /^No configured (\S+) webhook$/;
const UNSUPPORTED_PATTERN = /^Unsupported notification channel: (.+)$/;
const TEST_FAILED_PATTERN = /^Test notification failed: (.*)$/;

/**
 * 投递失败原因由后端以英文异常文案落库（NotificationOutboxService），界面按当前语言映射常见几类；
 * 未收录的文案原样展示，保证不丢信息（用户约定 2026-10-16：告警投递结果不得裸显英文错误串）。
 */
export const localizeDeliveryError = (raw: string | null | undefined, t: Translator): string => {
  if (!raw) return '';
  const trimmed = raw.trim();
  if (trimmed === 'No configured email recipients') return t('deliveries.errNoEmailRecipients');
  const webhook = WEBHOOK_PATTERN.exec(trimmed);
  if (webhook) return t('deliveries.errNoWebhook', { channel: webhook[1] });
  const unsupported = UNSUPPORTED_PATTERN.exec(trimmed);
  if (unsupported) return t('deliveries.errUnsupportedChannel', { channel: unsupported[1] });
  const testFailed = TEST_FAILED_PATTERN.exec(trimmed);
  if (testFailed) return t('deliveries.errTestFailed', { detail: testFailed[1] });
  return trimmed;
};
