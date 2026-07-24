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

import axios, {
  AxiosError,
  AxiosHeaders,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios';
import { message } from 'antd';
import { handleUnauthorized, TOKEN_STORAGE_KEY } from '../stores/authStorage';
import { API_BASE_URL } from '../config';

const SUCCESS_BUSINESS_CODES = new Set([0, 200]);
const MAX_HEADER_NAMES = 256;
const MAX_CREDENTIAL_LENGTH = 8192;
const MAX_CREDENTIAL_VALUES = 8;
const MAX_SCAN_DEPTH = 5;
const MAX_SCAN_ENTRIES = 512;
const MAX_SCAN_STRING_LENGTH = 65536;
const AUTHORIZATION_NAMES = ['Authorization', 'authorization'] as const;

interface BusinessResponse {
  code?: unknown;
  message?: unknown;
}

function isBusinessResponse(data: unknown): data is BusinessResponse {
  return typeof data === 'object' && data !== null;
}

function getBusinessError(data: unknown): string | null {
  if (!isBusinessResponse(data) || data.code === undefined) {
    return null;
  }
  if (typeof data.code === 'number' && SUCCESS_BUSINESS_CODES.has(data.code)) {
    return null;
  }
  return typeof data.message === 'string' && data.message.trim() ? data.message : '请求失败';
}

function isObject(value: unknown): value is object {
  return (typeof value === 'object' && value !== null) || typeof value === 'function';
}

interface PropertyRead {
  ok: boolean;
  value?: unknown;
}

function tryReadProperty(source: unknown, key: string): PropertyRead {
  if (!isObject(source)) {
    return { ok: true, value: undefined };
  }
  try {
    return { ok: true, value: Reflect.get(source, key) };
  } catch {
    return { ok: false };
  }
}

function readProperty(source: unknown, key: string): unknown {
  return tryReadProperty(source, key).value;
}

function isNativeHeaders(value: object): value is Headers {
  return typeof Headers !== 'undefined' && value instanceof Headers;
}

function isPlainObject(value: object): boolean {
  const prototype = Object.getPrototypeOf(value);
  return prototype === Object.prototype || prototype === null;
}

function isMissingHeaderValue(value: unknown): boolean {
  return value === null || value === undefined;
}

function collectCredentialValue(value: unknown, credentials: Set<string>): boolean {
  if (isMissingHeaderValue(value)) {
    return true;
  }
  const values = Array.isArray(value) ? value : [value];
  if (values.length > MAX_CREDENTIAL_VALUES) {
    return false;
  }

  for (const item of values) {
    if (typeof item !== 'string' || item.length > MAX_CREDENTIAL_LENGTH) {
      return false;
    }
    if (item.length > 0) {
      credentials.add(item);
    }
    const bearerMatch = /^Bearer[ \t]+([^\r\n]+)$/i.exec(item);
    const bearerToken = bearerMatch?.[1].trim();
    if (bearerToken) {
      if (bearerToken.length > MAX_CREDENTIAL_LENGTH) {
        return false;
      }
      credentials.add(bearerToken);
    }
    if (credentials.size > MAX_CREDENTIAL_VALUES) {
      return false;
    }
  }
  return true;
}

function sanitizeAuthorizationHeader(headers: object, credentials: Set<string>): boolean {
  try {
    const isMethodHeaders = headers instanceof AxiosHeaders || isNativeHeaders(headers);
    if (!isMethodHeaders && !isPlainObject(headers)) {
      return false;
    }

    const get = Reflect.get(headers, 'get');
    const has = Reflect.get(headers, 'has');
    const deleteHeader = Reflect.get(headers, 'delete');
    if (
      isMethodHeaders &&
      (typeof get !== 'function' || typeof has !== 'function' || typeof deleteHeader !== 'function')
    ) {
      return false;
    }

    for (const name of AUTHORIZATION_NAMES) {
      if (
        (typeof get === 'function' &&
          !collectCredentialValue(get.call(headers, name), credentials)) ||
        !collectCredentialValue(Reflect.get(headers, name), credentials)
      ) {
        return false;
      }
    }

    const keys = Reflect.ownKeys(headers);
    if (keys.length > MAX_HEADER_NAMES) {
      return false;
    }
    for (const key of keys) {
      const descriptor = Reflect.getOwnPropertyDescriptor(headers, key);
      if (!descriptor || !('value' in descriptor)) {
        return false;
      }
      if (
        typeof key === 'string' &&
        key.toLowerCase() === 'authorization' &&
        !collectCredentialValue(descriptor.value, credentials)
      ) {
        return false;
      }
    }

    if (typeof deleteHeader === 'function') {
      for (const name of AUTHORIZATION_NAMES) {
        deleteHeader.call(headers, name);
      }
    }
    for (const key of keys) {
      if (
        typeof key === 'string' &&
        key.toLowerCase() === 'authorization' &&
        !Reflect.deleteProperty(headers, key)
      ) {
        return false;
      }
    }

    const postGet = Reflect.get(headers, 'get');
    const postHas = Reflect.get(headers, 'has');
    if ((isMethodHeaders || typeof get === 'function') && typeof postGet !== 'function') {
      return false;
    }
    if ((isMethodHeaders || typeof has === 'function') && typeof postHas !== 'function') {
      return false;
    }
    for (const name of AUTHORIZATION_NAMES) {
      if (
        (typeof postGet === 'function' && !isMissingHeaderValue(postGet.call(headers, name))) ||
        (typeof postHas === 'function' && postHas.call(headers, name) !== false) ||
        !isMissingHeaderValue(Reflect.get(headers, name))
      ) {
        return false;
      }
    }

    const remainingKeys = Reflect.ownKeys(headers);
    return (
      remainingKeys.length <= MAX_HEADER_NAMES &&
      remainingKeys.every((key) => typeof key !== 'string' || key.toLowerCase() !== 'authorization')
    );
  } catch {
    return false;
  }
}

interface ScanState {
  credentials: Set<string>;
  remaining: number;
  seen: Set<object>;
}

function scanFailure(): never {
  throw new Error('Unsafe retained error surface');
}

function scanString(value: string, state: ScanState): void {
  if (
    value.length > MAX_SCAN_STRING_LENGTH ||
    [...state.credentials].some((credential) => credential.length > 0 && value.includes(credential))
  ) {
    scanFailure();
  }
}

function scanRetainedValue(value: unknown, state: ScanState, depth = 0): void {
  if (typeof value === 'string') {
    scanString(value, state);
    return;
  }
  if (
    value === null ||
    value === undefined ||
    typeof value === 'number' ||
    typeof value === 'boolean' ||
    typeof value === 'bigint' ||
    typeof value === 'symbol' ||
    typeof value === 'function'
  ) {
    return;
  }
  if (depth > MAX_SCAN_DEPTH || !isObject(value)) {
    scanFailure();
  }
  if (state.seen.has(value)) {
    return;
  }
  state.seen.add(value);

  if (isNativeHeaders(value)) {
    let count = 0;
    value.forEach((headerValue, key) => {
      if (++count > MAX_HEADER_NAMES || --state.remaining < 0) {
        scanFailure();
      }
      scanString(key, state);
      scanString(headerValue, state);
    });
    return;
  }
  if (!(value instanceof AxiosHeaders) && !Array.isArray(value) && !isPlainObject(value)) {
    scanFailure();
  }
  const keys = Reflect.ownKeys(value);
  if ((state.remaining -= keys.length) < 0) {
    scanFailure();
  }
  for (const key of keys) {
    scanString(String(key), state);
    const descriptor = Reflect.getOwnPropertyDescriptor(value, key);
    if (!descriptor || !('value' in descriptor)) {
      scanFailure();
    }
    scanRetainedValue(descriptor.value, state, depth + 1);
  }
}

function scanReadString(source: unknown, key: string, state: ScanState): void {
  const property = tryReadProperty(source, key);
  if (!property.ok) {
    scanFailure();
  }
  if (typeof property.value === 'string') {
    scanString(property.value, state);
  }
}

function scanRetainedError(
  error: object,
  response: unknown,
  configs: readonly unknown[],
  headers: readonly object[],
  credentials: Set<string>,
): boolean {
  if (credentials.size === 0) {
    return true;
  }

  try {
    const state: ScanState = {
      credentials,
      remaining: MAX_SCAN_ENTRIES,
      seen: new Set(),
    };
    for (const key of ['message', 'stack', 'name', 'code']) {
      scanReadString(error, key, state);
    }
    for (const config of configs) {
      if (config !== null && config !== undefined) {
        scanRetainedValue(config, state);
      }
    }
    for (const headerContainer of headers) {
      scanRetainedValue(headerContainer, state);
    }
    if (response !== null && response !== undefined) {
      const dataRead = tryReadProperty(response, 'data');
      if (!dataRead.ok) {
        scanFailure();
      }
      scanRetainedValue(dataRead.value, state);
      scanReadString(response, 'statusText', state);
    }
    return true;
  } catch {
    return false;
  }
}

function redactErrorHeadersInPlace(error: unknown): boolean {
  try {
    if (!isObject(error)) {
      return true;
    }
    const responseRead = tryReadProperty(error, 'response');
    if (!responseRead.ok) {
      return false;
    }
    const response = responseRead.value;
    const ownerReads = [
      tryReadProperty(error, 'config'),
      tryReadProperty(error, 'request'),
      { ok: true, value: response },
      tryReadProperty(response, 'config'),
      tryReadProperty(response, 'request'),
    ];
    const credentials = new Set<string>();
    const visitedHeaders = new Set<object>();
    const headerContainers: object[] = [];

    for (const ownerRead of ownerReads) {
      if (!ownerRead.ok) {
        return false;
      }
      const owner = ownerRead.value;
      if (!isObject(owner)) {
        continue;
      }
      const headersRead = tryReadProperty(owner, 'headers');
      if (!headersRead.ok) {
        return false;
      }
      const headers = headersRead.value;
      if (headers === null || headers === undefined) {
        continue;
      }
      if (!isObject(headers)) {
        return false;
      }
      if (!visitedHeaders.has(headers)) {
        visitedHeaders.add(headers);
        headerContainers.push(headers);
        if (!sanitizeAuthorizationHeader(headers, credentials)) {
          return false;
        }
      }
    }
    return scanRetainedError(
      error,
      response,
      [ownerReads[0].value, ownerReads[3].value],
      headerContainers,
      credentials,
    );
  } catch {
    return false;
  }
}

function finiteNumericProperty(source: unknown, key: string): number | undefined {
  const property = tryReadProperty(source, key);
  return property.ok && typeof property.value === 'number' && Number.isFinite(property.value)
    ? property.value
    : undefined;
}

function safeErrorStatus(error: unknown): number | undefined {
  const responseRead = tryReadProperty(error, 'response');
  const responseStatus = responseRead.ok
    ? finiteNumericProperty(responseRead.value, 'status')
    : undefined;
  return responseStatus ?? finiteNumericProperty(error, 'status');
}

function fixedSafeError(error: unknown): AxiosError {
  const status = safeErrorStatus(error);
  const safeConfig = {
    headers: new AxiosHeaders(),
  } as InternalAxiosRequestConfig;
  const safeResponse: AxiosResponse | undefined =
    status === undefined
      ? undefined
      : {
          data: undefined,
          status,
          statusText: '',
          headers: new AxiosHeaders(),
          config: safeConfig,
        };
  return new AxiosError(
    status === undefined ? 'Request failed' : `Request failed with status code ${status}`,
    undefined,
    safeConfig,
    undefined,
    safeResponse,
  );
}

function sanitizeAuthorizationError(error: unknown): unknown {
  return redactErrorHeadersInPlace(error) ? error : fixedSafeError(error);
}

function responseStatus(error: unknown): number | undefined {
  try {
    const status = readProperty(readProperty(error, 'response'), 'status');
    return typeof status === 'number' ? status : undefined;
  } catch {
    return undefined;
  }
}

export function createApiClient(navigate?: (url: string) => void) {
  const client = axios.create({
    baseURL: API_BASE_URL,
    timeout: 30000,
  });

  // Request interceptor: attach Authorization header
  client.interceptors.request.use(
    (config) => {
      const token = localStorage.getItem(TOKEN_STORAGE_KEY);
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
      return config;
    },
    (error) => Promise.reject(sanitizeAuthorizationError(error)),
  );

  // Response interceptor: check business code and handle 401
  client.interceptors.response.use(
    (response) => {
      const errorMessage = getBusinessError(response.data);
      if (errorMessage) {
        message.error(errorMessage);
        return Promise.reject(new Error(errorMessage));
      }
      return response;
    },
    (error) => {
      const sanitizedError = sanitizeAuthorizationError(error);
      if (responseStatus(sanitizedError) === 401) {
        handleUnauthorized(navigate);
      }
      return Promise.reject(sanitizedError);
    },
  );

  return client;
}

const client = createApiClient();

export default client;
