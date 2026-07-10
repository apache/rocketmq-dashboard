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

import axios from 'axios';
import { message } from 'antd';

const client = axios.create({
  baseURL: '/api',
  timeout: 30000,
  withCredentials: true,
});

// CSRF token cache
let csrfToken: string | null = null;

/** Fetch CSRF token from backend cookie-based endpoint */
export async function fetchCsrfToken(): Promise<string> {
  try {
    const res = await axios.get('/rocketmq-dashboard/csrf-token', { withCredentials: true });
    csrfToken = res.data?.token || res.data?.csrfToken || null;
    return csrfToken || '';
  } catch {
    // CSRF endpoint may not be available in all environments
    return '';
  }
}

/** Get cached CSRF token */
export function getCsrfToken(): string | null {
  return csrfToken;
}

// Request interceptor: attach CSRF token for non-GET requests
client.interceptors.request.use(
  (config) => {
    // Attach CSRF token for state-changing requests
    if (config.method && !['get', 'head', 'options'].includes(config.method.toLowerCase())) {
      const token = getCsrfToken();
      if (token) {
        config.headers['X-XSRF-TOKEN'] = token;
      }
    }
    return config;
  },
  (error) => Promise.reject(error),
);

// Response interceptor: handle backend response formats
// Backend has two response patterns:
// 1. Direct return: controller returns object directly (most endpoints)
// 2. JsonResult wrapper: { status: 0, data: T, errMsg: null } (e.g., ClientController)
client.interceptors.response.use(
  (response) => {
    const data = response.data;
    // Handle JsonResult wrapper pattern
    if (data && typeof data === 'object' && 'status' in data) {
      if (data.status !== 0) {
        const errMsg = data.errMsg || data.message || '请求失败';
        message.error(errMsg);
        return Promise.reject(new Error(errMsg));
      }
      // Unwrap JsonResult: return the inner data field
      response.data = data.data !== undefined ? data.data : data;
    }
    return response;
  },
  (error) => {
    if (error.response?.status === 401) {
      message.warning('登录已过期，请重新登录');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  },
);

export default client;
