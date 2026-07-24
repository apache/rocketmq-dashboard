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

import { describe, it, expect } from 'vitest';
import { STATUS_MAP, CLUSTER_TYPE_MAP, TOPIC_TYPE_MAP, PROTOCOL_MAP, THEME_COLORS } from '../theme';

describe('theme constants', () => {
  describe('STATUS_MAP', () => {
    it('has labelKey for all status entries', () => {
      for (const [key, val] of Object.entries(STATUS_MAP)) {
        expect(val.labelKey, `STATUS_MAP[${key}].labelKey should exist`).toBeDefined();
        expect(val.labelKey, `STATUS_MAP[${key}].labelKey should start with theme.`).toMatch(
          /^theme\./,
        );
      }
    });

    it('has color and dot for all status entries', () => {
      for (const [key, val] of Object.entries(STATUS_MAP)) {
        expect(val.color, `STATUS_MAP[${key}].color should exist`).toBeDefined();
        expect(val.dot, `STATUS_MAP[${key}].dot should exist`).toBeDefined();
      }
    });

    it('contains required status keys', () => {
      expect(STATUS_MAP.healthy).toBeDefined();
      expect(STATUS_MAP.warning).toBeDefined();
      expect(STATUS_MAP.error).toBeDefined();
      expect(STATUS_MAP.offline).toBeDefined();
      expect(STATUS_MAP.connecting).toBeDefined();
    });
  });

  describe('CLUSTER_TYPE_MAP', () => {
    it('has labelKey for all cluster type entries', () => {
      for (const [key, val] of Object.entries(CLUSTER_TYPE_MAP)) {
        expect(val.labelKey, `CLUSTER_TYPE_MAP[${key}].labelKey should exist`).toBeDefined();
        expect(val.color, `CLUSTER_TYPE_MAP[${key}].color should exist`).toBeDefined();
      }
    });

    it('contains V4_DIRECT, V5_PROXY_LOCAL, V5_PROXY_CLUSTER', () => {
      expect(CLUSTER_TYPE_MAP.V4_DIRECT).toBeDefined();
      expect(CLUSTER_TYPE_MAP.V5_PROXY_LOCAL).toBeDefined();
      expect(CLUSTER_TYPE_MAP.V5_PROXY_CLUSTER).toBeDefined();
    });
  });

  describe('TOPIC_TYPE_MAP', () => {
    it('has labelKey for all topic type entries', () => {
      for (const [key, val] of Object.entries(TOPIC_TYPE_MAP)) {
        expect(val.labelKey, `TOPIC_TYPE_MAP[${key}].labelKey should exist`).toBeDefined();
        expect(val.color, `TOPIC_TYPE_MAP[${key}].color should exist`).toBeDefined();
      }
    });

    it('contains NORMAL, FIFO, DELAY, TRANSACTION, LITE', () => {
      expect(TOPIC_TYPE_MAP.NORMAL).toBeDefined();
      expect(TOPIC_TYPE_MAP.FIFO).toBeDefined();
      expect(TOPIC_TYPE_MAP.DELAY).toBeDefined();
      expect(TOPIC_TYPE_MAP.TRANSACTION).toBeDefined();
      expect(TOPIC_TYPE_MAP.LITE).toBeDefined();
    });
  });

  describe('PROTOCOL_MAP', () => {
    it('has labelKey for all protocol entries', () => {
      for (const [key, val] of Object.entries(PROTOCOL_MAP)) {
        expect(val.labelKey, `PROTOCOL_MAP[${key}].labelKey should exist`).toBeDefined();
        expect(val.color, `PROTOCOL_MAP[${key}].color should exist`).toBeDefined();
      }
    });
  });

  describe('THEME_COLORS', () => {
    it('contains primary colors', () => {
      expect(THEME_COLORS.primary).toBeDefined();
      expect(THEME_COLORS.success).toBeDefined();
      expect(THEME_COLORS.warning).toBeDefined();
      expect(THEME_COLORS.error).toBeDefined();
    });
  });
});
