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

import { isMockMode } from './dataMode';
import * as connApi from '../api/connections';
import type { ClientConnection, ClientConnectionQuery } from '../api/connections';
import { mockClientClusterByNamesrvAddr, mockClients } from '../mock/clients';

function copyConnection(connection: ClientConnection): ClientConnection {
  return { ...connection };
}

export async function listConnections(params?: ClientConnectionQuery): Promise<ClientConnection[]> {
  if (isMockMode()) {
    const namesrvAddr = params?.namesrvAddr?.trim();
    if (!namesrvAddr) {
      throw new Error('namesrvAddr is required');
    }
    const instanceCluster = mockClientClusterByNamesrvAddr[namesrvAddr];
    if (!instanceCluster) return [];

    const clusterId = params?.clusterId?.trim();
    const type = params?.type?.trim();
    let result = mockClients.filter((connection) => connection.clusterName === instanceCluster);
    if (clusterId) result = result.filter((connection) => connection.clusterName === clusterId);
    if (type) result = result.filter((connection) => connection.type === type);
    return (result as unknown as ClientConnection[]).map(copyConnection);
  }
  return connApi.listConnections(params);
}
