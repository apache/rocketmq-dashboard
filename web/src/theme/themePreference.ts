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

export type ThemeMode = 'light' | 'dark' | 'system';

export const THEME_STORAGE_KEY = 'rocketmq-studio-theme';
export const COMPACT_STORAGE_KEY = 'rocketmq-studio-compact';

export function getStoredThemeMode(): ThemeMode {
  try {
    const stored = localStorage.getItem(THEME_STORAGE_KEY);
    return stored === 'dark' || stored === 'light' || stored === 'system' ? stored : 'system';
  } catch {
    return 'system';
  }
}

export function persistThemeMode(mode: ThemeMode): void {
  try {
    localStorage.setItem(THEME_STORAGE_KEY, mode);
  } catch {
    // The active theme still changes when browser storage is unavailable.
  }
}

export function getSystemDarkMode(): boolean {
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
}

export function getStoredCompact(): boolean {
  try {
    return localStorage.getItem(COMPACT_STORAGE_KEY) === 'true';
  } catch {
    return false;
  }
}

export function persistCompact(compact: boolean): void {
  try {
    localStorage.setItem(COMPACT_STORAGE_KEY, String(compact));
  } catch {
    // Compact mode still toggles when browser storage is unavailable.
  }
}
