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
