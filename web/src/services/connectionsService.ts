import { USE_MOCK } from '../config';
import * as connApi from '../api/connections';
import type { ClientConnection } from '../api/connections';
import { mockClients } from '../mock/clients';

export async function listConnections(params?: {
  keyword?: string;
  type?: string;
  language?: string;
}): Promise<ClientConnection[]> {
  if (USE_MOCK) {
    let result = [...mockClients];
    if (params?.keyword) {
      const kw = params.keyword.toLowerCase();
      result = result.filter(
        (c) => c.clientId.toLowerCase().includes(kw) || c.address.toLowerCase().includes(kw),
      );
    }
    if (params?.type && params.type !== 'all')
      result = result.filter((c) => c.type === params.type);
    if (params?.language && params.language !== 'all')
      result = result.filter((c) => c.language === params.language);
    return result as unknown as ClientConnection[];
  }
  return connApi.listConnections(params);
}
