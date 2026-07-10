/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.
 */

import client from './client';

// ─── Types ──────────────────────────────────────────────────────
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

// ─── Client API ─────────────────────────────────────────────────
// Backend: ClientController at /api/client
// Note: This controller already uses /api prefix, so with baseURL '/api'
// and proxy rewrite stripping /api, the path becomes /api/client/list
// which is correct: frontend /api/api/client/list → proxy → /api/client/list
// To avoid double /api, we use the full path directly without baseURL prefix.

export async function listConnections(params?: {
  keyword?: string;
  type?: string;
  language?: string;
}) {
  // ClientController is at /api/client — use absolute path to avoid baseURL
  const res = await client.get('/api/client/list', {
    params: {
      topic: params?.keyword,
      group: params?.type === 'consumer' ? params.keyword : undefined,
    },
  });
  return res.data;
}

export async function getClientDetail(clientId: string) {
  const res = await client.get(`/api/client/${clientId}`);
  return res.data;
}

export async function getClientSubscriptions(clientId: string) {
  const res = await client.get(`/api/client/${clientId}/subscriptions`);
  return res.data;
}
