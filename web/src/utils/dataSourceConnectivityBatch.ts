/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import type { DataSource } from '../api/settings';
export type DataSourceCheckStatus = 'SUCCESS' | 'FAILED' | 'SKIPPED_AUTH';
export interface DataSourceConnectivityResult {
  key: string;
  name: string;
  type: string;
  url: string;
  auth: string;
  status: DataSourceCheckStatus;
  message: string;
  latencyMs: number | null;
  instanceCount: number;
}
export interface DataSourceConnectivityBatch {
  results: DataSourceConnectivityResult[];
  summary: {
    total: number;
    tested: number;
    succeeded: number;
    failed: number;
    skippedAuth: number;
    averageLatencyMs: number | null;
    slowestLatencyMs: number | null;
  };
}
export const dataSourceNeedsRuntimeSecret = (source: Pick<DataSource, 'auth'>) =>
  source.auth === 'Basic Auth' || source.auth === 'Bearer Token';

/** 使用有界并发检查无需密钥的数据源；认证型数据源明确跳过，不发送空凭据。 */
export const checkDataSourceConnectivity = async (
  sources: DataSource[],
  test: (
    source: Pick<DataSource, 'type' | 'url' | 'auth'>,
  ) => Promise<{ success: boolean; message: string }>,
  concurrency = 3,
  clock: () => number = Date.now,
): Promise<DataSourceConnectivityBatch> => {
  const unique = [...new Map(sources.map((source) => [source.key, source])).values()];
  const skipped = unique
    .filter(dataSourceNeedsRuntimeSecret)
    .map<DataSourceConnectivityResult>((source) => ({
      key: source.key,
      name: source.name,
      type: source.type,
      url: source.url,
      auth: source.auth,
      status: 'SKIPPED_AUTH',
      message: 'Requires credentials entered in the editor',
      latencyMs: null,
      instanceCount: source.instanceIds?.length ?? 0,
    }));
  const pending = unique.filter((source) => !dataSourceNeedsRuntimeSecret(source));
  const tested: DataSourceConnectivityResult[] = [];
  let cursor = 0;
  const worker = async () => {
    while (cursor < pending.length) {
      const source = pending[cursor];
      cursor += 1;
      const started = clock();
      try {
        const response = await test({ type: source.type, url: source.url, auth: source.auth });
        tested.push({
          key: source.key,
          name: source.name,
          type: source.type,
          url: source.url,
          auth: source.auth,
          status: response.success ? 'SUCCESS' : 'FAILED',
          message: response.message,
          latencyMs: Math.max(0, clock() - started),
          instanceCount: source.instanceIds?.length ?? 0,
        });
      } catch (error) {
        tested.push({
          key: source.key,
          name: source.name,
          type: source.type,
          url: source.url,
          auth: source.auth,
          status: 'FAILED',
          message:
            error instanceof Error && error.message ? error.message : 'Connection request failed',
          latencyMs: Math.max(0, clock() - started),
          instanceCount: source.instanceIds?.length ?? 0,
        });
      }
    }
  };
  await Promise.all(
    Array.from({ length: Math.min(Math.max(1, Math.floor(concurrency)), pending.length) }, worker),
  );
  const order: Record<DataSourceCheckStatus, number> = { FAILED: 0, SKIPPED_AUTH: 1, SUCCESS: 2 };
  const results = [...tested, ...skipped].sort(
    (a, b) => order[a.status] - order[b.status] || a.name.localeCompare(b.name),
  );
  const latencies = tested
    .map((item) => item.latencyMs)
    .filter((value): value is number => value !== null);
  return {
    results,
    summary: {
      total: results.length,
      tested: tested.length,
      succeeded: tested.filter((item) => item.status === 'SUCCESS').length,
      failed: tested.filter((item) => item.status === 'FAILED').length,
      skippedAuth: skipped.length,
      averageLatencyMs: latencies.length
        ? Math.round(latencies.reduce((sum, value) => sum + value, 0) / latencies.length)
        : null,
      slowestLatencyMs: latencies.length ? Math.max(...latencies) : null,
    },
  };
};
export const filterDataSourceConnectivity = (
  results: DataSourceConnectivityResult[],
  search = '',
  status?: DataSourceCheckStatus,
) => {
  const keyword = search.trim().toLocaleLowerCase();
  return results
    .filter((result) => !status || result.status === status)
    .filter(
      (result) =>
        !keyword ||
        [result.name, result.key, result.type, result.url, result.message]
          .join('\n')
          .toLocaleLowerCase()
          .includes(keyword),
    );
};
