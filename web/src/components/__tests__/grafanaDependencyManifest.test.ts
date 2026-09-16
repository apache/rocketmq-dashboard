/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import { describe, expect, it } from 'vitest';
import type { GrafanaDashboardInfo } from '../../api/metrics';
import {
  analyzeGrafanaDashboard,
  buildGrafanaDependencyManifest,
  filterGrafanaDependencyRows,
  grafanaDependencyCsvRows,
} from '../grafanaDependencyManifest';

const info: GrafanaDashboardInfo = {
  uid: 'rocketmq-overview',
  title: 'RocketMQ Overview',
  description: 'overview',
  tags: ['rocketmq'],
};

describe('Grafana dependency manifest', () => {
  it('recursively counts row and nested panels', () => {
    const row = analyzeGrafanaDashboard(info, {
      uid: info.uid,
      title: info.title,
      schemaVersion: 39,
      panels: [
        { type: 'row', panels: [{ type: 'timeseries' }, { type: 'stat' }] },
        { type: 'table' },
      ],
    });
    expect(row.panelCount).toBe(4);
    expect(row.panelTypes).toEqual(['row', 'stat', 'table', 'timeseries']);
  });

  it('collects data sources from dashboard, panel, target, and variables', () => {
    const row = analyzeGrafanaDashboard(info, {
      uid: info.uid,
      title: info.title,
      schemaVersion: 39,
      datasource: { uid: 'global-prometheus' },
      panels: [
        {
          type: 'timeseries',
          datasource: 'panel-prometheus',
          targets: [{ datasource: { uid: 'target-prometheus' } }],
        },
      ],
      templating: { list: [{ name: 'cluster', type: 'query', datasource: 'variable-prometheus' }] },
    });
    expect(row.dataSources).toEqual([
      'global-prometheus',
      'panel-prometheus',
      'target-prometheus',
      'variable-prometheus',
    ]);
    expect(row.variableNames).toEqual(['cluster']);
  });

  it('counts advanced panel dependencies', () => {
    const row = analyzeGrafanaDashboard(info, {
      uid: info.uid,
      title: info.title,
      schemaVersion: 39,
      panels: [
        {
          type: 'timeseries',
          targets: [{ refId: 'A' }, { refId: 'B' }],
          transformations: [{ id: 'join' }, { id: 'rename' }],
          libraryPanel: { uid: 'shared' },
          repeat: 'broker',
          alert: { name: 'alert' },
        },
      ],
    });
    expect(row).toMatchObject({
      targetCount: 2,
      transformationCount: 2,
      libraryPanelCount: 1,
      repeatedPanelCount: 1,
      alertCount: 1,
    });
  });

  it('reports structural portability notes', () => {
    const row = analyzeGrafanaDashboard(info, {
      panels: [{ type: 'timeseries', targets: [{ expr: 'up' }] }],
      templating: { list: [{ type: 'query' }] },
    });
    expect(row.issues).toEqual([
      'IMPLICIT_DATASOURCE',
      'MISSING_SCHEMA_VERSION',
      'MISSING_TITLE',
      'MISSING_UID',
      'UNNAMED_VARIABLE',
    ]);
  });

  it('reports a metadata and model UID mismatch', () => {
    const row = analyzeGrafanaDashboard(info, {
      uid: 'different',
      title: info.title,
      schemaVersion: 39,
      panels: [{ type: 'stat' }],
    });
    expect(row.issues).toEqual(['UID_MISMATCH']);
  });

  it('builds portfolio totals and sorts noted dashboards first', () => {
    const ready = analyzeGrafanaDashboard(info, {
      uid: info.uid,
      title: info.title,
      schemaVersion: 39,
      panels: [{ type: 'stat' }],
    });
    const empty = analyzeGrafanaDashboard(
      { ...info, uid: 'empty', title: 'Empty' },
      {
        uid: 'empty',
        title: 'Empty',
        schemaVersion: 39,
        panels: [],
      },
    );
    const manifest = buildGrafanaDependencyManifest([ready, empty]);
    expect(manifest.rows[0].uid).toBe('empty');
    expect(manifest.summary).toMatchObject({ dashboards: 2, panels: 1, dashboardsWithIssues: 1 });
  });

  it('deduplicates shared data sources in portfolio totals', () => {
    const first = analyzeGrafanaDashboard(info, {
      uid: info.uid,
      title: info.title,
      schemaVersion: 39,
      datasource: 'prometheus',
      panels: [{ type: 'stat' }],
    });
    const second = analyzeGrafanaDashboard(
      { ...info, uid: 'two' },
      {
        uid: 'two',
        title: 'Two',
        schemaVersion: 39,
        datasource: 'prometheus',
        panels: [{ type: 'table' }],
      },
    );
    expect(buildGrafanaDependencyManifest([first, second]).summary.dataSources).toBe(1);
  });

  it('filters across title, UID, data source, panel type, and variable', () => {
    const row = analyzeGrafanaDashboard(info, {
      uid: info.uid,
      title: info.title,
      schemaVersion: 39,
      datasource: 'prometheus-prod',
      panels: [{ type: 'heatmap' }],
      templating: { list: [{ name: 'BrokerName', type: 'query' }] },
    });
    for (const term of ['overview', 'rocketmq-overview', 'PROMETHEUS', 'heatmap', 'brokername']) {
      expect(filterGrafanaDependencyRows([row], term, false)).toHaveLength(1);
    }
    expect(filterGrafanaDependencyRows([row], '', true)).toHaveLength(0);
  });

  it('creates deterministic flat CSV rows', () => {
    const row = analyzeGrafanaDashboard(info, {
      uid: info.uid,
      title: info.title,
      schemaVersion: 39,
      datasource: 'prometheus',
      panels: [{ type: 'stat' }],
    });
    expect(grafanaDependencyCsvRows([row])[0]).toMatchObject({
      uid: info.uid,
      schemaVersion: 39,
      panels: 1,
      panelTypes: 'stat',
      dataSources: 'prometheus',
    });
  });
});
