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

import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { LoginResponse } from '../auth';

// Mock the API client module
vi.mock('../client', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn(),
  },
}));

import client from '../client';
import { login, logout, checkLogin } from '../auth';

const mockPost = vi.mocked(client.post);
const mockGet = vi.mocked(client.get);

describe('auth API', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('login', () => {
    it('calls POST /login/login.do with credentials', async () => {
      const mockResponse: LoginResponse = {
        username: 'admin',
        type: 'ADMIN',
        contextPath: '',
      };
      mockPost.mockResolvedValueOnce({ data: mockResponse });

      const result = await login('admin', 'password123');

      expect(mockPost).toHaveBeenCalledWith('/login/login.do', {
        username: 'admin',
        password: 'password123',
      });
      expect(result).toEqual(mockResponse);
    });

    it('returns token and role when present (JWT-based auth)', async () => {
      const mockResponse: LoginResponse = {
        username: 'admin',
        type: 'ADMIN',
        contextPath: '',
        token: 'jwt-token-xyz',
        role: 'ADMIN',
      };
      mockPost.mockResolvedValueOnce({ data: mockResponse });

      const result = await login('admin', 'password123');

      expect(result.token).toBe('jwt-token-xyz');
      expect(result.role).toBe('ADMIN');
    });

    it('works without token and role (session-based auth)', async () => {
      const mockResponse: LoginResponse = {
        username: 'admin',
        type: 'ADMIN',
        contextPath: '',
      };
      mockPost.mockResolvedValueOnce({ data: mockResponse });

      const result = await login('admin', 'password123');

      expect(result.token).toBeUndefined();
      expect(result.role).toBeUndefined();
    });
  });

  describe('logout', () => {
    it('calls POST /login/logout.do', async () => {
      mockPost.mockResolvedValueOnce({});

      await logout();

      expect(mockPost).toHaveBeenCalledWith('/login/logout.do');
    });
  });

  describe('checkLogin', () => {
    it('calls GET /login/check.query and returns status', async () => {
      const mockData = { logined: true, loginRequired: false };
      mockGet.mockResolvedValueOnce({ data: mockData });

      const result = await checkLogin();

      expect(mockGet).toHaveBeenCalledWith('/login/check.query');
      expect(result).toEqual(mockData);
    });
  });
});
