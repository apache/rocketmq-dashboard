// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import { isMockMode } from './dataMode';
import * as alertRuleAssetsApi from '../api/alertRuleAssets';
import type { AlertRuleAssetInfo } from '../api/alertRuleAssets';
import { mockAlertRuleAssets } from '../mock/alertRuleAssets';

function getMockAlertRuleAsset(name: string) {
  const found = mockAlertRuleAssets.find((asset) => asset.name === name);
  if (!found) {
    throw new Error(`Alert rule asset not found: ${name}`);
  }
  return found;
}

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
    return getMockAlertRuleAsset(name).yaml;
  }
  return alertRuleAssetsApi.getAlertRuleAsset(name);
}

export async function exportAlertRuleAsset(name: string): Promise<Blob> {
  if (isMockMode()) {
    return new Blob([getMockAlertRuleAsset(name).yaml], { type: 'text/yaml' });
  }
  return alertRuleAssetsApi.exportAlertRuleAsset(name);
}
