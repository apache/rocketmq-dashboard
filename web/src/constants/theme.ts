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

export const CLUSTER_TYPE_MAP: Record<string, { label: string; color: TagProps['color'] }> = {
  V4_DIRECT: { label: 'V4 直连', color: 'orange' },
  V5_PROXY_LOCAL: { label: 'V5 Proxy 单节点', color: 'blue' },
  V5_PROXY_CLUSTER: { label: 'V5 Proxy 集群', color: 'purple' },
};

export const STATUS_MAP: Record<string, { label: string; color: string; dot: string }> = {
  healthy: { label: '运行中', color: '#52c41a', dot: '#52c41a' },
  warning: { label: '告警', color: '#faad14', dot: '#faad14' },
  error: { label: '异常', color: '#ff4d4f', dot: '#ff4d4f' },
  offline: { label: '离线', color: '#d9d9d9', dot: '#d9d9d9' },
  connecting: { label: '连接中', color: '#1677ff', dot: '#1677ff' },
};

export const TOPIC_TYPE_MAP: Record<string, { label: string; color: TagProps['color'] }> = {
  NORMAL: { label: '普通', color: 'default' },
  FIFO: { label: '顺序', color: 'blue' },
  DELAY: { label: '延迟', color: 'orange' },
  TRANSACTION: { label: '事务', color: 'purple' },
  LITE: { label: 'LiteTopic', color: 'magenta' },
};

export const PROTOCOL_MAP: Record<string, { label: string; color: TagProps['color'] }> = {
  REMOTING: { label: 'Remoting', color: 'geekblue' },
  GRPC: { label: 'gRPC', color: 'green' },
};
