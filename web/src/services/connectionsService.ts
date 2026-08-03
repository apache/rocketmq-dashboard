import { isMockMode } from './dataMode';
import * as connApi from '../api/connections';
import type { ClientConnection, ClientConnectionQuery } from '../api/connections';
import { mockClients } from '../mock/clients';

function copyConnection(connection: ClientConnection): ClientConnection {
  return { ...connection };
}

export async function listConnections(params?: ClientConnectionQuery): Promise<ClientConnection[]> {
  if (isMockMode()) {
    let result = [...mockClients];
    if (params?.clusterId)
      result = result.filter((connection) => connection.clusterName === params.clusterId);
    if (params?.type) result = result.filter((c) => c.type === params.type);
    return (result as unknown as ClientConnection[]).map(copyConnection);
  }
  return connApi.listConnections(params);
}
