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

import React, { useEffect, useState } from 'react';
import { Button, Checkbox, Form, Input, message, Popconfirm, Space, Table, Tag, Tooltip } from 'antd';
import { useLanguage } from '../../i18n/LanguageContext';
import { useClusterCapabilities } from '../../store/context/ClusterCapabilitiesContext';
import { remoteApi } from '../../api/remoteApi/remoteApi';
import CapabilityAwareTopic, { TopicTypeFilter, TopicOperations } from '../../components/topic/CapabilityAwareTopic';
import TopicCreationForm from '../../components/topic/TopicCreationForm';
import ResetOffsetResultDialog from "../../components/topic/ResetOffsetResultDialog";
import SendResultDialog from "../../components/topic/SendResultDialog";
import TopicModifyDialog from "../../components/topic/TopicModifyDialog";
import ConsumerViewDialog from "../../components/topic/ConsumerViewDialog";
import ConsumerResetOffsetDialog from "../../components/topic/ConsumerResetOffsetDialog";
import SkipMessageAccumulateDialog from "../../components/topic/SkipMessageAccumulateDialog";
import StatsViewDialog from "../../components/topic/StatsViewDialog";
import RouterViewDialog from "../../components/topic/RouterViewDialog";
import SendTopicMessageDialog from "../../components/topic/SendTopicMessageDialog";

/**
 * Enhanced Topic Management Page with capability-driven UI
 * Implements the three-tier abstraction of RIP-1: Business Layer -> MetadataProvider -> AdminClient
 */
const CapabilityTopicPage = () => {
  const { t } = useLanguage();
  const { capabilities, selectedCluster } = useClusterCapabilities();
  
  // Filter states
  const [filterStr, setFilterStr] = useState('');
  const [filterStates, setFilterStates] = useState({
    normal: true,
    delay: false,
    fifo: false,
    transaction: false,
    lite: false,
    unspecified: false,
    retry: false,
    dlq: false,
    system: false
  });
  
  // Data states
  const [allTopicList, setAllTopicList] = useState([]);
  const [allMessageTypeList, setAllMessageTypeList] = useState([]);
  const [allNamespaceList, setAllNamespaceList] = useState([]);
  const [topicShowList, setTopicShowList] = useState([]);
  const [loading, setLoading] = useState(false);
  const [writeOperationEnabled, setWriteOperationEnabled] = useState(true);
  
  // Dialog states
  const [dialogVisible, setDialogVisible] = useState({
    addUpdate: false,
    resetOffset: false,
    sendResult: false,
    consumerView: false,
    consumerReset: false,
    skipAccumulate: false,
    stats: false,
    router: false,
    sendMessage: false,
    liteConfig: false,
    grpcMetrics: false
  });
  
  // Dialog data
  const [dialogData, setDialogData] = useState({
    currentTopic: '',
    resetResult: null,
    sendResult: null,
    consumerData: null,
    allConsumerGroups: [],
    statsData: null,
    routeData: null,
    topicModifyData: [],
    sendMessageData: { topic: '', tag: '', key: '', messageBody: '', traceEnabled: false },
    selectedConsumerGroups: [],
    resetOffsetTime: new Date(),
    liteConfigData: null,
    grpcMetricsData: null
  });
  
  const [messageApi, msgContextHolder] = message.useMessage();
  const [paginationConf, setPaginationConf] = useState({ current: 1, pageSize: 10, total: 0 });

  // Effect: Load topic list
  useEffect(() => {
    getTopicList();
  }, [selectedCluster]);

  // Effect: Filter topics when filters change
  useEffect(() => {
    filterList(paginationConf.current);
  }, [filterStr, filterStates, allTopicList, capabilities]);

  // Effect: Check user permissions
  useEffect(() => {
    const userPermission = localStorage.getItem('userrole');
    setWriteOperationEnabled(userPermission != 2);
  }, []);

  /**
   * Fetch topic list with namespace and Lite Topic support
   */
  const getTopicList = async () => {
    setLoading(true);
    try {
      let result;
      
      // Use enhanced API for v5 clusters with namespace support
      if (capabilities.isV5Architecture) {
        result = await remoteApi.queryTopicListEnhanced();
        if (result.status === 0) {
          setAllTopicList(result.data.topicNameList || []);
          setAllMessageTypeList(result.data.messageTypeList || []);
          setAllNamespaceList(result.data.namespaceList || []);
        }
      } else {
        // Fallback to traditional API for v4 clusters
        result = await remoteApi.queryTopicList();
        if (result.status === 0) {
          setAllTopicList(result.data.topicNameList || []);
          setAllMessageTypeList(result.data.messageTypeList || []);
          setAllNamespaceList([]);
        }
      }
      
      if (result.status === 0) {
        setPaginationConf(prev => ({ ...prev, total: result.data.topicNameList?.length || 0 }));
      } else {
        messageApi.error(result.errMsg);
      }
    } catch (error) {
      console.error("Error fetching topic list:", error);
      messageApi.error("Failed to fetch topic list");
    } finally {
      setLoading(false);
    }
  };

  /**
   * Enhanced filtering with capability awareness
   */
  const filterList = (currentPage) => {
    const lowExceptStr = filterStr.toLowerCase();
    const canShowList = allTopicList.filter((topic, index) => {
      // Text filter
      if (filterStr && !topic.toLowerCase().includes(lowExceptStr)) {
        return false;
      }
      
      // Type filter with capability awareness
      const messageType = allMessageTypeList[index] || '';
      return filterByType(messageType);
    });

    const perPage = paginationConf.pageSize;
    const from = (currentPage - 1) * perPage;
    const to = Math.min(from + perPage, canShowList.length);

    setTopicShowList(canShowList.slice(from, to));
    setPaginationConf(prev => ({ ...prev, current: currentPage, total: canShowList.length }));
  };

  /**
   * Advanced type filtering based on cluster capabilities
   */
  const filterByType = (type) => {
    // Basic types
    if (filterStates.retry && type.includes("RETRY")) return true;
    if (filterStates.dlq && type.includes("DLQ")) return true;
    if (filterStates.system && type.includes("SYSTEM")) return true;
    if (filterStates.normal && type.includes("NORMAL")) return true;
    
    // Capability-dependent types
    if (!capabilities.isV5Architecture && filterStates.normal && type.includes("UNSPECIFIED")) return true;
    if (capabilities.isV5Architecture && filterStates.unspecified && type.includes("UNSPECIFIED")) return true;
    
    // Advanced types only available in v5
    if (capabilities.isV5Architecture) {
      if (filterStates.delay && type.includes("DELAY")) return true;
      if (filterStates.fifo && type.includes("FIFO")) return true;
      if (filterStates.transaction && type.includes("TRANSACTION")) return true;
      if (capabilities.supportsLiteTopic && filterStates.lite && type.includes("LITE")) return true;
    }
    
    return false;
  };

  /**
   * Handle filter state changes
   */
  const handleFilterChange = (filterName, checked) => {
    setFilterStates(prev => ({ ...prev, [filterName]: checked }));
  };

  /**
   * Unified action handler for topic operations
   */
  const handleTopicAction = async (action, topic) => {
    setDialogData(prev => ({ ...prev, currentTopic: topic }));
    
    try {
      switch (action) {
        case 'stats':
          const statsResult = await remoteApi.getTopicStats(topic);
          if (statsResult.status === 0) {
            setDialogData(prev => ({ ...prev, statsData: statsResult.data }));
            setDialogVisible(prev => ({ ...prev, stats: true }));
          } else {
            messageApi.error(statsResult.errMsg);
          }
          break;
          
        case 'router':
          const routeResult = await remoteApi.getTopicRoute(topic);
          if (routeResult.status === 0) {
            setDialogData(prev => ({ ...prev, routeData: routeResult.data }));
            setDialogVisible(prev => ({ ...prev, router: true }));
          } else {
            messageApi.error(routeResult.errMsg);
          }
          break;
          
        case 'consumer':
          const consumerResult = await remoteApi.getTopicConsumers(topic);
          if (consumerResult.status === 0) {
            setDialogData(prev => ({ 
              ...prev, 
              consumerData: consumerResult.data,
              allConsumerGroups: Object.keys(consumerResult.data)
            }));
            setDialogVisible(prev => ({ ...prev, consumerView: true }));
          } else {
            messageApi.error(consumerResult.errMsg);
          }
          break;
          
        // Add more actions as needed
        default:
          console.log(`Action ${action} for topic ${topic}`);
      }
    } catch (error) {
      console.error(`Error handling action ${action}:`, error);
      messageApi.error(`Failed to perform ${action}`);
    }
  };

  /**
   * Handle topic creation with capability-aware form
   */
  const handleTopicCreate = async (formData) => {
    try {
      // Enhance form data based on capabilities
      const enhancedData = {
        ...formData,
        clusterType: capabilities.isV5Architecture ? 'v5' : 'v4',
        accessType: capabilities.accessType,
        features: {
          namespace: capabilities.hasNamespace && formData.namespace,
          liteTopic: capabilities.supportsLiteTopic && formData.isLite,
          grpc: capabilities.supportsGrpc && formData.grpcEnabled
        }
      };
      
      const result = await remoteApi.createOrUpdateTopic(enhancedData);
      if (result.status === 0) {
        messageApi.success(t.TOPIC_OPERATION_SUCCESS);
        closeDialog('addUpdate');
        await getTopicList();
      } else {
        messageApi.error(result.errMsg);
      }
    } catch (error) {
      console.error("Error creating topic:", error);
      messageApi.error("Failed to create topic");
    }
  };

  /**
   * Close dialog helper
   */
  const closeDialog = (dialogName) => {
    setDialogVisible(prev => ({ ...prev, [dialogName]: false }));
  };

  /**
   * Table columns with capability-aware operations
   */
  const columns = [
    {
      title: t.TOPIC,
      dataIndex: 'topic',
      key: 'topic',
      align: 'center',
      render: (text) => {
        const sysFlag = text.startsWith('%SYS%');
        const topic = sysFlag ? text.substring(5) : text;
        
        // Show capability indicators
        const tags = [];
        if (capabilities.hasNamespace && text.includes('namespace://')) {
          tags.push(<Tag color="blue">NS</Tag>);
        }
        if (capabilities.supportsLiteTopic && allMessageTypeList[allTopicList.indexOf(text)]?.includes('LITE')) {
          tags.push(<Tag color="green">LITE</Tag>);
        }
        if (capabilities.supportsGrpc) {
          tags.push(<Tag color="purple">gRPC</Tag>);
        }
        
        return (
          <div style={{ color: sysFlag ? 'red' : '' }}>
            <span>{topic}</span>
            <div>{tags}</div>
          </div>
        );
      },
    },
    {
      title: t.MESSAGE_TYPE,
      dataIndex: 'type',
      key: 'type',
      align: 'center',
      render: (_, record, index) => {
        const type = allMessageTypeList[index] || 'UNKNOWN';
        return <Tag color={getTypeColor(type)}>{type}</Tag>;
      },
    },
    {
      title: t.OPERATION,
      key: 'operation',
      align: 'left',
      render: (_, record) => {
        const sysFlag = record.topic.startsWith('%SYS%');
        return (
          <TopicOperations
            topic={record.topic}
            isSystem={sysFlag}
            writeOperationEnabled={writeOperationEnabled}
            onAction={handleTopicAction}
            t={t}
          />
        );
      },
    },
  ];

  /**
   * Get color for message type
   */
  const getTypeColor = (type) => {
    if (type.includes('NORMAL')) return 'blue';
    if (type.includes('DELAY')) return 'orange';
    if (type.includes('FIFO')) return 'purple';
    if (type.includes('TRANSACTION')) return 'green';
    if (type.includes('RETRY')) return 'red';
    if (type.includes('DLQ')) return 'volcano';
    if (type.includes('LITE')) return 'cyan';
    return 'default';
  };

  return (
    <CapabilityAwareTopic>
      {msgContextHolder}
      
      <div className="container-fluid capability-topic-page">
        {/* Header with capability information */}
        <div className="capability-banner">
          <h4>{t.CLUSTER_CAPABILITIES}</h4>
          <div className="capability-tags">
            <Tag color={capabilities.isV5Architecture ? 'green' : 'red'}>
              {capabilities.isV5Architecture ? 'RocketMQ 5.0' : 'RocketMQ 4.0'}
            </Tag>
            <Tag color={capabilities.hasNamespace ? 'blue' : 'default'}>
              Namespace: {capabilities.hasNamespace ? t.SUPPORTED : t.NOT_SUPPORTED}
            </Tag>
            <Tag color={capabilities.supportsLiteTopic ? 'blue' : 'default'}>
              LiteTopic: {capabilities.supportsLiteTopic ? t.SUPPORTED : t.NOT_SUPPORTED}
            </Tag>
            <Tag color={capabilities.supportsGrpc ? 'blue' : 'default'}>
              gRPC: {capabilities.supportsGrpc ? t.SUPPORTED : t.NOT_SUPPORTED}
            </Tag>
            <Tag color={capabilities.supportsPopConsumption ? 'blue' : 'default'}>
              Pop: {capabilities.supportsPopConsumption ? t.SUPPORTED : t.NOT_SUPPORTED}
            </Tag>
          </div>
        </div>
        
        {/* Topic filters */}
        <div className="modal-body">
          <div className="row">
            <Form layout="inline" className="pull-left col-sm-12">
              <Form.Item label={t.TOPIC}>
                <Input
                  value={filterStr}
                  onChange={(e) => setFilterStr(e.target.value)}
                  placeholder={t.FILTER_PLACEHOLDER}
                />
              </Form.Item>
              
              {/* Capability-aware filter section */}
              <TopicTypeFilter t={t}>
                {Object.entries(filterStates).map(([key, checked]) => {
                  // Only show advanced filters if supported
                  if (['delay', 'fifo', 'transaction'].includes(key) && !capabilities.isV5Architecture) {
                    return null;
                  }
                  if (key === 'lite' && !capabilities.supportsLiteTopic) {
                    return null;
                  }
                  
                  return (
                    <Form.Item key={key}>
                      <Checkbox 
                        checked={checked} 
                        onChange={(e) => handleFilterChange(key, e.target.checked)}
                      >
                        {t[key.toUpperCase()]}
                      </Checkbox>
                    </Form.Item>
                  );
                })}
              </TopicTypeFilter>
              
              <Form.Item>
                <Button type="primary" onClick={() => setDialogVisible(prev => ({ ...prev, addUpdate: true }))}>
                  {t.CREATE_TOPIC}
                </Button>
              </Form.Item>
              
              <Form.Item>
                <Button type="primary" onClick={getTopicList}>
                  {t.REFRESH}
                </Button>
              </Form.Item>
            </Form>
          </div>
          
          {/* Topic table */}
          <div className="row mt-3">
            <Table
              bordered
              loading={loading}
              dataSource={topicShowList.map((topic, index) => ({ key: index, topic }))}
              columns={columns}
              pagination={paginationConf}
              onChange={(pagination) => {
                setPaginationConf(pagination);
                filterList(pagination.current);
              }}
            />
          </div>
        </div>

        {/* Capability-aware Topic Creation Dialog - To be implemented */}
        {dialogVisible.addUpdate && (
          <Modal
            title={t.CREATE_TOPIC}
            visible={true}
            onCancel={() => closeDialog('addUpdate')}
            footer={null}
            width={600}
          >
            <div style={{ padding: 20, textAlign: 'center' }}>
              <p>能力集感知的Topic创建表单将在这里显示</p>
              <p>根据集群能力动态调整表单字段</p>
            </div>
          </Modal>
        )}

        {/* Standard dialogs */}
        <ResetOffsetResultDialog
          visible={dialogVisible.resetOffset}
          onClose={() => closeDialog('resetOffset')}
          result={dialogData.resetResult}
          t={t}
        />
        
        <SendResultDialog
          visible={dialogVisible.sendResult}
          onClose={() => closeDialog('sendResult')}
          result={dialogData.sendResult}
          t={t}
        />
        
        <StatsViewDialog
          visible={dialogVisible.stats}
          onClose={() => closeDialog('stats')}
          topic={dialogData.currentTopic}
          statsData={dialogData.statsData}
          t={t}
        />
        
        <RouterViewDialog
          visible={dialogVisible.router}
          onClose={() => closeDialog('router')}
          topic={dialogData.currentTopic}
          routeData={dialogData.routeData}
          t={t}
        />
      </div>
    </CapabilityAwareTopic>
  );
};

export default CapabilityTopicPage;