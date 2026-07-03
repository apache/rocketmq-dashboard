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

import React, { useState, useEffect } from 'react';
import { Card, Badge, Timeline, Tag, Button, Modal, Tabs, Table, Space, Tooltip } from 'antd';
import { useClusterCapabilities } from '../../store/context/ClusterCapabilitiesContext';
import { remoteApi } from '../../api/remoteApi/remoteApi';

/**
 * Three-tier architecture visualization component
 * Implements the RIP-1 requirement: Business Layer -> MetadataProvider -> AdminClient
 */

/**
 * Business Layer Component
 */
export const BusinessLayer = ({ selectedEntity, onEntityAction, capabilities, t }) => {
  const [entities, setEntities] = useState({
    topics: [],
    consumers: [],
    producers: [],
    namespaces: [],
    clusters: []
  });
  const [loading, setLoading] = useState(false);
  
  useEffect(() => {
    loadBusinessEntities();
  }, [capabilities]);
  
  const loadBusinessEntities = async () => {
    setLoading(true);
    try {
      // Load entities based on capabilities
      const results = {};
      
      // Topics
      const topicResult = await remoteApi.queryTopicList();
      if (topicResult.status === 0) {
        results.topics = topicResult.data.topicNameList || [];
      }
      
      // Consumer Groups
      const consumerResult = await remoteApi.queryConsumerGroupList(false);
      if (consumerResult.status === 0) {
        results.consumers = consumerResult.data.groupNameList || [];
      }
      
      // Namespaces (if supported)
      if (capabilities.hasNamespace) {
        const namespaceResult = await remoteApi.queryNamespaceList();
        if (namespaceResult.status === 0) {
          results.namespaces = namespaceResult.data.namespaceList || [];
        }
      }
      
      // Clusters
      const clusterResult = await remoteApi.getClusterList();
      if (clusterResult.status === 0) {
        results.clusters = Object.keys(clusterResult.data.clusterInfo?.clusterAddrTable || {});
      }
      
      setEntities(results);
    } catch (error) {
      console.error('Error loading business entities:', error);
    } finally {
      setLoading(false);
    }
  };
  
  const getEntityCounts = () => {
    return {
      topics: entities.topics.length,
      consumers: entities.consumers.length,
      namespaces: entities.namespaces.length,
      clusters: entities.clusters.length
    };
  };
  
  const counts = getEntityCounts();
  
  return (
    <Card 
      title={t.BUSINESS_LAYER}
      className="business-layer"
      loading={loading}
      extra={
        <Badge 
          count={Object.values(counts).reduce((a, b) => a + b, 0)} 
          style={{ backgroundColor: '#52c41a' }}
          overflowCount={999}
        />
      }
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        {/* Topics */}
        <div className="entity-section">
          <Tag color="blue">{t.TOPICS}</Tag>
          <Badge count={counts.topics} />
          <Button 
            size="small" 
            onClick={() => onEntityAction('view', 'topics')}
            disabled={counts.topics === 0}
          >
            {t.VIEW}
          </Button>
        </div>
        
        {/* Consumer Groups */}
        <div className="entity-section">
          <Tag color="green">{t.CONSUMER_GROUPS}</Tag>
          <Badge count={counts.consumers} />
          <Button 
            size="small" 
            onClick={() => onEntityAction('view', 'consumers')}
            disabled={counts.consumers === 0}
          >
            {t.VIEW}
          </Button>
        </div>
        
        {/* Namespaces (conditional) */}
        {capabilities.hasNamespace && (
          <div className="entity-section">
            <Tag color="purple">{t.NAMESPACES}</Tag>
            <Badge count={counts.namespaces} />
            <Button 
              size="small" 
              onClick={() => onEntityAction('view', 'namespaces')}
              disabled={counts.namespaces === 0}
            >
              {t.VIEW}
            </Button>
          </div>
        )}
        
        {/* Clusters */}
        <div className="entity-section">
          <Tag color="orange">{t.CLUSTERS}</Tag>
          <Badge count={counts.clusters} />
          <Button 
            size="small" 
            onClick={() => onEntityAction('view', 'clusters')}
            disabled={counts.clusters === 0}
          >
            {t.VIEW}
          </Button>
        </div>
      </Space>
    </Card>
  );
};

/**
 * Metadata Provider Layer Component
 */
export const MetadataProviderLayer = ({ capabilities, t }) => {
  const [metadataServices, setMetadataServices] = useState([]);
  const [loading, setLoading] = useState(false);
  
  useEffect(() => {
    loadMetadataServices();
  }, [capabilities]);
  
  const loadMetadataServices = async () => {
    setLoading(true);
    try {
      // Simulate loading metadata services based on cluster type
      const services = [
        {
          name: 'TopicMetadataService',
          status: 'active',
          features: ['Topic Discovery', 'Route Info', 'Queue Management'],
          version: capabilities.isV5Architecture ? '2.0' : '1.0'
        },
        {
          name: 'ConsumerGroupMetadataService',
          status: 'active',
          features: ['Group Discovery', 'Rebalance Status', 'Client Info'],
          version: capabilities.isV5Architecture ? '2.0' : '1.0'
        },
        {
          name: 'BrokerMetadataService',
          status: 'active',
          features: ['Broker Status', 'Config Management', 'Health Check'],
          version: capabilities.isV5Architecture ? '2.0' : '1.0'
        }
      ];
      
      // Add v5-specific services
      if (capabilities.isV5Architecture) {
        services.push({
          name: 'NamespaceMetadataService',
          status: capabilities.hasNamespace ? 'active' : 'inactive',
          features: ['Namespace Management', 'Multi-tenancy', 'Resource Isolation'],
          version: '2.0'
        });
        
        services.push({
          name: 'AclMetadataService',
          status: capabilities.supportsAcl2 ? 'active' : 'legacy',
          features: ['ACL Management', 'Policy Enforcement', 'Access Control'],
          version: '2.0'
        });
      }
      
      setMetadataServices(services);
    } catch (error) {
      console.error('Error loading metadata services:', error);
    } finally {
      setLoading(false);
    }
  };
  
  const getStatusColor = (status) => {
    switch (status) {
      case 'active': return 'green';
      case 'legacy': return 'orange';
      case 'inactive': return 'red';
      default: return 'default';
    }
  };
  
  return (
    <Card 
      title={t.METADATA_PROVIDER}
      className="metadata-provider-layer"
      loading={loading}
    >
      <Timeline mode="left">
        {metadataServices.map((service, index) => (
          <Timeline.Item
            key={index}
            label={service.name}
            color={getStatusColor(service.status)}
          >
            <div className="service-info">
              <Tag color={getStatusColor(service.status)}>
                {service.status.toUpperCase()}
              </Tag>
              <Tag>{service.version}</Tag>
              <div className="service-features">
                {service.features.map((feature, idx) => (
                  <Tag key={idx} size="small">{feature}</Tag>
                ))}
              </div>
            </div>
          </Timeline.Item>
        ))}
      </Timeline>
    </Card>
  );
};

/**
 * Admin Client Layer Component
 */
export const AdminClientLayer = ({ capabilities, t }) => {
  const [adminClients, setAdminClients] = useState([]);
  const [loading, setLoading] = useState(false);
  
  useEffect(() => {
    loadAdminClients();
  }, [capabilities]);
  
  const loadAdminClients = async () => {
    setLoading(true);
    try {
      // Simulate loading admin clients based on access type
      const clients = [];
      
      // Always have the base admin client
      clients.push({
        name: 'RocketMQAdmin',
        protocol: 'Remoting',
        status: 'active',
        capabilities: ['Topic Management', 'Consumer Management', 'Broker Management'],
        accessType: 'direct'
      });
      
      // Add gRPC client if supported
      if (capabilities.supportsGrpc) {
        clients.push({
          name: 'RocketMQGrpcAdmin',
          protocol: 'gRPC',
          status: 'active',
          capabilities: ['Enhanced Metrics', 'Streaming APIs', 'Bi-directional Communication'],
          accessType: 'grpc'
        });
      }
      
      // Add Proxy clients for v5 architecture
      if (capabilities.isV5Architecture) {
        switch (capabilities.accessType) {
          case 'proxy-local':
            clients.push({
              name: 'ProxyLocalAdmin',
              protocol: 'gRPC',
              status: 'active',
              capabilities: ['Local Proxy', 'Unified API', 'Protocol Translation'],
              accessType: 'proxy-local'
            });
            break;
          case 'proxy-cluster':
            clients.push({
              name: 'ProxyClusterAdmin',
              protocol: 'gRPC',
              status: 'active',
              capabilities: ['Cluster Proxy', 'Load Balancing', 'Unified Namespace'],
              accessType: 'proxy-cluster'
            });
            break;
          default:
            break;
        }
      }
      
      setAdminClients(clients);
    } catch (error) {
      console.error('Error loading admin clients:', error);
    } finally {
      setLoading(false);
    }
  };
  
  return (
    <Card 
      title={t.ADMIN_CLIENT}
      className="admin-client-layer"
      loading={loading}
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        {adminClients.map((client, index) => (
          <Card key={index} size="small" className="admin-client-card">
            <div className="client-header">
              <Tag color="blue">{client.name}</Tag>
              <Tag color="green">{client.protocol}</Tag>
              <Tag color={client.status === 'active' ? 'green' : 'red'}>
                {client.status.toUpperCase()}
              </Tag>
            </div>
            <div className="client-capabilities">
              <strong>{t.CAPABILITIES}:</strong>
              <Space wrap>
                {client.capabilities.map((capability, idx) => (
                  <Tag key={idx} size="small">{capability}</Tag>
                ))}
              </Space>
            </div>
            <div className="client-access-type">
              <strong>{t.ACCESS_TYPE}:</strong> {client.accessType}
            </div>
          </Card>
        ))}
      </Space>
    </Card>
  );
};

/**
 * Architecture Overview Dashboard
 */
export const ArchitectureOverview = ({ capabilities, t }) => {
  const [activeTab, setActiveTab] = useState('overview');
  const [selectedEntity, setSelectedEntity] = useState(null);
  
  const handleEntityAction = (action, entityType) => {
    setSelectedEntity({ action, type: entityType });
  };
  
  const tabs = [
    {
      key: 'overview',
      label: t.ARCHITECTURE_OVERVIEW,
      children: (
        <div className="architecture-overview">
          <BusinessLayer
            onEntityAction={handleEntityAction}
            capabilities={capabilities}
            t={t}
          />
          <MetadataProviderLayer capabilities={capabilities} t={t} />
          <AdminClientLayer capabilities={capabilities} t={t} />
        </div>
      )
    },
    {
      key: 'capabilities',
      label: t.CLUSTER_CAPABILITIES,
      children: (
        <Card title={t.CAPABILITY_MATRIX}>
          <Table
            dataSource={[
              {
                feature: t.V5_ARCHITECTURE,
                supported: capabilities.isV5Architecture ? t.YES : t.NO,
                description: t.V5_ARCHITECTURE_DESC
              },
              {
                feature: t.NAMESPACE_SUPPORT,
                supported: capabilities.hasNamespace ? t.YES : t.NO,
                description: t.NAMESPACE_DESC
              },
              {
                feature: t.LITE_TOPIC_SUPPORT,
                supported: capabilities.supportsLiteTopic ? t.YES : t.NO,
                description: t.LITE_TOPIC_DESC
              },
              {
                feature: t.GRPC_SUPPORT,
                supported: capabilities.supportsGrpc ? t.YES : t.NO,
                description: t.GRPC_DESC
              },
              {
                feature: t.ACL_2_0_SUPPORT,
                supported: capabilities.supportsAcl2 ? t.YES : t.NO,
                description: t.ACL_2_0_DESC
              },
              {
                feature: t.POP_CONSUMPTION,
                supported: capabilities.supportsPopConsumption ? t.YES : t.NO,
                description: t.POP_DESC
              }
            ]}
            columns={[
              { title: t.FEATURE, dataIndex: 'feature', key: 'feature' },
              { title: t.SUPPORTED, dataIndex: 'supported', key: 'supported' },
              { title: t.DESCRIPTION, dataIndex: 'description', key: 'description' }
            ]}
            pagination={false}
          />
        </Card>
      )
    },
    {
      key: 'access-types',
      label: t.ACCESS_TYPES,
      children: (
        <Card title={t.ACCESS_TYPE_MATRIX}>
          <Table
            dataSource={[
              {
                type: 'v4-namesrv',
                protocol: 'Remoting',
                architecture: 'RocketMQ 4.0',
                features: 'Basic Operations',
                current: capabilities.accessType === 'v4-namesrv'
              },
              {
                type: 'v5-proxy-local',
                protocol: 'gRPC',
                architecture: 'RocketMQ 5.0',
                features: 'Proxy + Local Access',
                current: capabilities.accessType === 'v5-proxy-local'
              },
              {
                type: 'v5-proxy-cluster',
                protocol: 'gRPC',
                architecture: 'RocketMQ 5.0',
                features: 'Proxy + Cluster Access',
                current: capabilities.accessType === 'v5-proxy-cluster'
              }
            ]}
            columns={[
              { title: t.TYPE, dataIndex: 'type', key: 'type' },
              { title: t.PROTOCOL, dataIndex: 'protocol', key: 'protocol' },
              { title: t.ARCHITECTURE, dataIndex: 'architecture', key: 'architecture' },
              { title: t.FEATURES, dataIndex: 'features', key: 'features' },
              {
                title: t.CURRENT,
                dataIndex: 'current',
                key: 'current',
                render: (current) => current ? <Tag color="green">{t.ACTIVE}</Tag> : ''
              }
            ]}
            pagination={false}
          />
        </Card>
      )
    }
  ];
  
  return (
    <div className="three-tier-architecture">
      <Tabs activeKey={activeTab} onChange={setActiveTab}>
        {tabs.map(tab => (
          <Tabs.TabPane key={tab.key} tab={tab.label}>
            {tab.children}
          </Tabs.TabPane>
        ))}
      </Tabs>
    </div>
  );
};

/**
 * Main Three Tier Architecture Component
 */
const ThreeTierArchitecture = ({ t }) => {
  const { capabilities } = useClusterCapabilities();
  
  if (!capabilities) {
    return <div>Loading cluster capabilities...</div>;
  }
  
  return (
    <div className="three-tier-architecture-container">
      <ArchitectureOverview capabilities={capabilities} t={t} />
    </div>
  );
};

export default ThreeTierArchitecture;