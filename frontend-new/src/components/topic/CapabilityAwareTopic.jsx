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
import { useClusterCapabilities } from '../../store/context/ClusterCapabilitiesContext';

/**
 * Higher-order component that conditionally renders content based on cluster capabilities
 * This implements the capability-driven UI pattern for RIP-1
 */

/**
 * Topic type filter component that adapts based on cluster capabilities
 */
export const TopicTypeFilter = ({ children, t }) => {
  const { capabilities } = useClusterCapabilities();
  
  // Only show advanced topic types (DELAY, FIFO, TRANSACTION) if v5 architecture is supported
  const showAdvancedTypes = capabilities.isV5Architecture;
  
  return (
    <div className="topic-type-filter">
      {/* Basic types always shown */}
      <div className="basic-types">
        <label>
          <input type="checkbox" name="filterNormal" />
          {t.NORMAL}
        </label>
        <label>
          <input type="checkbox" name="filterRetry" />
          {t.RETRY}
        </label>
        <label>
          <input type="checkbox" name="filterDLQ" />
          {t.DLQ}
        </label>
        <label>
          <input type="checkbox" name="filterSystem" />
          {t.SYSTEM}
        </label>
      </div>
      
      {/* Advanced types only shown for v5 clusters */}
      {showAdvancedTypes && (
        <div className="advanced-types">
          <label>
            <input type="checkbox" name="filterDelay" />
            {t.DELAY}
          </label>
          <label>
            <input type="checkbox" name="filterFifo" />
            {t.FIFO}
          </label>
          <label>
            <input type="checkbox" name="filterTransaction" />
            {t.TRANSACTION}
          </label>
          {capabilities.supportsLiteTopic && (
            <label>
              <input type="checkbox" name="filterLite" />
              {t.LITE_TOPIC}
            </label>
          )}
        </div>
      )}
      
      {children}
    </div>
  );
};

/**
 * Topic operation buttons that adapt based on cluster capabilities
 */
export const TopicOperations = ({ topic, isSystem, writeOperationEnabled, onAction, t }) => {
  const { capabilities } = useClusterCapabilities();
  
  const topicName = isSystem && topic.startsWith('%SYS%') ? topic.substring(5) : topic;
  const isSystemTopic = topic.startsWith('%SYS%');
  
  return (
    <div className="topic-operations">
      {/* Always available operations */}
      <button 
        className="btn btn-primary btn-sm"
        onClick={() => onAction('stats', topicName)}
      >
        {t.STATUS}
      </button>
      
      <button 
        className="btn btn-primary btn-sm"
        onClick={() => onAction('router', topicName)}
      >
        {t.ROUTER}
      </button>
      
      <button 
        className="btn btn-primary btn-sm"
        onClick={() => onAction('consumer', topicName)}
      >
        Consumer {t.MANAGE}
      </button>
      
      <button 
        className="btn btn-primary btn-sm"
        onClick={() => onAction('config', topicName)}
      >
        Topic {t.CONFIG}
      </button>
      
      {/* Conditional operations based on capabilities */}
      {!isSystemTopic && (
        <button 
          className="btn btn-primary btn-sm"
          onClick={() => onAction('send', topicName)}
        >
          {t.SEND_MSG}
        </button>
      )}
      
      {/* Advanced operations only for supported clusters */}
      {!isSystemTopic && writeOperationEnabled && (
        <>
          <button 
            className="btn btn-danger btn-sm"
            onClick={() => onAction('resetOffset', topicName)}
          >
            {t.RESET_CUS_OFFSET}
          </button>
          
          <button 
            className="btn btn-danger btn-sm"
            onClick={() => onAction('skipAccumulate', topicName)}
          >
            {t.SKIP_MESSAGE_ACCUMULATE}
          </button>
          
          {/* Lite Topic specific operations */}
          {capabilities.supportsLiteTopic && (
            <button 
              className="btn btn-info btn-sm"
              onClick={() => onAction('liteConfig', topicName)}
            >
              {t.LITE_CONFIG}
            </button>
          )}
          
          {/* gRPC specific operations */}
          {capabilities.supportsGrpc && (
            <button 
              className="btn btn-success btn-sm"
              onClick={() => onAction('grpcMetrics', topicName)}
            >
              {t.GRPC_METRICS}
            </button>
          )}
          
          <button 
            className="btn btn-danger btn-sm"
            onClick={() => onAction('delete', topicName)}
          >
            {t.DELETE}
          </button>
        </>
      )}
    </div>
  );
};

/**
 * Topic creation form that adapts based on cluster capabilities
 */
export const TopicCreationForm = ({ capabilities, initialData, onSubmit, t }) => {
  const [formData, setFormData] = React.useState(initialData || {
    topicName: '',
    messageType: 'NORMAL',
    writeQueueNums: 8,
    readQueueNums: 8,
    perm: 7,
    namespace: '',
    isLite: false,
    grpcEnabled: false,
  });
  
  const handleChange = (field, value) => {
    setFormData(prev => ({ ...prev, [field]: value }));
  };
  
  const handleSubmit = (e) => {
    e.preventDefault();
    onSubmit(formData);
  };
  
  return (
    <form onSubmit={handleSubmit} className="topic-creation-form">
      <div className="form-group">
        <label>{t.TOPIC_NAME}:</label>
        <input
          type="text"
          value={formData.topicName}
          onChange={(e) => handleChange('topicName', e.target.value)}
          required
          className="form-control"
        />
      </div>
      
      {/* Namespace field only for v5 clusters */}
      {capabilities.hasNamespace && (
        <div className="form-group">
          <label>{t.NAMESPACE}:</label>
          <input
            type="text"
            value={formData.namespace}
            onChange={(e) => handleChange('namespace', e.target.value)}
            className="form-control"
          />
        </div>
      )}
      
      <div className="form-group">
        <label>{t.MESSAGE_TYPE}:</label>
        <select
          value={formData.messageType}
          onChange={(e) => handleChange('messageType', e.target.value)}
          className="form-control"
        >
          <option value="NORMAL">{t.NORMAL}</option>
          {!capabilities.isV5Architecture && <option value="UNSPECIFIED">{t.UNSPECIFIED}</option>}
          {capabilities.isV5Architecture && <option value="DELAY">{t.DELAY}</option>}
          {capabilities.isV5Architecture && <option value="FIFO">{t.FIFO}</option>}
          {capabilities.isV5Architecture && <option value="TRANSACTION">{t.TRANSACTION}</option>}
        </select>
      </div>
      
      {/* Lite Topic option */}
      {capabilities.supportsLiteTopic && (
        <div className="form-group">
          <label>
            <input
              type="checkbox"
              checked={formData.isLite}
              onChange={(e) => handleChange('isLite', e.target.checked)}
            />
            {t.LITE_TOPIC}
          </label>
        </div>
      )}
      
      {/* gRPC option */}
      {capabilities.supportsGrpc && (
        <div className="form-group">
          <label>
            <input
              type="checkbox"
              checked={formData.grpcEnabled}
              onChange={(e) => handleChange('grpcEnabled', e.target.checked)}
            />
            {t.GRPC_ENABLED}
          </label>
        </div>
      )}
      
      <div className="form-row">
        <div className="form-group col">
          <label>{t.WRITE_QUEUE_NUMS}:</label>
          <input
            type="number"
            value={formData.writeQueueNums}
            onChange={(e) => handleChange('writeQueueNums', parseInt(e.target.value))}
            min="1"
            max="128"
            className="form-control"
          />
        </div>
        
        <div className="form-group col">
          <label>{t.READ_QUEUE_NUMS}:</label>
          <input
            type="number"
            value={formData.readQueueNums}
            onChange={(e) => handleChange('readQueueNums', parseInt(e.target.value))}
            min="1"
            max="128"
            className="form-control"
          />
        </div>
      </div>
      
      <button type="submit" className="btn btn-primary">
        {t.CREATE_TOPIC}
      </button>
    </form>
  );
};

/**
 * Main capability-aware topic component that orchestrates the adaptive UI
 */
const CapabilityAwareTopic = ({ children, ...props }) => {
  return (
    <div className="capability-aware-topic">
      {children}
    </div>
  );
};

export default CapabilityAwareTopic;