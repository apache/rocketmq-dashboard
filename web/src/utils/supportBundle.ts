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

import { API_BASE_URL } from '../config';
import { LANGUAGE_STORAGE_KEY } from '../i18n/languagePreference';
import { COMPACT_STORAGE_KEY, THEME_STORAGE_KEY, type ThemeMode } from '../theme/themePreference';
import {
  USER_ADMIN_STORAGE_KEY,
  USER_ID_STORAGE_KEY,
  USER_STORAGE_KEY,
} from '../stores/authStorage';

type Availability = 'available' | 'unavailable';
type KnownBoolean = 'yes' | 'no' | 'unknown';
type DataMode = 'mock' | 'live' | 'unset' | 'invalid' | 'unknown';
type ColorScheme = 'dark' | 'light' | 'unknown';
type StorageKind = 'localStorage' | 'sessionStorage';

export interface SupportBundleProduct {
  name: string;
  version: string;
  buildCommit: string;
  buildTime: string;
  rocketmqVersions: string;
  frontendFramework: string;
  backendFramework: string;
  license: string;
}

export interface StorageProbe {
  status: Availability;
  readable: boolean;
  writable: boolean;
  keyCount: number;
  error?: string;
}

export interface SupportBundlePreferences {
  language: 'zh' | 'en' | 'unknown';
  theme: ThemeMode | 'unknown';
  compactMode: KnownBoolean;
  effectiveDataMode: Exclude<DataMode, 'unset' | 'invalid'>;
  persistedDataMode: DataMode;
  authUserPresent: KnownBoolean;
  authUserIdPresent: KnownBoolean;
  authAdmin: KnownBoolean;
  proxyAddressConfigured: KnownBoolean;
  clusterIdConfigured: KnownBoolean;
  metricsProfileConfigured: KnownBoolean;
  customTraceTopicScopes: number;
}

export interface SupportBundleRuntime {
  apiBaseUrl: string;
  origin: string;
  pathname: string;
  hasQueryString: boolean;
  hasHash: boolean;
  protocol: string;
  online: KnownBoolean;
}

export interface SupportBundleBrowser {
  userAgent: string;
  language: string;
  languages: string[];
  platform: string;
  cookieEnabled: KnownBoolean;
  doNotTrack: string;
  hardwareConcurrency: number | null;
  maxTouchPoints: number | null;
  colorScheme: ColorScheme;
  timezone: string;
  timezoneOffsetMinutes: number;
  viewport: {
    width: number;
    height: number;
    devicePixelRatio: number;
  };
  screen: {
    width: number;
    height: number;
    colorDepth: number | null;
  };
  storage: Record<StorageKind, StorageProbe>;
}

export interface SupportBundleRedaction {
  routeQueryAndHashOmitted: boolean;
  unsafeLocalStorageKeysOmitted: number;
  unknownLocalStorageKeysOmitted: number;
  sessionStorageValuesOmitted: boolean;
  notes: string[];
}

export interface SupportBundle {
  generatedAt: string;
  product: SupportBundleProduct;
  runtime: SupportBundleRuntime;
  browser: SupportBundleBrowser;
  preferences: SupportBundlePreferences;
  redaction: SupportBundleRedaction;
}

export interface SupportBundleSummaryItem {
  label: string;
  value: string;
}

export interface CollectSupportBundleOptions {
  now?: Date;
  windowRef?: Window;
  navigatorRef?: Navigator;
  localStorageRef?: Storage;
  sessionStorageRef?: Storage;
  effectiveMockMode?: boolean;
}

const DATA_MODE_STORAGE_KEY = 'rocketmq-studio-data-mode';
const METRICS_PROFILE_STORAGE_KEY = 'rocketmq-studio.metric-profile';
const TRACE_TOPIC_STORAGE_PREFIX = 'rocketmq-studio-message-trace-topic:';
const PROXY_ADDRESS_STORAGE_KEY = 'proxyAddr';
const CLUSTER_ID_STORAGE_KEY = 'clusterId';
const SENSITIVE_KEY_PATTERN =
  /(token|secret|password|credential|bearer|authorization|apikey|api-key|access[-_]?key)/i;

const SAFE_LOCAL_STORAGE_KEYS = new Set([
  LANGUAGE_STORAGE_KEY,
  THEME_STORAGE_KEY,
  COMPACT_STORAGE_KEY,
  DATA_MODE_STORAGE_KEY,
  USER_STORAGE_KEY,
  USER_ID_STORAGE_KEY,
  USER_ADMIN_STORAGE_KEY,
  PROXY_ADDRESS_STORAGE_KEY,
  CLUSTER_ID_STORAGE_KEY,
  METRICS_PROFILE_STORAGE_KEY,
]);

const truncate = (value: string, maxLength = 240) =>
  value.length > maxLength ? `${value.slice(0, maxLength)}...` : value;

const asKnownBoolean = (value: boolean | undefined | null): KnownBoolean => {
  if (value === true) return 'yes';
  if (value === false) return 'no';
  return 'unknown';
};

const storageError = (error: unknown) =>
  error instanceof Error ? `${error.name}: ${error.message}` : String(error);

function storageFromWindow(windowRef: Window | undefined, kind: StorageKind): Storage | undefined {
  if (!windowRef) return undefined;
  try {
    return windowRef[kind];
  } catch {
    return undefined;
  }
}

function readStorageValue(storage: Storage | undefined, key: string): string | null {
  if (!storage) return null;
  try {
    return storage.getItem(key);
  } catch {
    return null;
  }
}

function storageKeys(storage: Storage | undefined): string[] {
  if (!storage) return [];
  const keys: string[] = [];
  try {
    for (let index = 0; index < storage.length; index += 1) {
      const key = storage.key(index);
      if (key) keys.push(key);
    }
  } catch {
    return [];
  }
  return keys.sort((left, right) => left.localeCompare(right));
}

function probeStorage(storage: Storage | undefined, probeKey: string): StorageProbe {
  if (!storage) {
    return { status: 'unavailable', readable: false, writable: false, keyCount: 0 };
  }

  let readable = false;
  let writable = false;
  let keyCount = 0;
  let error: string | undefined;

  try {
    keyCount = storage.length;
    readable = true;
  } catch (caught) {
    error = storageError(caught);
  }

  try {
    storage.setItem(probeKey, '1');
    storage.removeItem(probeKey);
    writable = true;
    keyCount = storage.length;
  } catch (caught) {
    error = error ?? storageError(caught);
  }

  return {
    status: readable || writable ? 'available' : 'unavailable',
    readable,
    writable,
    keyCount,
    ...(error ? { error: truncate(error) } : {}),
  };
}

function parseLanguage(value: string | null): SupportBundlePreferences['language'] {
  return value === 'zh' || value === 'en' ? value : 'unknown';
}

function parseTheme(value: string | null): SupportBundlePreferences['theme'] {
  return value === 'light' || value === 'dark' || value === 'system' ? value : 'unknown';
}

function parseCompact(value: string | null): KnownBoolean {
  if (value === 'true') return 'yes';
  if (value === 'false') return 'no';
  return 'unknown';
}

function parseDataMode(value: string | null): DataMode {
  if (value == null || value.trim() === '') return 'unset';
  try {
    const parsed = JSON.parse(value) as { state?: { useMock?: unknown } };
    if (parsed?.state?.useMock === true) return 'mock';
    if (parsed?.state?.useMock === false) return 'live';
    return 'invalid';
  } catch {
    return 'invalid';
  }
}

function summarizePreferences(
  storage: Storage | undefined,
  keys: string[],
  effectiveMockMode?: boolean,
): SupportBundlePreferences {
  const persistedDataMode = parseDataMode(readStorageValue(storage, DATA_MODE_STORAGE_KEY));
  const effectiveDataMode =
    effectiveMockMode === true
      ? 'mock'
      : effectiveMockMode === false
        ? 'live'
        : persistedDataMode === 'mock' || persistedDataMode === 'live'
          ? persistedDataMode
          : 'unknown';

  return {
    language: parseLanguage(readStorageValue(storage, LANGUAGE_STORAGE_KEY)),
    theme: parseTheme(readStorageValue(storage, THEME_STORAGE_KEY)),
    compactMode: parseCompact(readStorageValue(storage, COMPACT_STORAGE_KEY)),
    effectiveDataMode,
    persistedDataMode,
    authUserPresent: asKnownBoolean(Boolean(readStorageValue(storage, USER_STORAGE_KEY))),
    authUserIdPresent: asKnownBoolean(Boolean(readStorageValue(storage, USER_ID_STORAGE_KEY))),
    authAdmin: parseCompact(readStorageValue(storage, USER_ADMIN_STORAGE_KEY)),
    proxyAddressConfigured: asKnownBoolean(
      Boolean(readStorageValue(storage, PROXY_ADDRESS_STORAGE_KEY)),
    ),
    clusterIdConfigured: asKnownBoolean(Boolean(readStorageValue(storage, CLUSTER_ID_STORAGE_KEY))),
    metricsProfileConfigured: asKnownBoolean(
      Boolean(readStorageValue(storage, METRICS_PROFILE_STORAGE_KEY)),
    ),
    customTraceTopicScopes: keys.filter((key) => key.startsWith(TRACE_TOPIC_STORAGE_PREFIX)).length,
  };
}

function summarizeRuntime(windowRef: Window | undefined): SupportBundleRuntime {
  const location = windowRef?.location;
  return {
    apiBaseUrl: API_BASE_URL,
    origin: location?.origin ?? 'unknown',
    pathname: location?.pathname ?? 'unknown',
    hasQueryString: Boolean(location?.search),
    hasHash: Boolean(location?.hash),
    protocol: location?.protocol ?? 'unknown',
    online: asKnownBoolean(windowRef?.navigator?.onLine),
  };
}

function detectColorScheme(windowRef: Window | undefined): ColorScheme {
  try {
    if (windowRef?.matchMedia?.('(prefers-color-scheme: dark)').matches) return 'dark';
    if (windowRef?.matchMedia?.('(prefers-color-scheme: light)').matches) return 'light';
  } catch {
    return 'unknown';
  }
  return 'unknown';
}

function summarizeBrowser(
  windowRef: Window | undefined,
  navigatorRef: Navigator | undefined,
  localStorageProbe: StorageProbe,
  sessionStorageProbe: StorageProbe,
): SupportBundleBrowser {
  const nav = navigatorRef ?? windowRef?.navigator;
  const screen = windowRef?.screen;
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'unknown';

  return {
    userAgent: truncate(nav?.userAgent ?? 'unknown'),
    language: nav?.language ?? 'unknown',
    languages: Array.from(nav?.languages ?? []),
    platform: nav?.platform ?? 'unknown',
    cookieEnabled: asKnownBoolean(nav?.cookieEnabled),
    doNotTrack: nav?.doNotTrack ?? 'unknown',
    hardwareConcurrency:
      typeof nav?.hardwareConcurrency === 'number' ? nav.hardwareConcurrency : null,
    maxTouchPoints: typeof nav?.maxTouchPoints === 'number' ? nav.maxTouchPoints : null,
    colorScheme: detectColorScheme(windowRef),
    timezone,
    timezoneOffsetMinutes: new Date().getTimezoneOffset(),
    viewport: {
      width: windowRef?.innerWidth ?? 0,
      height: windowRef?.innerHeight ?? 0,
      devicePixelRatio: windowRef?.devicePixelRatio ?? 1,
    },
    screen: {
      width: screen?.width ?? 0,
      height: screen?.height ?? 0,
      colorDepth: typeof screen?.colorDepth === 'number' ? screen.colorDepth : null,
    },
    storage: {
      localStorage: localStorageProbe,
      sessionStorage: sessionStorageProbe,
    },
  };
}

function summarizeRedaction(keys: string[], runtime: SupportBundleRuntime): SupportBundleRedaction {
  const traceTopicKeys = keys.filter((key) => key.startsWith(TRACE_TOPIC_STORAGE_PREFIX));
  const knownKeys = new Set([...SAFE_LOCAL_STORAGE_KEYS, ...traceTopicKeys]);
  const unsafe = keys.filter((key) => SENSITIVE_KEY_PATTERN.test(key));
  const unknown = keys.filter((key) => !knownKeys.has(key) && !SENSITIVE_KEY_PATTERN.test(key));

  return {
    routeQueryAndHashOmitted: runtime.hasQueryString || runtime.hasHash,
    unsafeLocalStorageKeysOmitted: unsafe.length,
    unknownLocalStorageKeysOmitted: unknown.length,
    sessionStorageValuesOmitted: true,
    notes: [
      'Local storage values are limited to allow-listed UI preferences and presence flags.',
      'Token, password, secret, credential, bearer and authorization-like keys are omitted.',
      'Route query strings, hash fragments and all sessionStorage values are omitted.',
    ],
  };
}

export function collectSupportBundle(
  product: SupportBundleProduct,
  options: CollectSupportBundleOptions = {},
): SupportBundle {
  const windowRef = options.windowRef ?? (typeof window === 'undefined' ? undefined : window);
  const navigatorRef =
    options.navigatorRef ?? (typeof navigator === 'undefined' ? undefined : navigator);
  const localStorage = options.localStorageRef ?? storageFromWindow(windowRef, 'localStorage');
  const sessionStorage =
    options.sessionStorageRef ?? storageFromWindow(windowRef, 'sessionStorage');
  const now = options.now ?? new Date();
  const localKeys = storageKeys(localStorage);
  const runtime = summarizeRuntime(windowRef);

  return {
    generatedAt: now.toISOString(),
    product,
    runtime,
    browser: summarizeBrowser(
      windowRef,
      navigatorRef,
      probeStorage(localStorage, '__rocketmq_studio_support_local_probe__'),
      probeStorage(sessionStorage, '__rocketmq_studio_support_session_probe__'),
    ),
    preferences: summarizePreferences(localStorage, localKeys, options.effectiveMockMode),
    redaction: summarizeRedaction(localKeys, runtime),
  };
}

export function formatSupportBundle(bundle: SupportBundle): string {
  return `${JSON.stringify(bundle, null, 2)}\n`;
}

export function buildSupportBundleFilename(bundle: SupportBundle): string {
  const commit = bundle.product.buildCommit.replace(/[^a-zA-Z0-9._-]/g, '_').slice(0, 12) || 'dev';
  const timestamp = bundle.generatedAt.replace(/[:.]/g, '-');
  return `rocketmq-studio-support-${commit}-${timestamp}.json`;
}

export function buildSupportBundleSummary(bundle: SupportBundle): SupportBundleSummaryItem[] {
  const storage = bundle.browser.storage.localStorage;
  return [
    {
      label: '构建',
      value: `${bundle.product.version} / ${bundle.product.buildCommit}`,
    },
    {
      label: '生成时间',
      value: bundle.generatedAt,
    },
    {
      label: 'API 前缀',
      value: bundle.runtime.apiBaseUrl,
    },
    {
      label: '当前路径',
      value: bundle.runtime.pathname,
    },
    {
      label: '数据模式',
      value: bundle.preferences.effectiveDataMode,
    },
    {
      label: '界面偏好',
      value: `${bundle.preferences.language} / ${bundle.preferences.theme} / compact=${bundle.preferences.compactMode}`,
    },
    {
      label: '本地存储',
      value: `${storage.status}, ${storage.keyCount} keys`,
    },
    {
      label: '浏览器',
      value: `${bundle.browser.platform} / ${bundle.browser.language}`,
    },
    {
      label: '视口',
      value: `${bundle.browser.viewport.width}x${bundle.browser.viewport.height}@${bundle.browser.viewport.devicePixelRatio}`,
    },
    {
      label: '时区',
      value: bundle.browser.timezone,
    },
  ];
}
