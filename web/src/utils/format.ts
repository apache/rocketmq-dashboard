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

const pad = (n: number, width = 2): string => String(n).padStart(width, '0');

/**
 * Format a date string or Date object to 'YYYY-MM-DD HH:mm:ss'.
 */
export function formatDateTime(date: string | Date): string {
  const d = typeof date === 'string' ? new Date(date) : date;
  if (isNaN(d.getTime())) return String(date);
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  );
}

/**
 * Format a date string or Date object to 'YYYY-MM-DD'.
 */
export function formatDate(date: string | Date): string {
  const d = typeof date === 'string' ? new Date(date) : date;
  if (isNaN(d.getTime())) return String(date);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/**
 * Format bytes into human-readable string (1024-based).
 * e.g. 1536 → '1.5 KB', 1048576 → '1 MB'
 */
export function formatBytes(bytes: number, decimals = 1): string {
  if (bytes === 0) return '0 B';
  if (bytes < 0) return `-${formatBytes(-bytes, decimals)}`;

  const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
  const k = 1024;
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  const value = bytes / Math.pow(k, i);
  return `${value.toFixed(decimals)} ${units[i]}`;
}

/**
 * Format a number with thousands separators.
 * e.g. 1234567 → '1,234,567'
 */
export function formatNumber(num: number): string {
  return num.toLocaleString('en-US');
}

/**
 * Format delay seconds into human-readable duration.
 * Supports i18n via the lang parameter.
 * e.g. 82500 → zh: "22小时55分钟", en: "22h 55m"
 */
export function formatDelay(totalSeconds: number, lang: 'zh' | 'en' = 'zh'): string {
  if (totalSeconds <= 0) return lang === 'zh' ? '0秒' : '0s';

  const days = Math.floor(totalSeconds / 86400);
  let remaining = totalSeconds % 86400;
  const hours = Math.floor(remaining / 3600);
  remaining %= 3600;
  const minutes = Math.floor(remaining / 60);
  const seconds = remaining % 60;

  if (lang === 'en') {
    const parts: string[] = [];
    if (days > 0) parts.push(`${days}d`);
    if (hours > 0) parts.push(`${hours}h`);
    if (minutes > 0) parts.push(`${minutes}m`);
    if (seconds > 0 && parts.length < 3) parts.push(`${seconds}s`);
    return parts.length > 0 ? parts.join(' ') : '0s';
  }

  const parts: string[] = [];
  if (days > 0) parts.push(`${days}天`);
  if (hours > 0) parts.push(`${hours}小时`);
  if (minutes > 0) parts.push(`${minutes}分钟`);
  if (seconds > 0 && parts.length < 3) parts.push(`${seconds}秒`);
  return parts.length > 0 ? parts.join('') : '0秒';
}

/**
 * Format a percentage value (0-100) with fixed decimals.
 */
export function formatPercent(value: number, decimals = 1): string {
  return `${value.toFixed(decimals)}%`;
}
