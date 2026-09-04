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

import type { AuditRecord } from '../../api/ops';

export type AuditOperationCategory =
  | 'metadata'
  | 'proxy'
  | 'messaging'
  | 'cluster'
  | 'security'
  | 'settings'
  | 'alerts'
  | 'instance'
  | 'certificate'
  | 'other';

export interface AuditPresentation {
  /** English fallback when no i18n resolver is available. */
  label: string;
  color: string;
  /** Translation key resolved via the language context at render time. */
  labelKey?: string;
}

export interface AuditOperationPresentation extends AuditPresentation {
  category: AuditOperationCategory;
}

export interface AuditDetailToken {
  label: string;
  value: string;
}

const operationPresentation: Record<string, AuditOperationPresentation> = {
  CREATE_TOPIC: { label: 'Create Topic', color: 'blue', category: 'metadata' },
  UPDATE_TOPIC: { label: 'Update Topic', color: 'cyan', category: 'metadata' },
  DELETE_TOPIC: { label: 'Delete Topic', color: 'volcano', category: 'metadata' },

  CREATE_GROUP: { label: 'Create Group', color: 'blue', category: 'metadata' },
  UPDATE_GROUP: { label: 'Update Group', color: 'cyan', category: 'metadata' },
  DELETE_GROUP: { label: 'Delete Group', color: 'volcano', category: 'metadata' },
  RESET_OFFSET: { label: 'Reset Offset', color: 'gold', category: 'metadata' },

  ADD_PROXY_ADDRESS: { label: 'Add Proxy Address', color: 'geekblue', category: 'proxy' },
  REMOVE_PROXY_ADDRESS: { label: 'Remove Proxy Address', color: 'volcano', category: 'proxy' },
  RELOAD_PROXY_CONFIG: { label: 'Reload Proxy Config', color: 'purple', category: 'proxy' },

  SEND_MESSAGE: { label: 'Send Message', color: 'green', category: 'messaging' },
  RESEND_DLQ: { label: 'Resend DLQ', color: 'green', category: 'messaging' },
  DIRECT_CONSUME_MESSAGE: {
    label: 'Direct Consume Message',
    color: 'green',
    category: 'messaging',
  },

  UPDATE_BROKER_CONFIG: { label: 'Update Broker Config', color: 'purple', category: 'cluster' },
  UPDATE_CLUSTER_CONFIG: { label: 'Update Cluster Config', color: 'purple', category: 'cluster' },
  RESTART_BROKER: { label: 'Restart Broker', color: 'orange', category: 'cluster' },

  CREATE_ACL_RULE: { label: 'Create ACL Rule', color: 'blue', category: 'security' },
  UPDATE_ACL_RULE: { label: 'Update ACL Rule', color: 'cyan', category: 'security' },
  DELETE_ACL_RULE: { label: 'Delete ACL Rule', color: 'volcano', category: 'security' },
  CREATE_ACL_USER: { label: 'Create ACL User', color: 'blue', category: 'security' },
  UPDATE_ACL_USER: { label: 'Update ACL User', color: 'cyan', category: 'security' },
  DELETE_ACL_USER: { label: 'Delete ACL User', color: 'volcano', category: 'security' },
  UPSERT_PLAIN_ACCESS_CONFIG: {
    label: 'Upsert Plain Access Config',
    color: 'purple',
    category: 'security',
  },

  UPDATE_SETTINGS: { label: 'Update Settings', color: 'purple', category: 'settings' },
  CREATE_DATA_SOURCE: { label: 'Create Data Source', color: 'blue', category: 'settings' },
  UPDATE_DATA_SOURCE: { label: 'Update Data Source', color: 'cyan', category: 'settings' },
  DELETE_DATA_SOURCE: { label: 'Delete Data Source', color: 'volcano', category: 'settings' },
  CREATE_CLOUD_CREDENTIAL: {
    label: 'Create Cloud Credential',
    color: 'blue',
    category: 'settings',
  },
  UPDATE_CLOUD_CREDENTIAL: {
    label: 'Update Cloud Credential',
    color: 'cyan',
    category: 'settings',
  },
  DELETE_CLOUD_CREDENTIAL: {
    label: 'Delete Cloud Credential',
    color: 'volcano',
    category: 'settings',
  },

  CREATE_ALERT_RULE: { label: 'Create Alert Rule', color: 'blue', category: 'alerts' },
  UPDATE_ALERT_RULE: { label: 'Update Alert Rule', color: 'cyan', category: 'alerts' },
  TOGGLE_ALERT_RULE: { label: 'Toggle Alert Rule', color: 'gold', category: 'alerts' },
  DELETE_ALERT_RULE: { label: 'Delete Alert Rule', color: 'volcano', category: 'alerts' },
  ACKNOWLEDGE_SYSTEM_ALERT: {
    label: 'Acknowledge System Alert',
    color: 'green',
    category: 'alerts',
  },
  CLEAR_ACKNOWLEDGED_SYSTEM_ALERTS: {
    label: 'Clear Acknowledged Alerts',
    color: 'volcano',
    category: 'alerts',
  },

  CREATE_INSTANCE: { label: 'Create Instance', color: 'blue', category: 'instance' },
  UPDATE_INSTANCE: { label: 'Update Instance', color: 'cyan', category: 'instance' },
  DELETE_INSTANCE: { label: 'Delete Instance', color: 'volcano', category: 'instance' },

  CREATE_K8S_CERTIFICATE: {
    label: 'Create K8s Certificate',
    color: 'blue',
    category: 'certificate',
  },
  UPDATE_K8S_CERTIFICATE: {
    label: 'Update K8s Certificate',
    color: 'cyan',
    category: 'certificate',
  },
  RENEW_K8S_CERTIFICATE: {
    label: 'Renew K8s Certificate',
    color: 'green',
    category: 'certificate',
  },
  DELETE_K8S_CERTIFICATE: {
    label: 'Delete K8s Certificate',
    color: 'volcano',
    category: 'certificate',
  },
};

const resourcePresentation: Record<string, AuditPresentation> = {
  TOPIC: { label: 'Topic', color: 'blue' },
  GROUP: { label: 'Consumer Group', color: 'geekblue' },
  CONSUMER_GROUP: { label: 'Consumer Group', color: 'geekblue' },
  MESSAGE: { label: 'Message', color: 'green' },
  DLQ: { label: 'DLQ', color: 'green' },
  PROXY: { label: 'Proxy', color: 'purple' },
  BROKER: { label: 'Broker', color: 'orange' },
  CLUSTER: { label: 'Cluster', color: 'orange' },
  INSTANCE: { label: 'Instance', color: 'cyan' },
  ACL_RULE: { label: 'ACL Rule', color: 'red' },
  ACL_USER: { label: 'ACL User', color: 'red' },
  SETTINGS: { label: 'Settings', color: 'purple' },
  METRICS_DATA_SOURCE: { label: 'Metrics Data Source', color: 'purple' },
  CLOUD_CREDENTIAL: { label: 'Cloud Credential', color: 'cyan' },
  ALERT_RULE: { label: 'Alert Rule', color: 'gold' },
  SYSTEM_ALERT: { label: 'System Alert', color: 'gold' },
  K8S_CERTIFICATE: { label: 'K8s Certificate', color: 'lime' },
};

const resultPresentation: Record<string, AuditPresentation> = {
  SUCCESS: { label: 'Success', color: 'green' },
  PARTIAL: { label: 'Partial', color: 'orange' },
  FAILED: { label: 'Failed', color: 'red' },
  FAILURE: { label: 'Failed', color: 'red' },
};

const categoryFallbackColor: Record<AuditOperationCategory, string> = {
  metadata: 'blue',
  proxy: 'purple',
  messaging: 'green',
  cluster: 'orange',
  security: 'red',
  settings: 'cyan',
  alerts: 'gold',
  instance: 'geekblue',
  certificate: 'lime',
  other: 'default',
};

export function normalizeAuditCode(code: string | null | undefined): string {
  return (code ?? '').trim().toUpperCase();
}

export function formatAuditCode(code: string | null | undefined): string {
  const normalized = normalizeAuditCode(code);
  if (!normalized) return '-';
  return normalized
    .split('_')
    .filter(Boolean)
    .map((part) => part.charAt(0) + part.slice(1).toLowerCase())
    .join(' ');
}

export function getAuditOperationPresentation(
  operationType: string | null | undefined,
): AuditOperationPresentation {
  const normalized = normalizeAuditCode(operationType);
  const known = operationPresentation[normalized];
  if (known) {
    return { ...known, labelKey: `audit.op.${normalized}` };
  }
  return {
    label: formatAuditCode(normalized),
    color: categoryFallbackColor.other,
    category: 'other',
  };
}

export function getAuditResourcePresentation(
  resourceType: string | null | undefined,
): AuditPresentation {
  const normalized = normalizeAuditCode(resourceType);
  const known = resourcePresentation[normalized];
  if (known) {
    return { ...known, labelKey: `audit.res.${normalized}` };
  }
  return { label: formatAuditCode(normalized), color: 'default' };
}

export function getAuditResultPresentation(result: string | null | undefined): AuditPresentation {
  const normalized = normalizeAuditCode(result);
  const known = resultPresentation[normalized];
  if (known) {
    const key = normalized === 'FAILURE' ? 'FAILED' : normalized;
    return { ...known, labelKey: `audit.result.${key}` };
  }
  return { label: formatAuditCode(normalized), color: 'default' };
}

export function isControlPlaneAuditRecord(record: Pick<AuditRecord, 'operationType'>): boolean {
  const category = getAuditOperationPresentation(record.operationType).category;
  return category === 'metadata' || category === 'proxy' || category === 'cluster';
}

export function describeAuditRecord(
  record: AuditRecord,
  translate?: (key: string) => string,
): string {
  const operationPresentation = getAuditOperationPresentation(record.operationType);
  const resourcePresentationValue = getAuditResourcePresentation(record.resourceType);
  const operation =
    translate && operationPresentation.labelKey
      ? translate(operationPresentation.labelKey)
      : operationPresentation.label;
  const resource =
    translate && resourcePresentationValue.labelKey
      ? translate(resourcePresentationValue.labelKey)
      : resourcePresentationValue.label;
  const target = record.target?.trim() || '-';
  const clusterId = record.clusterId?.trim();
  if (translate) {
    const parts = [operation, resource, target];
    if (clusterId) {
      parts.push(clusterId);
    }
    return parts.join(' · ');
  }
  return clusterId
    ? `${operation} ${resource} ${target} in ${clusterId}`
    : `${operation} ${resource} ${target}`;
}

export function parseAuditDetail(detail: string | null | undefined): AuditDetailToken[] {
  const text = detail?.trim();
  if (!text) return [];

  const parts = splitTopLevelDetailFields(text);
  if (!parts) return [{ label: '', value: text }];
  if (parts.length <= 1) return [{ label: '', value: text }];

  const tokens = parts.map((part) => {
    const separatorIndex = part.indexOf('=');
    if (separatorIndex < 1) {
      return null;
    }
    const label = part.slice(0, separatorIndex).trim();
    const value = part.slice(separatorIndex + 1).trim();
    return label && value ? { label, value } : null;
  });

  if (tokens.some((token) => token == null)) {
    return [{ label: '', value: text }];
  }
  return tokens as AuditDetailToken[];
}

const DETAIL_DELIMITERS: Record<string, string> = {
  '{': '}',
  '[': ']',
  '(': ')',
};

function splitTopLevelDetailFields(text: string): string[] | null {
  const fields: string[] = [];
  const expectedClosings: string[] = [];
  let fieldStart = 0;
  let quote = '';
  let escaped = false;

  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    if (quote) {
      if (escaped) {
        escaped = false;
      } else if (char === '\\') {
        escaped = true;
      } else if (char === quote) {
        quote = '';
      }
      continue;
    }

    if (char === '"' || char === "'") {
      quote = char;
      continue;
    }
    const closing = DETAIL_DELIMITERS[char];
    if (closing) {
      expectedClosings.push(closing);
      continue;
    }
    if (char === '}' || char === ']' || char === ')') {
      if (expectedClosings.pop() !== char) return null;
      continue;
    }
    if (char === ',' && expectedClosings.length === 0) {
      const field = text.slice(fieldStart, index).trim();
      if (field) fields.push(field);
      fieldStart = index + 1;
    }
  }

  if (quote || expectedClosings.length > 0) return null;
  const field = text.slice(fieldStart).trim();
  if (field) fields.push(field);
  return fields;
}
