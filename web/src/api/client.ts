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
import { clearAuthSession, TOKEN_STORAGE_KEY } from '../stores/authStorage';
import { API_BASE_URL } from '../config';

const SUCCESS_BUSINESS_CODES = new Set([0, 200]);

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
  (error) => Promise.reject(error),
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
    if (error.response?.status === 401) {
      clearAuthSession();
      window.location.href = '/';
    }
    return Promise.reject(error);
  },
);

export default client;
