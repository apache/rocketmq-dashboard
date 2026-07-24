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
const MAX_SAFE_COPY_DEPTH = 4;
const MAX_SAFE_COPY_ENTRIES = 256;
const OMIT_VALUE = Symbol('omit-value');

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

function deleteAuthorizationHeader(headers: unknown): boolean {
  if (!isObject(headers)) {
    return headers === null || headers === undefined;
  }

  try {
    const deleteHeader = Reflect.get(headers, 'delete');
    if (typeof deleteHeader === 'function') {
      deleteHeader.call(headers, 'Authorization');
    }

    const names = Object.getOwnPropertyNames(headers);
    if (names.length > MAX_HEADER_NAMES) {
      return false;
    }
    for (const key of names) {
      if (key.toLowerCase() === 'authorization' && !Reflect.deleteProperty(headers, key)) {
        return false;
      }
    }

    const hasHeader = Reflect.get(headers, 'has');
    if (typeof hasHeader === 'function' && hasHeader.call(headers, 'Authorization')) {
      return false;
    }
    const remainingNames = Object.getOwnPropertyNames(headers);
    return (
      remainingNames.length <= MAX_HEADER_NAMES &&
      remainingNames.every((key) => key.toLowerCase() !== 'authorization')
    );
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
      tryReadProperty(response, 'config'),
      tryReadProperty(response, 'request'),
    ];
    const visited = new Set<object>();

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
      if (!visited.has(headers)) {
        visited.add(headers);
        if (!deleteAuthorizationHeader(headers)) {
          return false;
        }
      }
    }
    return true;
  } catch {
    return false;
  }
}

interface SafeCopyState {
  remaining: number;
  seen: Set<object>;
}

function copySafeValue(
  value: unknown,
  state: SafeCopyState,
  depth: number,
): unknown | typeof OMIT_VALUE {
  if (
    value === null ||
    typeof value === 'string' ||
    typeof value === 'number' ||
    typeof value === 'boolean'
  ) {
    return value;
  }
  if (value === undefined) {
    return undefined;
  }
  if (!isObject(value) || depth >= MAX_SAFE_COPY_DEPTH || state.seen.has(value)) {
    return OMIT_VALUE;
  }

  try {
    state.seen.add(value);
    if (Array.isArray(value)) {
      if (value.length > state.remaining) {
        return OMIT_VALUE;
      }
      const copy: unknown[] = [];
      for (const item of value) {
        state.remaining -= 1;
        const copied = copySafeValue(item, state, depth + 1);
        if (copied !== OMIT_VALUE) {
          copy.push(copied);
        }
      }
      return copy;
    }

    const prototype = Object.getPrototypeOf(value);
    if (prototype !== Object.prototype && prototype !== null) {
      return OMIT_VALUE;
    }
    const names = Object.getOwnPropertyNames(value);
    if (names.length > state.remaining) {
      return OMIT_VALUE;
    }
    const copy: Record<string, unknown> = Object.create(null);
    for (const key of names) {
      state.remaining -= 1;
      if (key.toLowerCase() === 'authorization') {
        continue;
      }
      const descriptor = Object.getOwnPropertyDescriptor(value, key);
      if (!descriptor || !('value' in descriptor)) {
        continue;
      }
      const copied = copySafeValue(descriptor.value, state, depth + 1);
      if (copied !== OMIT_VALUE) {
        copy[key] = copied;
      }
    }
    return copy;
  } catch {
    return OMIT_VALUE;
  } finally {
    state.seen.delete(value);
  }
}

function copyHeaders(headers: unknown): AxiosHeaders {
  const copy = new AxiosHeaders();
  if (!isObject(headers)) {
    return copy;
  }

  try {
    const forEachHeader = Reflect.get(headers, 'forEach');
    if (typeof forEachHeader === 'function') {
      let count = 0;
      forEachHeader.call(headers, (value: unknown, key: unknown) => {
        count += 1;
        if (count > MAX_HEADER_NAMES) {
          throw new Error('Header copy limit exceeded');
        }
        if (typeof key === 'string' && key.toLowerCase() !== 'authorization') {
          copy.set(key, String(value));
        }
      });
      return copy;
    }

    const names = Object.getOwnPropertyNames(headers);
    if (names.length > MAX_HEADER_NAMES) {
      return new AxiosHeaders();
    }
    for (const key of names) {
      if (key.toLowerCase() === 'authorization') {
        continue;
      }
      const descriptor = Object.getOwnPropertyDescriptor(headers, key);
      if (!descriptor || !('value' in descriptor)) {
        continue;
      }
      const value = descriptor.value;
      if (
        typeof value === 'string' ||
        typeof value === 'number' ||
        typeof value === 'boolean' ||
        value === null
      ) {
        copy.set(key, value);
      } else if (Array.isArray(value) && value.every((item) => typeof item === 'string')) {
        copy.set(key, value);
      }
    }
  } catch {
    return new AxiosHeaders();
  }
  return copy;
}

function copyConfig(config: unknown): InternalAxiosRequestConfig {
  const state: SafeCopyState = {
    remaining: MAX_SAFE_COPY_ENTRIES,
    seen: new Set(),
  };
  const copied = copySafeValue(config, state, 0);
  const result =
    copied !== OMIT_VALUE && isObject(copied) && !Array.isArray(copied)
      ? (copied as Record<string, unknown>)
      : Object.create(null);
  result.headers = copyHeaders(readProperty(config, 'headers'));
  return result as unknown as InternalAxiosRequestConfig;
}

function copyResponse(
  response: unknown,
  errorConfig: unknown,
  safeErrorConfig: InternalAxiosRequestConfig,
): AxiosResponse | undefined {
  if (!isObject(response)) {
    return undefined;
  }

  const responseConfig = readProperty(response, 'config');
  const safeResponseConfig =
    responseConfig === errorConfig ? safeErrorConfig : copyConfig(responseConfig);
  const dataState: SafeCopyState = {
    remaining: MAX_SAFE_COPY_ENTRIES,
    seen: new Set(),
  };
  const copiedData = copySafeValue(readProperty(response, 'data'), dataState, 0);
  const status = readProperty(response, 'status');
  const statusText = readProperty(response, 'statusText');

  return {
    data: copiedData === OMIT_VALUE ? undefined : copiedData,
    status: typeof status === 'number' ? status : 0,
    statusText: typeof statusText === 'string' ? statusText : '',
    headers: copyHeaders(readProperty(response, 'headers')),
    config: safeResponseConfig,
  };
}

function safeErrorCopy(error: unknown): AxiosError {
  const config = readProperty(error, 'config');
  const safeConfig = copyConfig(config);
  const safeResponse = copyResponse(readProperty(error, 'response'), config, safeConfig);
  const messageValue = readProperty(error, 'message');
  const codeValue = readProperty(error, 'code');
  const nameValue = readProperty(error, 'name');
  const statusValue = readProperty(error, 'status');
  const safeError = new AxiosError(
    typeof messageValue === 'string' ? messageValue : 'Request failed',
    typeof codeValue === 'string' ? codeValue : undefined,
    safeConfig,
    undefined,
    safeResponse,
  );
  if (typeof nameValue === 'string') {
    safeError.name = nameValue;
  }
  if (!safeResponse && typeof statusValue === 'number') {
    safeError.status = statusValue;
  }
  return safeError;
}

function sanitizeAuthorizationError(error: unknown): unknown {
  return redactErrorHeadersInPlace(error) ? error : safeErrorCopy(error);
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
