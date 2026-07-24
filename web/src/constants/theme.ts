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

import type { TagProps } from 'antd';

export const THEME_COLORS = {
  primary: '#1677ff',
  success: '#52c41a',
  warning: '#faad14',
  error: '#ff4d4f',
  info: '#1677ff',
  purple: '#722ed1',
  clusterV4: '#fa8c16',
  clusterV5Local: '#1677ff',
  clusterV5Cluster: '#722ed1',
} as const;

/**
 * Cluster type map — labels are i18n keys, resolved at render time via t().
 */
export const CLUSTER_TYPE_MAP: Record<string, { labelKey: string; color: TagProps['color'] }> = {
  V4_DIRECT: { labelKey: 'theme.clusterV4', color: 'orange' },
  V5_PROXY_LOCAL: { labelKey: 'theme.clusterV5Local', color: 'blue' },
  V5_PROXY_CLUSTER: { labelKey: 'theme.clusterV5Cluster', color: 'purple' },
};

/**
 * Status map — labels are i18n keys, resolved at render time via t().
 */
export const STATUS_MAP: Record<string, { labelKey: string; color: string; dot: string }> = {
  healthy: { labelKey: 'theme.healthy', color: '#52c41a', dot: '#52c41a' },
  warning: { labelKey: 'theme.warning', color: '#faad14', dot: '#faad14' },
  error: { labelKey: 'theme.error', color: '#ff4d4f', dot: '#ff4d4f' },
  offline: { labelKey: 'theme.offline', color: '#d9d9d9', dot: '#d9d9d9' },
  connecting: { labelKey: 'theme.connecting', color: '#1677ff', dot: '#1677ff' },
};

/**
 * Topic type map — labels are i18n keys, resolved at render time via t().
 */
export const TOPIC_TYPE_MAP: Record<string, { labelKey: string; color: TagProps['color'] }> = {
  NORMAL: { labelKey: 'theme.topicNormal', color: 'default' },
  FIFO: { labelKey: 'theme.topicFifo', color: 'blue' },
  DELAY: { labelKey: 'theme.topicDelay', color: 'orange' },
  TRANSACTION: { labelKey: 'theme.topicTransaction', color: 'purple' },
  LITE: { labelKey: 'theme.topicLite', color: 'magenta' },
};

/**
 * Protocol map — labels are i18n keys, resolved at render time via t().
 */
export const PROTOCOL_MAP: Record<string, { labelKey: string; color: TagProps['color'] }> = {
  REMOTING: { labelKey: 'theme.protocolRemoting', color: 'geekblue' },
  GRPC: { labelKey: 'theme.protocolGrpc', color: 'green' },
};
