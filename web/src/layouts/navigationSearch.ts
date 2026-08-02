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

import type { ReactNode } from 'react';

export interface NavigationSearchEntry {
  key: string;
  label: string;
  icon?: ReactNode;
}

function normalizeSearchText(value: string): string {
  return value
    .normalize('NFKC')
    .toLocaleLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .trim();
}

function matchScore(entry: NavigationSearchEntry, query: string, terms: string[]): number | null {
  const label = normalizeSearchText(entry.label);
  const key = normalizeSearchText(entry.key);
  const searchableText = `${label} ${key}`;

  if (!terms.every((term) => searchableText.includes(term))) return null;
  if (label === query) return 0;
  if (key === query) return 1;
  if (label.startsWith(query)) return 2;
  if (key.startsWith(query)) return 3;
  if (label.includes(query)) return 4;
  if (key.includes(query)) return 5;
  return 6;
}

export function filterNavigationEntries(
  entries: NavigationSearchEntry[],
  query: string,
): NavigationSearchEntry[] {
  const normalizedQuery = normalizeSearchText(query);
  if (!normalizedQuery) return entries;
  const terms = normalizedQuery.split(' ');

  return entries
    .map((entry, index) => ({ entry, index, score: matchScore(entry, normalizedQuery, terms) }))
    .filter(
      (match): match is { entry: NavigationSearchEntry; index: number; score: number } =>
        match.score !== null,
    )
    .sort((left, right) => left.score - right.score || left.index - right.index)
    .map(({ entry }) => entry);
}

export function isNavigationSearchShortcut(event: {
  key: string;
  metaKey: boolean;
  ctrlKey: boolean;
  altKey: boolean;
  target?: EventTarget | null;
}): boolean {
  const isEditableTarget =
    event.target instanceof Element &&
    event.target.closest(
      'input, textarea, select, [contenteditable]:not([contenteditable="false"])',
    ) !== null;

  return (
    event.key.toLocaleLowerCase() === 'k' &&
    (event.metaKey || event.ctrlKey) &&
    !event.altKey &&
    !isEditableTarget
  );
}
