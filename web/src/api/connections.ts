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
  connectedAt: string;
  clusterName: string;
}

export async function listConnections(params?: {
  keyword?: string;
  type?: string;
  language?: string;
}) {
  const res = await client.get<{ data: ClientConnection[] }>('/clients', { params });
  return res.data.data;
}
