import client from './client';

// Matches mock/clients.ts
export interface ClientConnection {
  clientId: string;
  type: string;
  groupOrTopic: string;
  protocol: string;
  address: string;
  language: string;
  version: string;
  connectedAt?: string | null;
  partial?: boolean;
  clusterName: string;
}

export interface ClientConnectionQuery {
  instanceId: string;
  clusterId?: string;
  type?: string;
  page?: number;
  pageSize?: number;
}

export interface ClientConnectionPage {
  items: ClientConnection[];
  total: number;
  page: number;
  size: number;
}

export async function listConnections(params?: ClientConnectionQuery) {
  const res = await client.get<{ data: ClientConnectionPage }>('/clients', { params });
  return res.data.data;
}
