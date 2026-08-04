// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.

import { isMockMode } from './dataMode';
import * as alertRuleAssetsApi from '../api/alertRuleAssets';
import type { AlertRuleAssetInfo } from '../api/alertRuleAssets';
import { mockAlertRuleAssets } from '../mock/alertRuleAssets';

export async function listAlertRuleAssets(): Promise<AlertRuleAssetInfo[]> {
  if (isMockMode()) {
    return mockAlertRuleAssets.map(({ name, group, ruleCount, severities }) => ({
      name,
      group,
      ruleCount,
      severities,
    }));
  }
  return alertRuleAssetsApi.listAlertRuleAssets();
}

export async function getAlertRuleAsset(name: string): Promise<string> {
  if (isMockMode()) {
    const found = mockAlertRuleAssets.find((asset) => asset.name === name);
    if (!found) {
      throw new Error(`Alert rule asset not found: ${name}`);
    }
    return found.yaml;
  }
  return alertRuleAssetsApi.getAlertRuleAsset(name);
}

export async function exportAlertRuleAsset(name: string): Promise<Blob> {
  if (isMockMode()) {
    const found = mockAlertRuleAssets.find((asset) => asset.name === name);
    const yaml = found ? found.yaml : '';
    return new Blob([yaml], { type: 'text/yaml' });
  }
  return alertRuleAssetsApi.exportAlertRuleAsset(name);
}
