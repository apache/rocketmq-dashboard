/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may may not use this file except in compliance with
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

import { describe, it, expect } from 'vitest';
import {
  formatDateTime,
  formatDate,
  formatBytes,
  formatNumber,
  formatDelay,
  formatPercent,
} from '../format';

describe('format utilities', () => {
  describe('formatDateTime', () => {
    it('formats a Date object to YYYY-MM-DD HH:mm:ss', () => {
      const d = new Date(2024, 0, 15, 8, 30, 45); // 2024-01-15 08:30:45
      expect(formatDateTime(d)).toBe('2024-01-15 08:30:45');
    });

    it('formats a date string to YYYY-MM-DD HH:mm:ss', () => {
      expect(formatDateTime('2024-06-01T12:05:03')).toBe('2024-06-01 12:05:03');
    });

    it('pads single-digit month/day/hour/minute/second', () => {
      const d = new Date(2024, 2, 5, 3, 7, 9); // 2024-03-05 03:07:09
      expect(formatDateTime(d)).toBe('2024-03-05 03:07:09');
    });

    it('returns original value for invalid date string', () => {
      expect(formatDateTime('not-a-date')).toBe('not-a-date');
    });
  });

  describe('formatDate', () => {
    it('formats a Date object to YYYY-MM-DD', () => {
      const d = new Date(2024, 5, 1); // 2024-06-01
      expect(formatDate(d)).toBe('2024-06-01');
    });

    it('formats a date string to YYYY-MM-DD', () => {
      expect(formatDate('2024-12-25T10:30:00')).toBe('2024-12-25');
    });

    it('returns original value for invalid date', () => {
      expect(formatDate('invalid')).toBe('invalid');
    });
  });

  describe('formatBytes', () => {
    it('returns "0 B" for zero bytes', () => {
      expect(formatBytes(0)).toBe('0 B');
    });

    it('formats bytes correctly', () => {
      expect(formatBytes(512)).toBe('512.0 B');
    });

    it('formats kilobytes correctly', () => {
      expect(formatBytes(1536)).toBe('1.5 KB');
    });

    it('formats megabytes correctly', () => {
      expect(formatBytes(1048576)).toBe('1.0 MB');
    });

    it('formats gigabytes correctly', () => {
      expect(formatBytes(1073741824)).toBe('1.0 GB');
    });

    it('handles negative bytes', () => {
      expect(formatBytes(-1536)).toBe('-1.5 KB');
    });

    it('respects custom decimals', () => {
      expect(formatBytes(1536, 2)).toBe('1.50 KB');
    });
  });

  describe('formatNumber', () => {
    it('formats numbers with thousands separators', () => {
      expect(formatNumber(1234567)).toBe('1,234,567');
    });

    it('handles small numbers', () => {
      expect(formatNumber(42)).toBe('42');
    });

    it('handles zero', () => {
      expect(formatNumber(0)).toBe('0');
    });
  });

  describe('formatDelay', () => {
    it('returns "0秒" for zero seconds in Chinese', () => {
      expect(formatDelay(0, 'zh')).toBe('0秒');
    });

    it('returns "0s" for zero seconds in English', () => {
      expect(formatDelay(0, 'en')).toBe('0s');
    });

    it('formats hours and minutes in Chinese', () => {
      // 82500s = 22h 55m
      expect(formatDelay(82500, 'zh')).toBe('22小时55分钟');
    });

    it('formats hours and minutes in English', () => {
      expect(formatDelay(82500, 'en')).toBe('22h 55m');
    });

    it('formats days in Chinese', () => {
      // 90000s = 1d 1h
      expect(formatDelay(90000, 'zh')).toBe('1天1小时');
    });

    it('formats days in English', () => {
      expect(formatDelay(90000, 'en')).toBe('1d 1h');
    });

    it('formats seconds only in Chinese', () => {
      expect(formatDelay(45, 'zh')).toBe('45秒');
    });

    it('formats seconds only in English', () => {
      expect(formatDelay(45, 'en')).toBe('45s');
    });
  });

  describe('formatPercent', () => {
    it('formats percentage with default 1 decimal', () => {
      expect(formatPercent(85.5)).toBe('85.5%');
    });

    it('formats percentage with custom decimals', () => {
      expect(formatPercent(85.567, 2)).toBe('85.57%');
    });

    it('formats zero percent', () => {
      expect(formatPercent(0)).toBe('0.0%');
    });

    it('formats 100 percent', () => {
      expect(formatPercent(100)).toBe('100.0%');
    });
  });
});
