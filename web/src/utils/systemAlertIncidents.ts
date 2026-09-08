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

import type { SystemAlert } from '../api/ops';

export type IncidentStatus = 'ACTIVE' | 'RESOLVED' | 'UNKNOWN';

export interface SystemAlertIncident {
  key: string;
  correlationSource: 'FINGERPRINT' | 'RULE_SCOPE' | 'ISOLATED';
  title: string;
  instanceId: string;
  domain: string;
  level: string;
  status: IncidentStatus;
  eventCount: number;
  firingCount: number;
  resolvedCount: number;
  suppressedCount: number;
  acknowledgedCount: number;
  firstSeen: string;
  lastSeen: string;
  durationMs: number | null;
  ruleIds: number[];
  alerts: SystemAlert[];
}

export interface SystemAlertIncidentAnalysis {
  incidents: SystemAlertIncident[];
  summary: {
    incidents: number;
    active: number;
    resolved: number;
    unknown: number;
    suppressedEvents: number;
    unacknowledgedEvents: number;
    longestDurationMs: number | null;
  };
}

export interface IncidentFilters {
  search: string;
  status: IncidentStatus | 'ALL';
  domain: 'BUSINESS' | 'CLUSTER' | 'ALL';
  level: string | 'ALL';
  unacknowledgedOnly: boolean;
}

const parsedTime = (value: string): number | null => {
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : null;
};

const labelsKey = (labels?: Record<string, string>) =>
  Object.entries(labels ?? {})
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, value]) => `${key}=${value}`)
    .join(',');

export const systemAlertCorrelationKey = (
  alert: SystemAlert,
): {
  key: string;
  source: SystemAlertIncident['correlationSource'];
} => {
  if (alert.fingerprint?.trim()) {
    return { key: `fingerprint:${alert.fingerprint.trim()}`, source: 'FINGERPRINT' };
  }
  if (alert.ruleId != null) {
    return {
      key: `rule:${alert.domain ?? '*'}:${alert.ruleId}:${alert.instanceId ?? '*'}:${labelsKey(alert.labels)}`,
      source: 'RULE_SCOPE',
    };
  }
  return { key: `alert:${alert.id}`, source: 'ISOLATED' };
};

const levelRank = (level: string) => {
  const normalized = level.toLocaleLowerCase();
  if (normalized === 'error' || normalized === 'critical') return 3;
  if (normalized === 'warning' || normalized === 'warn') return 2;
  return 1;
};

const incidentFromAlerts = (
  key: string,
  source: SystemAlertIncident['correlationSource'],
  alerts: SystemAlert[],
): SystemAlertIncident => {
  const ordered = [...alerts].sort((left, right) => {
    const leftTime = parsedTime(left.time) ?? Number.MAX_SAFE_INTEGER;
    const rightTime = parsedTime(right.time) ?? Number.MAX_SAFE_INTEGER;
    return leftTime - rightTime || left.id - right.id;
  });
  const first = ordered[0]!;
  const last = ordered[ordered.length - 1]!;
  const firstMs = parsedTime(first.time);
  const lastMs = parsedTime(last.time);
  const status: IncidentStatus =
    last.transition === 'FIRING'
      ? 'ACTIVE'
      : last.transition === 'RESOLVED'
        ? 'RESOLVED'
        : 'UNKNOWN';
  const level = ordered.reduce(
    (highest, alert) => (levelRank(alert.level) > levelRank(highest) ? alert.level : highest),
    first.level,
  );
  return {
    key,
    correlationSource: source,
    title: first.title,
    instanceId: first.instanceId ?? '',
    domain: first.domain ?? '',
    level,
    status,
    eventCount: ordered.length,
    firingCount: ordered.filter((alert) => alert.transition === 'FIRING').length,
    resolvedCount: ordered.filter((alert) => alert.transition === 'RESOLVED').length,
    suppressedCount: ordered.filter((alert) => alert.notificationSuppressed === true).length,
    acknowledgedCount: ordered.filter((alert) => alert.acknowledged).length,
    firstSeen: first.time,
    lastSeen: last.time,
    durationMs: firstMs === null || lastMs === null || lastMs < firstMs ? null : lastMs - firstMs,
    ruleIds: [
      ...new Set(ordered.flatMap((alert) => (alert.ruleId == null ? [] : [alert.ruleId]))),
    ].sort((left, right) => left - right),
    alerts: ordered,
  };
};

export const analyzeSystemAlertIncidents = (alerts: SystemAlert[]): SystemAlertIncidentAnalysis => {
  const groups = new Map<
    string,
    { source: SystemAlertIncident['correlationSource']; alerts: SystemAlert[] }
  >();
  alerts.forEach((alert) => {
    const correlation = systemAlertCorrelationKey(alert);
    const current = groups.get(correlation.key);
    groups.set(correlation.key, {
      source: correlation.source,
      alerts: [...(current?.alerts ?? []), alert],
    });
  });
  const incidents = [...groups.entries()]
    .map(([key, group]) => incidentFromAlerts(key, group.source, group.alerts))
    .sort((left, right) => {
      const active = Number(right.status === 'ACTIVE') - Number(left.status === 'ACTIVE');
      return active !== 0 ? active : right.lastSeen.localeCompare(left.lastSeen);
    });
  const durations = incidents.flatMap((incident) =>
    incident.durationMs === null ? [] : [incident.durationMs],
  );
  return {
    incidents,
    summary: {
      incidents: incidents.length,
      active: incidents.filter((incident) => incident.status === 'ACTIVE').length,
      resolved: incidents.filter((incident) => incident.status === 'RESOLVED').length,
      unknown: incidents.filter((incident) => incident.status === 'UNKNOWN').length,
      suppressedEvents: alerts.filter((alert) => alert.notificationSuppressed === true).length,
      unacknowledgedEvents: alerts.filter((alert) => !alert.acknowledged).length,
      longestDurationMs: durations.length === 0 ? null : Math.max(...durations),
    },
  };
};

export const filterSystemAlertIncidents = (
  incidents: SystemAlertIncident[],
  filters: IncidentFilters,
): SystemAlertIncident[] => {
  const search = filters.search.trim().toLocaleLowerCase();
  return incidents.filter(
    (incident) =>
      (filters.status === 'ALL' || incident.status === filters.status) &&
      (filters.domain === 'ALL' || incident.domain === filters.domain) &&
      (filters.level === 'ALL' ||
        incident.level.toLocaleLowerCase() === filters.level.toLocaleLowerCase()) &&
      (!filters.unacknowledgedOnly || incident.acknowledgedCount < incident.eventCount) &&
      (!search ||
        [incident.title, incident.instanceId, incident.key, incident.ruleIds.join(',')].some(
          (value) => value.toLocaleLowerCase().includes(search),
        )),
  );
};
