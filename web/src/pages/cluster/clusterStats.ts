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

import type { ClusterInfo } from '../../api/cluster';

export interface ClusterComponentCounts {
  brokers: number;
  nameServers: number;
  proxies: number;
}

/**
 * Aggregate per-component counts across a list of clusters.
 *
 * The backend may serialize a cluster without its runtime component lists
 * (e.g. a real cluster without a proxy), leaving `brokers`/`proxies`/
 * `nameServers` as null. Guard every field so the summary never crashes on
 * such payloads.
 */
export function countClusterComponents(
  clusters: readonly (ClusterInfo | null | undefined)[],
): ClusterComponentCounts {
  return clusters.reduce(
    (acc, cluster) => ({
      brokers: acc.brokers + (cluster?.brokers ?? []).length,
      nameServers: acc.nameServers + (cluster?.nameServers ?? []).length,
      proxies: acc.proxies + (cluster?.proxies ?? []).length,
    }),
    { brokers: 0, nameServers: 0, proxies: 0 },
  );
}
