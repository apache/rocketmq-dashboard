import { isMockMode } from './dataMode';
import * as connApi from '../api/connections';
import type { ClientConnection, ClientConnectionPage, ClientConnectionQuery } from '../api/connections';
import { mockClients } from '../mock/clients';

function copyConnection(connection: ClientConnection): ClientConnection {
  return { ...connection };
}

export async function listConnections(
  params: ClientConnectionQuery,
): Promise<ClientConnectionPage> {
  if (isMockMode()) {
    let result = [...mockClients];
    if (params?.clusterId)
      result = result.filter((connection) => connection.clusterName === params.clusterId);
    if (params?.type) result = result.filter((c) => c.type === params.type);
    const page = params.page ?? 1;
    const pageSize = params.pageSize ?? 20;
    const start = (page - 1) * pageSize;
    return {
      items: (result as unknown as ClientConnection[])
        .sort((left, right) =>
          `${left.clusterName}\0${left.type}\0${left.groupOrTopic}\0${left.clientId}\0${left.address}`.localeCompare(
            `${right.clusterName}\0${right.type}\0${right.groupOrTopic}\0${right.clientId}\0${right.address}`,
          ),
        )
        .slice(start, start + pageSize)
        .map(copyConnection),
      total: result.length,
      page,
      size: pageSize,
    };
  }
  return connApi.listConnections(params);
}
