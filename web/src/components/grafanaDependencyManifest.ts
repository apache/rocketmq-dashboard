/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import type { GrafanaDashboardInfo } from '../api/metrics';

export interface GrafanaDependencyRow {
  uid: string;
  title: string;
  schemaVersion: number | null;
  panelCount: number;
  panelTypes: string[];
  dataSources: string[];
  variableNames: string[];
  variableTypes: string[];
  targetCount: number;
  transformationCount: number;
  libraryPanelCount: number;
  repeatedPanelCount: number;
  alertCount: number;
  issues: string[];
}
export interface GrafanaDependencyManifest {
  rows: GrafanaDependencyRow[];
  summary: {
    dashboards: number;
    panels: number;
    targets: number;
    dataSources: number;
    variables: number;
    libraryPanels: number;
    dashboardsWithIssues: number;
  };
}
const objectValue = (value: unknown): Record<string, unknown> | null =>
  value !== null && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
const stringValue = (value: unknown) =>
  typeof value === 'string' && value.trim() ? value.trim() : null;
const dataSourceName = (value: unknown): string | null => {
  const direct = stringValue(value);
  if (direct) return direct;
  const object = objectValue(value);
  return stringValue(object?.uid) || stringValue(object?.type);
};
const uniqueSorted = (values: Array<string | null>) =>
  [...new Set(values.filter((value): value is string => Boolean(value)))].sort((a, b) =>
    a.localeCompare(b),
  );
const flattenPanels = (model: Record<string, unknown>) => {
  const result: Record<string, unknown>[] = [];
  const visit = (candidate: unknown) => {
    if (!Array.isArray(candidate)) return;
    candidate.forEach((item) => {
      const panel = objectValue(item);
      if (!panel) return;
      result.push(panel);
      visit(panel.panels);
    });
  };
  visit(model.panels);
  return result;
};

/** 递归分析 Dashboard 模型；保留未知字段，且不修改原始 JSON。 */
export const analyzeGrafanaDashboard = (
  info: GrafanaDashboardInfo,
  model: Record<string, unknown>,
): GrafanaDependencyRow => {
  const panels = flattenPanels(model);
  const templating = objectValue(model.templating);
  const variables = Array.isArray(templating?.list)
    ? templating.list
        .map(objectValue)
        .filter((value): value is Record<string, unknown> => Boolean(value))
    : [];
  const targets = panels.flatMap((panel) =>
    Array.isArray(panel.targets)
      ? panel.targets
          .map(objectValue)
          .filter((value): value is Record<string, unknown> => Boolean(value))
      : [],
  );
  const dataSources = uniqueSorted([
    dataSourceName(model.datasource),
    ...panels.map((panel) => dataSourceName(panel.datasource)),
    ...targets.map((target) => dataSourceName(target.datasource)),
    ...variables.map((variable) => dataSourceName(variable.datasource)),
  ]);
  const schemaVersion =
    typeof model.schemaVersion === 'number' && Number.isFinite(model.schemaVersion)
      ? model.schemaVersion
      : null;
  const modelUid = stringValue(model.uid);
  const issues: string[] = [];
  if (!modelUid) issues.push('MISSING_UID');
  else if (modelUid !== info.uid) issues.push('UID_MISMATCH');
  if (!stringValue(model.title)) issues.push('MISSING_TITLE');
  if (schemaVersion === null) issues.push('MISSING_SCHEMA_VERSION');
  if (panels.length === 0) issues.push('NO_PANELS');
  if (dataSources.length === 0 && targets.length > 0) issues.push('IMPLICIT_DATASOURCE');
  if (variables.some((variable) => !stringValue(variable.name))) issues.push('UNNAMED_VARIABLE');
  return {
    uid: info.uid,
    title: info.title,
    schemaVersion,
    panelCount: panels.length,
    panelTypes: uniqueSorted(panels.map((panel) => stringValue(panel.type))),
    dataSources,
    variableNames: uniqueSorted(variables.map((variable) => stringValue(variable.name))),
    variableTypes: uniqueSorted(variables.map((variable) => stringValue(variable.type))),
    targetCount: targets.length,
    transformationCount: panels.reduce(
      (count, panel) =>
        count + (Array.isArray(panel.transformations) ? panel.transformations.length : 0),
      0,
    ),
    libraryPanelCount: panels.filter((panel) => Boolean(objectValue(panel.libraryPanel))).length,
    repeatedPanelCount: panels.filter((panel) => Boolean(stringValue(panel.repeat))).length,
    alertCount: panels.filter((panel) => Boolean(objectValue(panel.alert))).length,
    issues: uniqueSorted(issues),
  };
};

export const buildGrafanaDependencyManifest = (
  rows: GrafanaDependencyRow[],
): GrafanaDependencyManifest => {
  const sorted = [...rows].sort(
    (a, b) => b.issues.length - a.issues.length || a.title.localeCompare(b.title),
  );
  return {
    rows: sorted,
    summary: {
      dashboards: sorted.length,
      panels: sorted.reduce((sum, row) => sum + row.panelCount, 0),
      targets: sorted.reduce((sum, row) => sum + row.targetCount, 0),
      dataSources: new Set(sorted.flatMap((row) => row.dataSources)).size,
      variables: sorted.reduce((sum, row) => sum + row.variableNames.length, 0),
      libraryPanels: sorted.reduce((sum, row) => sum + row.libraryPanelCount, 0),
      dashboardsWithIssues: sorted.filter((row) => row.issues.length > 0).length,
    },
  };
};
export const filterGrafanaDependencyRows = (
  rows: GrafanaDependencyRow[],
  search: string,
  issuesOnly: boolean,
) => {
  const keyword = search.trim().toLocaleLowerCase();
  return rows
    .filter((row) => !issuesOnly || row.issues.length > 0)
    .filter(
      (row) =>
        !keyword ||
        [row.uid, row.title, ...row.panelTypes, ...row.dataSources, ...row.variableNames]
          .join('\n')
          .toLocaleLowerCase()
          .includes(keyword),
    );
};
export const grafanaDependencyCsvRows = (rows: GrafanaDependencyRow[]) =>
  rows.map((row) => ({
    uid: row.uid,
    title: row.title,
    schemaVersion: row.schemaVersion ?? '',
    panels: row.panelCount,
    panelTypes: row.panelTypes.join('; '),
    dataSources: row.dataSources.join('; '),
    variables: row.variableNames.join('; '),
    variableTypes: row.variableTypes.join('; '),
    targets: row.targetCount,
    transformations: row.transformationCount,
    libraryPanels: row.libraryPanelCount,
    repeatedPanels: row.repeatedPanelCount,
    alerts: row.alertCount,
    issues: row.issues.join('; '),
  }));
