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

export interface AclRule {
  id: string;
  principal: string;
  resource: string;
  resourceType: 'Topic' | 'Group' | 'Cluster';
  resourcePattern: 'LITERAL' | 'PREFIX';
  actions: ('PUB' | 'SUB' | 'ALL')[];
  decision: 'ALLOW' | 'DENY';
  scope: 'cluster' | 'namespace';
  aclVersion: '1.0' | '2.0';
  createdAt: string;
}

export interface AclUser {
  id: string;
  username: string;
  accessKey: string;
  secretKey: string;
  admin: boolean;
  clusters: string[];
  createdAt: string;
}

export const aclRules: AclRule[] = [
  {
    id: 'acl-001',
    principal: 'user-order-service',
    resource: 'order-*',
    resourceType: 'Topic',
    resourcePattern: 'PREFIX',
    actions: ['PUB', 'SUB'],
    decision: 'ALLOW',
    scope: 'cluster',
    aclVersion: '2.0',
    createdAt: '2025-08-12T10:30:00Z',
  },
  {
    id: 'acl-002',
    principal: 'user-payment-service',
    resource: 'payment-*',
    resourceType: 'Topic',
    resourcePattern: 'PREFIX',
    actions: ['PUB', 'SUB'],
    decision: 'ALLOW',
    scope: 'cluster',
    aclVersion: '2.0',
    createdAt: '2025-09-05T14:20:00Z',
  },
  {
    id: 'acl-003',
    principal: 'user-admin',
    resource: '*',
    resourceType: 'Cluster',
    resourcePattern: 'LITERAL',
    actions: ['ALL'],
    decision: 'ALLOW',
    scope: 'cluster',
    aclVersion: '2.0',
    createdAt: '2025-03-01T08:00:00Z',
  },
  {
    id: 'acl-004',
    principal: 'user-log-collector',
    resource: 'system-log',
    resourceType: 'Topic',
    resourcePattern: 'LITERAL',
    actions: ['SUB'],
    decision: 'ALLOW',
    scope: 'namespace',
    aclVersion: '1.0',
    createdAt: '2025-06-20T16:45:00Z',
  },
  {
    id: 'acl-005',
    principal: 'user-order-service',
    resource: 'cg-order-*',
    resourceType: 'Group',
    resourcePattern: 'PREFIX',
    actions: ['SUB'],
    decision: 'ALLOW',
    scope: 'cluster',
    aclVersion: '2.0',
    createdAt: '2025-08-15T11:00:00Z',
  },
  {
    id: 'acl-006',
    principal: 'user-inventory-service',
    resource: 'inventory-sync',
    resourceType: 'Topic',
    resourcePattern: 'LITERAL',
    actions: ['PUB', 'SUB'],
    decision: 'ALLOW',
    scope: 'cluster',
    aclVersion: '2.0',
    createdAt: '2025-10-10T09:30:00Z',
  },
  {
    id: 'acl-007',
    principal: 'user-guest',
    resource: 'payment-callback',
    resourceType: 'Topic',
    resourcePattern: 'LITERAL',
    actions: ['PUB', 'SUB'],
    decision: 'DENY',
    scope: 'cluster',
    aclVersion: '1.0',
    createdAt: '2025-11-02T13:15:00Z',
  },
  {
    id: 'acl-008',
    principal: 'user-notification-service',
    resource: 'notification-*',
    resourceType: 'Topic',
    resourcePattern: 'PREFIX',
    actions: ['PUB'],
    decision: 'ALLOW',
    scope: 'namespace',
    aclVersion: '2.0',
    createdAt: '2025-12-18T10:00:00Z',
  },
  {
    id: 'acl-009',
    principal: 'user-risk-control',
    resource: 'order-create',
    resourceType: 'Topic',
    resourcePattern: 'LITERAL',
    actions: ['SUB'],
    decision: 'ALLOW',
    scope: 'cluster',
    aclVersion: '1.0',
    createdAt: '2026-01-08T15:30:00Z',
  },
  {
    id: 'acl-010',
    principal: 'user-guest',
    resource: '*',
    resourceType: 'Cluster',
    resourcePattern: 'LITERAL',
    actions: ['PUB'],
    decision: 'DENY',
    scope: 'cluster',
    aclVersion: '2.0',
    createdAt: '2026-02-14T08:45:00Z',
  },
  {
    id: 'acl-011',
    principal: 'user-payment-service',
    resource: 'cg-payment-*',
    resourceType: 'Group',
    resourcePattern: 'PREFIX',
    actions: ['SUB'],
    decision: 'ALLOW',
    scope: 'cluster',
    aclVersion: '2.0',
    createdAt: '2026-03-22T11:20:00Z',
  },
  {
    id: 'acl-012',
    principal: 'user-monitor',
    resource: 'user-activity-log',
    resourceType: 'Topic',
    resourcePattern: 'LITERAL',
    actions: ['SUB'],
    decision: 'ALLOW',
    scope: 'namespace',
    aclVersion: '1.0',
    createdAt: '2026-04-15T09:10:00Z',
  },
  {
    id: 'acl-013',
    principal: 'user-ai-service',
    resource: 'chat/sess-*',
    resourceType: 'Topic',
    resourcePattern: 'PREFIX',
    actions: ['PUB', 'SUB'],
    decision: 'ALLOW',
    scope: 'namespace',
    aclVersion: '2.0',
    createdAt: '2026-05-20T14:00:00Z',
  },
];

export const aclUsers: AclUser[] = [
  {
    id: 'u-001',
    username: 'user-admin',
    accessKey: 'LTAI****admin',
    secretKey: 'HqWz****xK8P',
    admin: true,
    clusters: ['rmq-cn-v5-prod-01', 'rmq-cn-v5-prod-02', 'rmq-cn-v4-prod-02'],
    createdAt: '2025-01-10T08:00:00Z',
  },
  {
    id: 'u-002',
    username: 'user-order-service',
    accessKey: 'LTAI****ordr',
    secretKey: 'MnBv****7dF3',
    admin: false,
    clusters: ['rmq-cn-v5-prod-01'],
    createdAt: '2025-03-22T10:30:00Z',
  },
  {
    id: 'u-003',
    username: 'user-payment-service',
    accessKey: 'LTAI****paym',
    secretKey: 'XcZa****9pQ2',
    admin: false,
    clusters: ['rmq-cn-v5-prod-01', 'rmq-cn-v5-prod-02'],
    createdAt: '2025-04-15T14:20:00Z',
  },
  {
    id: 'u-004',
    username: 'user-log-collector',
    accessKey: 'LTAI****logs',
    secretKey: 'RtYu****3hJ5',
    admin: false,
    clusters: ['rmq-cn-v4-prod-02'],
    createdAt: '2025-06-01T09:00:00Z',
  },
  {
    id: 'u-005',
    username: 'user-guest',
    accessKey: 'LTAI****guest',
    secretKey: 'KpLm****6wN8',
    admin: false,
    clusters: ['rmq-cn-v5-prod-01'],
    createdAt: '2025-07-18T16:00:00Z',
  },
  {
    id: 'u-006',
    username: 'user-inventory-service',
    accessKey: 'LTAI****invn',
    secretKey: 'GhJk****4tY1',
    admin: false,
    clusters: ['rmq-cn-v5-prod-01', 'rmq-cn-v5-prod-02'],
    createdAt: '2025-09-10T11:30:00Z',
  },
  {
    id: 'u-007',
    username: 'user-notification-service',
    accessKey: 'LTAI****ntfy',
    secretKey: 'BvCx****8mZ6',
    admin: false,
    clusters: ['rmq-cn-v5-prod-01'],
    createdAt: '2025-11-05T13:45:00Z',
  },
  {
    id: 'u-008',
    username: 'user-monitor',
    accessKey: 'LTAI****monr',
    secretKey: 'WqEr****2fA4',
    admin: false,
    clusters: ['rmq-cn-v5-prod-01', 'rmq-cn-v5-prod-02', 'rmq-cn-v4-prod-02'],
    createdAt: '2026-01-20T10:00:00Z',
  },
];
