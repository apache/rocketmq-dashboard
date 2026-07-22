import { USE_MOCK } from '../config';
import * as connApi from '../api/connections';
import type { ClientConnection, ClientConnectionQuery } from '../api/connections';
import { mockClients } from '../mock/clients';

export async function listConnections(params?: ClientConnectionQuery): Promise<ClientConnection[]> {
  if (USE_MOCK) {
    let result = [...mockClients];
    if (params?.clusterId)
      result = result.filter((connection) => connection.clusterName === params.clusterId);
    if (params?.type) result = result.filter((c) => c.type === params.type);
    return result as unknown as ClientConnection[];
  }
  return connApi.listConnections(params);
}
