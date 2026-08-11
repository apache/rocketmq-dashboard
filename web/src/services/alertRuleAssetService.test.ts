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

import { describe, expect, it, vi } from 'vitest';
import type { AlertRuleAssetInfo } from '../api/alertRuleAssets';
import * as api from '../api/alertRuleAssets';
import { mockAlertRuleAssets } from '../mock/alertRuleAssets';

const { mode } = vi.hoisted(() => ({ mode: { mock: true } }));

vi.mock('./dataMode', () => ({ isMockMode: () => mode.mock }));
vi.mock('../api/alertRuleAssets', () => ({
  listAlertRuleAssets: vi.fn(),
  getAlertRuleAsset: vi.fn(),
  exportAlertRuleAsset: vi.fn(),
}));

import {
  exportAlertRuleAsset,
  getAlertRuleAsset,
  listAlertRuleAssets,
} from './alertRuleAssetService';

describe('alertRuleAssetService (mock mode)', () => {
  it('maps mock assets to AlertRuleAssetInfo list', async () => {
    mode.mock = true;
    const assets = await listAlertRuleAssets();
    expect(assets.length).toBe(mockAlertRuleAssets.length);
    expect(assets[0]).toEqual({
      name: mockAlertRuleAssets[0].name,
      group: mockAlertRuleAssets[0].group,
      ruleCount: mockAlertRuleAssets[0].ruleCount,
      severities: mockAlertRuleAssets[0].severities,
    });
  });

  it('returns yaml for a known asset', async () => {
    mode.mock = true;
    const yaml = await getAlertRuleAsset('rocketmq-broker-down');
    expect(yaml).toContain('RocketMQBrokerDown');
  });

  it('throws for an unknown asset', async () => {
    mode.mock = true;
    await expect(getAlertRuleAsset('does-not-exist')).rejects.toThrow();
  });

  it('exports a yaml blob', async () => {
    mode.mock = true;
    const blob = await exportAlertRuleAsset('rocketmq-broker-down');
    expect(blob).toBeInstanceOf(Blob);
    expect(blob.type).toBe('text/yaml');
  });

  it('rejects an export for an unknown asset', async () => {
    mode.mock = true;

    await expect(exportAlertRuleAsset('does-not-exist')).rejects.toThrow(
      'Alert rule asset not found: does-not-exist',
    );
  });
});

describe('alertRuleAssetService (real mode)', () => {
  it('delegates list to the api module', async () => {
    mode.mock = false;
    const data: AlertRuleAssetInfo[] = [
      { name: 'a', group: 'g', ruleCount: 1, severities: ['info'] },
    ];
    vi.mocked(api.listAlertRuleAssets).mockResolvedValue(data);

    const result = await listAlertRuleAssets();
    expect(api.listAlertRuleAssets).toHaveBeenCalled();
    expect(result).toEqual(data);
  });

  it('delegates get to the api module', async () => {
    mode.mock = false;
    vi.mocked(api.getAlertRuleAsset).mockResolvedValue('yaml-content');

    const result = await getAlertRuleAsset('a');
    expect(api.getAlertRuleAsset).toHaveBeenCalledWith('a');
    expect(result).toBe('yaml-content');
  });

  it('delegates export to the api module', async () => {
    mode.mock = false;
    const blob = new Blob(['x']);
    vi.mocked(api.exportAlertRuleAsset).mockResolvedValue(blob);

    const result = await exportAlertRuleAsset('a');
    expect(api.exportAlertRuleAsset).toHaveBeenCalledWith('a');
    expect(result).toBe(blob);
  });
});
