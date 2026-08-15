import { isMockMode } from './dataMode';
import * as connApi from '../api/connections';
import type { ClientConnection, ClientConnectionQuery } from '../api/connections';
import { mockClientClusterByInstance, mockClients } from '../mock/clients';

function copyConnection(connection: ClientConnection): ClientConnection {
  return { ...connection };
}

export async function listConnections(params?: ClientConnectionQuery): Promise<ClientConnection[]> {
  if (isMockMode()) {
    const instanceId = params?.instanceId?.trim();
    if (!instanceId) {
      throw new Error('instanceId is required');
    }
    const instanceCluster = mockClientClusterByInstance[instanceId];
    if (!instanceCluster) return [];

    let result = mockClients.filter((connection) => connection.clusterName === instanceCluster);
    if (params?.clusterId)
      result = result.filter((connection) => connection.clusterName === params.clusterId);
    if (params?.type) result = result.filter((c) => c.type === params.type);
    return (result as unknown as ClientConnection[]).map(copyConnection);
  }
  return connApi.listConnections(params);
}
