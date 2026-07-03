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

import React from 'react';
import { Card, Button, Space, Divider, Typography } from 'antd';
import { useClusterCapabilities } from '../../store/context/ClusterCapabilitiesContext';
import ThreeTierArchitecture from '../../components/architecture/ThreeTierArchitecture';
import CapabilityAwareTopic from '../../components/topic/CapabilityAwareTopic';

const { Title, Paragraph } = Typography;

/**
 * Demo page showcasing RIP-1 UI重构功能
 * 展示能力集驱动UI和三层抽象架构的实现效果
 */
const CapabilityDemo = () => {
  const { capabilities, selectedCluster } = useClusterCapabilities();
  
  // Mock translation function for demo
  const t = (key) => {
    const translations = {
      'RIP_1_DEMO': 'RIP-1 UI重构演示',
      'CAPABILITY_DRIVEN_UI': '能力集驱动UI',
      'THREE_TIER_ARCHITECTURE': '三层抽象架构前端适配',
      'CLUSTER_INFO': '集群信息',
      'CURRENT_CLUSTER': '当前集群',
      'ARCHITECTURE_VERSION': '架构版本',
      'SUPPORTED_FEATURES': '支持特性',
      'INTERACTIVE_DEMO': '交互演示',
      'VIEW_TIER_ARCHITECTURE': '查看三层架构',
      'TEST_TOPIC_FILTERS': '测试Topic过滤器',
      'FEATURE_MATRIX': '能力矩阵',
      'V5_ARCHITECTURE': 'RocketMQ 5.0架构',
      'NAMESPACE_SUPPORT': '命名空间支持',
      'LITE_TOPIC_SUPPORT': 'Lite Topic支持',
      'GRPC_SUPPORT': 'gRPC协议支持',
      'ACL_2_0_SUPPORT': 'ACL 2.0支持',
      'POP_CONSUMPTION': 'Pop消费模式',
      'REMOTING_PROTOCOL': 'Remoting协议',
      'PROXY_LOCAL': 'Proxy本地模式',
      'PROXY_CLUSTER': 'Proxy集群模式',
      'ACCESS_TYPES': '访问类型',
      'YES': '是',
      'NO': '否'
    };
    return translations[key] || key;
  };
  
  return (
    <div className="capability-demo">
      <Title level={2}>{t('RIP_1_DEMO')}</Title>
      
      <Paragraph>
        此页面展示了RIP-1（RocketMQ 5.0统一控制面）的UI重构成果，包括能力集驱动的UI适配和三层抽象架构的前端实现。
      </Paragraph>
      
      <Divider />
      
      {/* 集群信息卡片 */}
      <Card 
        title={t('CLUSTER_INFO')} 
        style={{ marginBottom: 20 }}
        extra={
          <Button type="primary" onClick={() => window.location.reload()}>
            刷新能力信息
          </Button>
        }
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <div>
            <strong>{t('CURRENT_CLUSTER')}:</strong> 
            {selectedCluster || '未选择'}
          </div>
          
          <div>
            <strong>{t('ARCHITECTURE_VERSION')}:</strong>
            {capabilities?.isV5Architecture ? ' RocketMQ 5.0' : ' RocketMQ 4.0'}
          </div>
          
          <div>
            <strong>{t('ACCESS_TYPES')}:</strong>
            {capabilities?.accessType === 'v4-namesrv' && t('REMOTING_PROTOCOL')}
            {capabilities?.accessType === 'v5-proxy-local' && t('PROXY_LOCAL')}
            {capabilities?.accessType === 'v5-proxy-cluster' && t('PROXY_CLUSTER')}
          </div>
          
          <div>
            <strong>{t('SUPPORTED_FEATURES')}:</strong>
            <Space wrap style={{ marginTop: 8 }}>
              {capabilities?.hasNamespace && <span>✓ {t('NAMESPACE_SUPPORT')}</span>}
              {capabilities?.supportsLiteTopic && <span>✓ {t('LITE_TOPIC_SUPPORT')}</span>}
              {capabilities?.supportsGrpc && <span>✓ {t('GRPC_SUPPORT')}</span>}
              {capabilities?.supportsAcl2 && <span>✓ {t('ACL_2_0_SUPPORT')}</span>}
              {capabilities?.supportsPopConsumption && <span>✓ {t('POP_CONSUMPTION')}</span>}
            </Space>
          </div>
        </Space>
      </Card>
      
      {/* 能力矩阵 */}
      <Card title={t('FEATURE_MATRIX')} style={{ marginBottom: 20 }}>
        <div className="feature-matrix">
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr>
                <th style={{ border: '1px solid #ddd', padding: '8px' }}>特性</th>
                <th style={{ border: '1px solid #ddd', padding: '8px' }}>当前支持</th>
                <th style={{ border: '1px solid #ddd', padding: '8px' }}>描述</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td style={{ border: '1px solid #ddd', padding: '8px' }}>{t('V5_ARCHITECTURE')}</td>
                <td style={{ border: '1px solid #ddd', padding: '8px', color: capabilities?.isV5Architecture ? 'green' : 'red' }}>
                  {capabilities?.isV5Architecture ? t('YES') : t('NO')}
                </td>
                <td style={{ border: '1px solid #ddd', padding: '8px' }}>统一控制面和增强的元数据管理</td>
              </tr>
              <tr>
                <td style={{ border: '1px solid #ddd', padding: '8px' }}>{t('NAMESPACE_SUPPORT')}</td>
                <td style={{ border: '1px solid #ddd', padding: '8px', color: capabilities?.hasNamespace ? 'green' : 'red' }}>
                  {capabilities?.hasNamespace ? t('YES') : t('NO')}
                </td>
                <td style={{ border: '1px solid #ddd', padding: '8px' }}>多租户和资源隔离</td>
              </tr>
              <tr>
                <td style={{ border: '1px solid #ddd', padding: '8px' }}>{t('LITE_TOPIC_SUPPORT')}</td>
                <td style={{ border: '1px solid #ddd', padding: '8px', color: capabilities?.supportsLiteTopic ? 'green' : 'red' }}>
                  {capabilities?.supportsLiteTopic ? t('YES') : t('NO')}
                </td>
                <td style={{ border: '1px solid #ddd', padding: '8px' }}>轻量级Topic和高效消费</td>
              </tr>
              <tr>
                <td style={{ border: '1px solid #ddd', padding: '8px' }}>{t('GRPC_SUPPORT')}</td>
                <td style={{ border: '1px solid #ddd', padding: '8px', color: capabilities?.supportsGrpc ? 'green' : 'red' }}>
                  {capabilities?.supportsGrpc ? t('YES') : t('NO')}
                </td>
                <td style={{ border: '1px solid #ddd', padding: '8px' }}>高性能gRPC协议通信</td>
              </tr>
              <tr>
                <td style={{ border: '1px solid #ddd', padding: '8px' }}>{t('ACL_2_0_SUPPORT')}</td>
                <td style={{ border: '1px solid #ddd', padding: '8px', color: capabilities?.supportsAcl2 ? 'green' : 'red' }}>
                  {capabilities?.supportsAcl2 ? t('YES') : t('NO')}
                </td>
                <td style={{ border: '1px solid #ddd', padding: '8px' }}>增强的访问控制和权限管理</td>
              </tr>
              <tr>
                <td style={{ border: '1px solid #ddd', padding: '8px' }}>{t('POP_CONSUMPTION')}</td>
                <td style={{ border: '1px solid #ddd', padding: '8px', color: capabilities?.supportsPopConsumption ? 'green' : 'red' }}>
                  {capabilities?.supportsPopConsumption ? t('YES') : t('NO')}
                </td>
                <td style={{ border: '1px solid #ddd', padding: '8px' }}>无状态消费和高效消息获取</td>
              </tr>
            </tbody>
          </table>
        </div>
      </Card>
      
      {/* 功能演示 */}
      <Card title={t('INTERACTIVE_DEMO')}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Card title={t('THREE_TIER_ARCHITECTURE')} size="small">
            <ThreeTierArchitecture t={t} />
          </Card>
          
          <Card title={t('CAPABILITY_DRIVEN_UI')} size="small">
            <CapabilityAwareTopic t={t}>
              <div style={{ padding: 20, textAlign: 'center' }}>
                <p>RIP-1能力集驱动的UI组件会在这里根据集群能力动态显示不同的功能。</p>
                <p>当前集群支持的能力将决定UI的复杂度和可用功能。</p>
                <Space>
                  <Button 
                    type="primary" 
                    disabled={!capabilities?.supportsLiteTopic}
                    title={capabilities?.supportsLiteTopic ? 'Lite Topic支持已启用' : '当前集群不支持Lite Topic'}
                  >
                    Lite Topic功能 {capabilities?.supportsLiteTopic ? '✓' : '✗'}
                  </Button>
                  <Button 
                    type="primary" 
                    disabled={!capabilities?.supportsGrpc}
                    title={capabilities?.supportsGrpc ? 'gRPC支持已启用' : '当前集群不支持gRPC'}
                  >
                    gRPC监控 {capabilities?.supportsGrpc ? '✓' : '✗'}
                  </Button>
                  <Button 
                    type="primary" 
                    disabled={!capabilities?.hasNamespace}
                    title={capabilities?.hasNamespace ? 'Namespace支持已启用' : '当前集群不支持Namespace'}
                  >
                    Namespace管理 {capabilities?.hasNamespace ? '✓' : '✗'}
                  </Button>
                </Space>
              </div>
            </CapabilityAwareTopic>
          </Card>
        </Space>
      </Card>
    </div>
  );
};

export default CapabilityDemo;