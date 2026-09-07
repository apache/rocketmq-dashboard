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

interface LocalDateTimeParts {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
  second: number;
}

const LOCAL_DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/;

const parseLocalDateTime = (value: string): LocalDateTimeParts => {
  const match = LOCAL_DATE_TIME.exec(value);
  if (!match) throw new Error(`Invalid local date time: ${value}`);
  const [, year, month, day, hour, minute, second = '0'] = match;
  const parts = {
    year: Number(year),
    month: Number(month),
    day: Number(day),
    hour: Number(hour),
    minute: Number(minute),
    second: Number(second),
  };
  const normalized = new Date(
    Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute, parts.second),
  );
  if (
    normalized.getUTCFullYear() !== parts.year ||
    normalized.getUTCMonth() + 1 !== parts.month ||
    normalized.getUTCDate() !== parts.day ||
    normalized.getUTCHours() !== parts.hour ||
    normalized.getUTCMinutes() !== parts.minute ||
    normalized.getUTCSeconds() !== parts.second
  ) {
    throw new Error(`Invalid local date time: ${value}`);
  }
  return parts;
};

const partsAsUtcMillis = (parts: LocalDateTimeParts) =>
  Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute, parts.second);

const formatterFor = (timeZone: string) =>
  new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  });

const formatParts = (formatter: Intl.DateTimeFormat, timestamp: number): LocalDateTimeParts => {
  const parts = Object.fromEntries(
    formatter
      .formatToParts(new Date(timestamp))
      .filter((part) => part.type !== 'literal')
      .map((part) => [part.type, Number(part.value)]),
  );
  return {
    year: parts.year,
    month: parts.month,
    day: parts.day,
    hour: parts.hour,
    minute: parts.minute,
    second: parts.second,
  };
};

const sameParts = (left: LocalDateTimeParts, right: LocalDateTimeParts) =>
  left.year === right.year &&
  left.month === right.month &&
  left.day === right.day &&
  left.hour === right.hour &&
  left.minute === right.minute &&
  left.second === right.second;

/** Converts a wall-clock date time in an IANA time zone to a UTC ISO timestamp. */
export const zonedLocalDateTimeToUtc = (value: string, timeZone: string): string => {
  const desired = parseLocalDateTime(value);
  const formatter = formatterFor(timeZone);
  const desiredMillis = partsAsUtcMillis(desired);
  let candidate = desiredMillis;

  // Offset changes are discontinuous around DST, so converge using the wall-clock delta.
  for (let attempt = 0; attempt < 4; attempt += 1) {
    const observed = formatParts(formatter, candidate);
    const delta = desiredMillis - partsAsUtcMillis(observed);
    if (delta === 0) return new Date(candidate).toISOString();
    candidate += delta;
  }

  if (!sameParts(formatParts(formatter, candidate), desired)) {
    throw new Error(`Local date time does not exist in ${timeZone}: ${value}`);
  }
  return new Date(candidate).toISOString();
};
