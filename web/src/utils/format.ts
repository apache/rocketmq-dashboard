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
const safeDecimals = (decimals: number): number =>
  Number.isFinite(decimals) ? Math.min(100, Math.max(0, Math.trunc(decimals))) : 1;

/**
 * Format a date string or Date object to 'YYYY-MM-DD HH:mm:ss'.
 */
export function formatDateTime(date: string | Date | null | undefined): string {
  if (date === null || date === undefined || (typeof date === 'string' && !date.trim())) return '-';
  const d = typeof date === 'string' ? new Date(date) : date;
  if (isNaN(d.getTime())) return '-';
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  );
}

/**
 * Format an epoch-millisecond value or a timestamp string as 'YYYY-MM-DD HH:mm:ss.SSS'.
 *
 * Message store times and trace node timestamps arrive either as epoch milliseconds (Apache
 * brokers) or as formatted strings (cloud providers), so both are accepted. Zero is a real
 * timestamp rather than a missing one, and only an unusable value yields the placeholder - a
 * malformed timestamp has to read as absent instead of rendering as NaN-NaN-NaN.
 */
export function formatTimeMs(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') return '-';
  const timestamp = typeof value === 'string' ? Date.parse(value) : value;
  if (!Number.isFinite(timestamp)) return '-';
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) return '-';
  return `${formatDateTime(date)}.${pad(date.getMilliseconds(), 3)}`;
}

/**
 * Epoch milliseconds of a backend alert timestamp, parsed the way the backend stamps it.
 *
 * Alert APIs serialize UTC `LocalDateTime` values without an offset, so a plain
 * `new Date(value)` would read them in the browser's zone. An explicit offset (`Z` or
 * `±hh:mm`) is honored; unusable input yields `NaN` so callers can treat it as absent.
 */
export function parseAlertTimestamp(date: string | Date | null | undefined): number {
  if (date === null || date === undefined || (typeof date === 'string' && !date.trim())) {
    return Number.NaN;
  }
  const utcDate =
    typeof date === 'string' && !/(?:Z|[+-]\d{2}:?\d{2})$/i.test(date.trim())
      ? new Date(`${date}Z`)
      : new Date(date);
  return utcDate.getTime();
}

/**
 * Format a UTC timestamp for alert events in the viewer's timezone. Alert APIs
 * serialize UTC LocalDateTime values without an offset, so normal Date parsing
 * would incorrectly treat them as browser-local timestamps.
 *
 * The zone name is deliberately not appended: every timestamp on a page renders in the viewer's own
 * zone, so a `GMT+8` suffix repeats the reader's own setting on every row and forces every time
 * column wider.
 */
export function formatUtcDateTime(
  date: string | Date | null | undefined,
  timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone,
): string {
  if (date === null || date === undefined || (typeof date === 'string' && !date.trim())) return '-';
  const utcDate = typeof date === 'string' ? new Date(parseUtcDateTime(date)) : new Date(date);
  if (Number.isNaN(utcDate.getTime())) return '-';

  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(utcDate);
  const value = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value;
  const year = value('year');
  const month = value('month');
  const day = value('day');
  const hour = value('hour');
  const minute = value('minute');
  const second = value('second');
  return year && month && day && hour && minute && second
    ? `${year}-${month}-${day} ${hour}:${minute}:${second}`
    : '-';
}

/**
 * Parse a UTC timestamp serialized without an offset into epoch milliseconds. Same guard as
 * {@link formatUtcDateTime}: a value that already names its zone is parsed as-is, a zone-less
 * one is read as UTC — without the suffix Date would treat it as browser-local instead.
 */
export function parseUtcDateTime(date: string | null | undefined): number {
  if (date === null || date === undefined || !date.trim()) return Number.NaN;
  const trimmed = date.trim();
  const normalized = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(trimmed)
    ? trimmed
    : `${trimmed.replace(' ', 'T')}Z`;
  const parsed = new Date(normalized).getTime();
  return Number.isNaN(parsed) ? Number.NaN : parsed;
}

/**
 * Format a date string or Date object to 'YYYY-MM-DD'.
 */
export function formatDate(date: string | Date | null | undefined): string {
  if (date === null || date === undefined || (typeof date === 'string' && !date.trim())) return '-';
  const d = typeof date === 'string' ? new Date(date) : date;
  if (isNaN(d.getTime())) return '-';
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

type RelativeTimeTranslator = (key: string, params?: Record<string, string | number>) => string;

/**
 * Format a recent timestamp for compact conversation history entries.
 */
export function formatRelativeTime(
  timestamp: number,
  lang: 'zh' | 'en',
  t: RelativeTimeTranslator,
  now = Date.now(),
): string {
  if (!Number.isFinite(timestamp) || !Number.isFinite(now)) return '-';
  const updatedAt = new Date(timestamp);
  const current = new Date(now);
  if (Number.isNaN(updatedAt.getTime()) || Number.isNaN(current.getTime())) return '-';
  if (!timestamp) return t('ai.history.justNow');

  const elapsed = Math.max(0, now - timestamp);
  const minutes = Math.floor(elapsed / 60_000);
  if (minutes < 1) return t('ai.history.justNow');
  if (minutes < 60) return t('ai.history.minutesAgo', { count: minutes });

  const locale = lang === 'zh' ? 'zh-CN' : 'en-US';
  if (updatedAt.toDateString() === current.toDateString()) {
    return new Intl.DateTimeFormat(locale, {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(updatedAt);
  }
  return new Intl.DateTimeFormat(locale, { month: 'short', day: 'numeric' }).format(updatedAt);
}

/**
 * Format a message timestamp for a compact chat bubble footer.
 */
export function formatTimeOfDay(timestamp: number): string {
  if (!Number.isFinite(timestamp)) return '-';
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) return '-';
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/**
 * Format bytes into human-readable string (1024-based).
 * e.g. 1536 → '1.5 KB', 1048576 → '1 MB'
 */
export function formatBytes(bytes: number, decimals = 1): string {
  if (!Number.isFinite(bytes)) return '-';
  if (bytes === 0) return '0 B';
  if (bytes < 0) return `-${formatBytes(-bytes, decimals)}`;

  const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
  const k = 1024;
  const digits = safeDecimals(decimals);
  let i = 0;
  let value = Math.abs(bytes);
  while (value >= k && i < units.length - 1) {
    value /= k;
    i += 1;
  }
  // A byte count has no meaningful fraction, so the base unit renders whole; every scaled unit keeps
  // the requested precision. The promotion loop has to use the same width it renders with, or a
  // value just below a boundary would round up to 1024 B instead of being promoted to 1.0 KB.
  const precision = () => (i === 0 ? 0 : digits);
  while (i < units.length - 1 && Number(value.toFixed(precision())) >= k) {
    value /= k;
    i += 1;
  }
  return `${value.toFixed(precision())} ${units[i]}`;
}

/**
 * Format a number with thousands separators.
 * e.g. 1234567 → '1,234,567'
 */
export function formatNumber(num: number): string {
  return Number.isFinite(num) ? num.toLocaleString('en-US') : '-';
}

/**
 * Format delay seconds into human-readable duration.
 * Supports i18n via the lang parameter.
 * e.g. 82500 → zh: "22小时55分钟", en: "22h 55m"
 */
export function formatDelay(totalSeconds: number, lang: 'zh' | 'en' = 'zh'): string {
  if (!Number.isFinite(totalSeconds)) return '-';
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
  if (!Number.isFinite(value)) return '-';
  return `${value.toFixed(safeDecimals(decimals))}%`;
}

/**
 * Round a percentage to the one decimal the UI shows.
 *
 * Ratios scaled to percentage points carry binary floating point noise — a broker reporting
 * `commitLogDiskRatio=0.29` becomes `28.999999999999996` — and an antd `Progress` reserves room
 * for its label inside the bar, so a label that long squeezes the bar down to zero width.
 */
export function roundPercent(value: number): number {
  return Math.round(value * 10) / 10;
}

/**
 * Convert a stored ratio to its percentage number the way the alert backend renders it.
 *
 * `AlertNotificationTemplate` scales ratio metrics with `BigDecimal.movePointRight(2)`, which
 * moves the decimal point without changing the digits, so 0.29 renders as `29` and 0.1234 as
 * `12.34`. Multiplying by 100 in binary floating point instead renders 0.29 as
 * 28.999999999999996 — eight of the ninety-nine whole percents do not round-trip — and the alert
 * pages used to print, and write back, that residue.
 */
export function ratioToPercent(value: number): number {
  if (!Number.isFinite(value)) return value;
  // `toString()` yields the shortest decimal that round-trips, which is the same representation
  // `BigDecimal.valueOf(double)` starts from. Exponential notation only shows up outside the
  // range a stored ratio can take, and `toLocaleString` falls back to the plain form there.
  const text = /[eE]/.test(value.toString())
    ? value.toLocaleString('en-US', { useGrouping: false, maximumFractionDigits: 20 })
    : value.toString();
  const match = /^(-?)(\d+)(?:\.(\d+))?$/.exec(text);
  if (!match) return value * 100;
  const [, sign = '', integer = '', fraction = ''] = match;
  const digits = integer + fraction;
  const pointIndex = integer.length + 2;
  const scaled =
    pointIndex < digits.length
      ? `${digits.slice(0, pointIndex)}.${digits.slice(pointIndex)}`
      : `${digits}${'0'.repeat(pointIndex - digits.length)}`;
  return Number(`${sign}${scaled}`);
}

/**
 * Trace and consumption records report "no time" as 0. {@link formatTimeMs} deliberately treats
 * zero as a real timestamp because stored values can legitimately be the epoch, so the
 * optional-time call sites map zero to the placeholder themselves.
 */
export function formatOptionalTimeMs(value: number | string | null | undefined): string {
  return value === 0 || value === '0' ? '-' : formatTimeMs(value);
}

/** The server's sentinel for a message body size the vendor API does not report: unknown, not zero. */
export const UNKNOWN_MESSAGE_SIZE = -1;

/**
 * Message records report "no body size" as {@link UNKNOWN_MESSAGE_SIZE}. {@link formatBytes}
 * deliberately formats negatives as real byte counts, so the message-size call sites map the
 * sentinel to the placeholder themselves.
 */
export function formatOptionalBytes(bytes: number): string {
  return bytes < 0 ? '-' : formatBytes(bytes);
}
