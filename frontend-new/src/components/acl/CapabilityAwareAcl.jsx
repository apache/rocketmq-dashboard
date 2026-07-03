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
import { useClusterCapabilities } from '../../store/context/ClusterCapabilitiesContext';
import { Form, Select, Switch, Tag, Tooltip, Button, Modal, message } from 'antd';

/**
 * ACL version-aware component that adapts UI based on cluster ACL capabilities
 * Supports both ACL 1.0 and ACL 2.0 mixed mode as per RIP-1 requirements
 */

/**
 * ACL Version Indicator Component
 */
export const AclVersionIndicator = ({ capabilities, t }) => {
  return (
    <div className="acl-version-indicator">
      <Tag color={capabilities.supportsAcl2 ? 'green' : 'orange'}>
        ACL {capabilities.supportsAcl2 ? '2.0' : '1.0'}
      </Tag>
      {capabilities.supportsAcl2 && (
        <Tooltip title={t.ACL_2_0_FEATURES}>
          <Tag color="blue">{t.ADVANCED_FEATURES}</Tag>
        </Tooltip>
      )}
    </div>
  );
};

/**
 * Subject type selector that adapts based on ACL version
 */
export const AclSubjectSelector = ({ 
  subjectType, 
  onSubjectTypeChange, 
  subjectValue, 
  onSubjectValueChange, 
  capabilities, 
  t 
}) => {
  const [subjectOptions, setSubjectOptions] = useState([]);
  const [loading, setLoading] = useState(false);
  
  // Different subject types for ACL 1.0 vs 2.0
  const getSubjectTypes = () => {
    const baseTypes = [
      { value: 'consumerGroup', label: t.CONSUMER_GROUP },
      { value: 'producerGroup', label: t.PRODUCER_GROUP },
      { value: 'ipWhiteList', label: t.IP_WHITE_LIST },
    ];
    
    // ACL 2.0 additional subject types
    if (capabilities.supportsAcl2) {
      return [
        ...baseTypes,
        { value: 'userId', label: t.USER_ID },
        { value: 'role', label: t.ROLE },
        { value: 'application', label: t.APPLICATION },
        { value: 'service', label: t.SERVICE },
      ];
    }
    
    return baseTypes;
  };
  
  // Load subject options based on type
  const loadSubjectOptions = async (type) => {
    setLoading(true);
    try {
      let options = [];
      
      switch (type) {
        case 'consumerGroup':
          // Load consumer groups
          break;
        case 'producerGroup':
          // Load producer groups
          break;
        case 'userId':
          // ACL 2.0 user IDs
          if (capabilities.supportsAcl2) {
            // Load from enhanced API
          }
          break;
        case 'role':
          // ACL 2.0 roles
          if (capabilities.supportsAcl2) {
            // Load roles
          }
          break;
        default:
          options = [];
      }
      
      setSubjectOptions(options);
    } catch (error) {
      console.error('Error loading subject options:', error);
      message.error(t.LOAD_SUBJECT_OPTIONS_ERROR);
    } finally {
      setLoading(false);
    }
  };
  
  useEffect(() => {
    if (subjectType) {
      loadSubjectOptions(subjectType);
    }
  }, [subjectType, capabilities]);
  
  return (
    <div className="acl-subject-selector">
      <Form.Item label={t.SUBJECT_TYPE} required>
        <Select
          value={subjectType}
          onChange={onSubjectTypeChange}
          placeholder={t.SELECT_SUBJECT_TYPE}
        >
          {getSubjectTypes().map(type => (
            <Select.Option key={type.value} value={type.value}>
              {type.label}
            </Select.Option>
          ))}
        </Select>
      </Form.Item>
      
      <Form.Item label={t.SUBJECT_VALUE} required>
        <Select
          value={subjectValue}
          onChange={onSubjectValueChange}
          options={subjectOptions}
          loading={loading}
          placeholder={t.SELECT_SUBJECT_VALUE}
          showSearch
          allowClear
        />
      </Form.Item>
    </div>
  );
};

/**
 * Resource type selector with ACL version awareness
 */
export const AclResourceSelector = ({
  resourceType,
  onResourceTypeChange,
  resourcePattern,
  onResourcePatternChange,
  capabilities,
  t
}) => {
  // Resource patterns differ between ACL versions
  const getResourceTypes = () => {
    const baseTypes = [
      { value: 'topic', label: t.TOPIC },
      { value: 'group', label: t.CONSUMER_GROUP },
    ];
    
    if (capabilities.supportsAcl2) {
      return [
        ...baseTypes,
        { value: 'cluster', label: t.CLUSTER },
        { value: 'namespace', label: t.NAMESPACE },
        { value: 'transaction', label: t.TRANSACTION },
      ];
    }
    
    return baseTypes;
  };
  
  // Pattern types for ACL 2.0
  const getPatternTypes = () => {
    const basePatterns = [
      { value: 'exact', label: t.EXACT_MATCH },
      { value: 'prefix', label: t.PREFIX_MATCH },
    ];
    
    if (capabilities.supportsAcl2) {
      return [
        ...basePatterns,
        { value: 'wildcard', label: t.WILDCARD_MATCH },
        { value: 'regex', label: t.REGEX_MATCH },
      ];
    }
    
    return basePatterns;
  };
  
  return (
    <div className="acl-resource-selector">
      <Form.Item label={t.RESOURCE_TYPE} required>
        <Select
          value={resourceType}
          onChange={onResourceTypeChange}
          placeholder={t.SELECT_RESOURCE_TYPE}
        >
          {getResourceTypes().map(type => (
            <Select.Option key={type.value} value={type.value}>
              {type.label}
            </Select.Option>
          ))}
        </Select>
      </Form.Item>
      
      {capabilities.supportsAcl2 && (
        <Form.Item label={t.RESOURCE_PATTERN}>
          <Select
            value={resourcePattern}
            onChange={onResourcePatternChange}
            placeholder={t.SELECT_PATTERN_TYPE}
          >
            {getPatternTypes().map(pattern => (
              <Select.Option key={pattern.value} value={pattern.value}>
                {pattern.label}
              </Select.Option>
            ))}
          </Select>
        </Form.Item>
      )}
    </div>
  );
};

/**
 * Permission configuration with ACL version awareness
 */
export const AclPermissionConfig = ({
  permissions,
  onPermissionsChange,
  capabilities,
  t
}) => {
  // Basic permissions for ACL 1.0
  const basePermissions = [
    { key: 'read', label: t.READ_PERMISSION, description: t.READ_DESC },
    { key: 'write', label: t.WRITE_PERMISSION, description: t.WRITE_DESC },
  ];
  
  // Extended permissions for ACL 2.0
  const extendedPermissions = capabilities.supportsAcl2 ? [
    { key: 'delete', label: t.DELETE_PERMISSION, description: t.DELETE_DESC },
    { key: 'admin', label: t.ADMIN_PERMISSION, description: t.ADMIN_DESC },
    { key: 'configure', label: t.CONFIGURE_PERMISSION, description: t.CONFIGURE_DESC },
    { key: 'consume', label: t.CONSUME_PERMISSION, description: t.CONSUME_DESC },
    { key: 'produce', label: t.PRODUCE_PERMISSION, description: t.PRODUCE_DESC },
  ] : [];
  
  const allPermissions = [...basePermissions, ...extendedPermissions];
  
  const handlePermissionToggle = (permission, enabled) => {
    const newPermissions = enabled
      ? [...permissions, permission]
      : permissions.filter(p => p !== permission);
    
    onPermissionsChange(newPermissions);
  };
  
  return (
    <div className="acl-permission-config">
      <h4>{t.PERMISSIONS}</h4>
      
      {/* Basic permissions always available */}
      <div className="basic-permissions">
        {basePermissions.map(permission => (
          <Form.Item key={permission.key}>
            <Tooltip title={permission.description}>
              <Switch
                checked={permissions.includes(permission.key)}
                onChange={(checked) => handlePermissionToggle(permission.key, checked)}
              />
              <span className="permission-label">{permission.label}</span>
            </Tooltip>
          </Form.Item>
        ))}
      </div>
      
      {/* Extended permissions for ACL 2.0 */}
      {capabilities.supportsAcl2 && (
        <div className="extended-permissions">
          <h5>{t.ACL_2_0_PERMISSIONS}</h5>
          {extendedPermissions.map(permission => (
            <Form.Item key={permission.key}>
              <Tooltip title={permission.description}>
                <Switch
                  checked={permissions.includes(permission.key)}
                  onChange={(checked) => handlePermissionToggle(permission.key, checked)}
                />
                <span className="permission-label">{permission.label}</span>
              </Tooltip>
            </Form.Item>
          ))}
        </div>
      )}
      
      {/* Permission summary */}
      <div className="permission-summary">
        <p>{t.CURRENT_PERMISSIONS}: {permissions.join(', ') || t.NONE}</p>
      </div>
    </div>
  );
};

/**
 * Main ACL policy form component
 */
export const AclPolicyForm = ({
  initialData,
  onSubmit,
  onCancel,
  capabilities,
  t
}) => {
  const [formData, setFormData] = useState({
    subjectType: '',
    subjectValue: '',
    resourceType: '',
    resourceValue: '',
    resourcePattern: 'exact',
    permissions: [],
    effect: 'allow',
    priority: 1,
    conditions: [],
    ...initialData
  });
  
  const [loading, setLoading] = useState(false);
  
  const handleSubmit = async (e) => {
    e.preventDefault();
    
    // Validate form
    if (!formData.subjectType || !formData.subjectValue || !formData.resourceType) {
      message.error(t.FORM_VALIDATION_ERROR);
      return;
    }
    
    setLoading(true);
    try {
      // Enhance data for ACL 2.0
      const enhancedData = {
        ...formData,
        aclVersion: capabilities.supportsAcl2 ? '2.0' : '1.0',
        clusterCapabilities: {
          supportsAcl2: capabilities.supportsAcl2,
          hasNamespace: capabilities.hasNamespace,
          isV5Architecture: capabilities.isV5Architecture
        }
      };
      
      await onSubmit(enhancedData);
    } catch (error) {
      console.error('Error submitting ACL policy:', error);
      message.error(t.SUBMIT_ERROR);
    } finally {
      setLoading(false);
    }
  };
  
  return (
    <form onSubmit={handleSubmit} className="acl-policy-form">
      <AclVersionIndicator capabilities={capabilities} t={t} />
      
      <AclSubjectSelector
        subjectType={formData.subjectType}
        onSubjectTypeChange={(value) => setFormData(prev => ({ ...prev, subjectType: value }))}
        subjectValue={formData.subjectValue}
        onSubjectValueChange={(value) => setFormData(prev => ({ ...prev, subjectValue: value }))}
        capabilities={capabilities}
        t={t}
      />
      
      <AclResourceSelector
        resourceType={formData.resourceType}
        onResourceTypeChange={(value) => setFormData(prev => ({ ...prev, resourceType: value }))}
        resourcePattern={formData.resourcePattern}
        onResourcePatternChange={(value) => setFormData(prev => ({ ...prev, resourcePattern: value }))}
        capabilities={capabilities}
        t={t}
      />
      
      <Form.Item label={t.RESOURCE_VALUE} required>
        <input
          type="text"
          value={formData.resourceValue}
          onChange={(e) => setFormData(prev => ({ ...prev, resourceValue: e.target.value }))}
          className="form-control"
          placeholder={t.ENTER_RESOURCE_NAME}
        />
      </Form.Item>
      
      <AclPermissionConfig
        permissions={formData.permissions}
        onPermissionsChange={(permissions) => setFormData(prev => ({ ...prev, permissions }))}
        capabilities={capabilities}
        t={t}
      />
      
      {/* ACL 2.0 enhanced features */}
      {capabilities.supportsAcl2 && (
        <div className="acl-2-enhanced-features">
          <Form.Item label={t.EFFECT}>
            <Select
              value={formData.effect}
              onChange={(value) => setFormData(prev => ({ ...prev, effect: value }))}
            >
              <Select.Option value="allow">{t.ALLOW}</Select.Option>
              <Select.Option value="deny">{t.DENY}</Select.Option>
            </Select>
          </Form.Item>
          
          <Form.Item label={t.PRIORITY}>
            <input
              type="number"
              value={formData.priority}
              onChange={(e) => setFormData(prev => ({ ...prev, priority: parseInt(e.target.value) }))}
              min="1"
              max="100"
              className="form-control"
            />
          </Form.Item>
        </div>
      )}
      
      <div className="form-actions">
        <Button type="primary" htmlType="submit" loading={loading}>
          {t.SUBMIT}
        </Button>
        <Button onClick={onCancel} style={{ marginLeft: 8 }}>
          {t.CANCEL}
        </Button>
      </div>
    </form>
  );
};

/**
 * ACL Policy Manager - Main component for ACL management
 */
const CapabilityAwareAcl = ({
  policies,
  onPolicyCreate,
  onPolicyUpdate,
  onPolicyDelete,
  t
}) => {
  const { capabilities } = useClusterCapabilities();
  const [isCreateModalVisible, setIsCreateModalVisible] = useState(false);
  const [editingPolicy, setEditingPolicy] = useState(null);
  
  const handleCreatePolicy = async (policyData) => {
    await onPolicyCreate(policyData);
    setIsCreateModalVisible(false);
  };
  
  const handleUpdatePolicy = async (policyData) => {
    await onPolicyUpdate(editingPolicy.id, policyData);
    setEditingPolicy(null);
  };
  
  const handleDeletePolicy = async (policyId) => {
    Modal.confirm({
      title: t.CONFIRM_DELETE,
      content: t.DELETE_POLICY_CONFIRM,
      onOk: () => onPolicyDelete(policyId),
    });
  };
  
  return (
    <div className="capability-aware-acl">
      <div className="acl-header">
        <h3>{t.ACL_MANAGEMENT}</h3>
        <AclVersionIndicator capabilities={capabilities} t={t} />
        <Button
          type="primary"
          onClick={() => setIsCreateModalVisible(true)}
        >
          {t.CREATE_POLICY}
        </Button>
      </div>
      
      {/* ACL Policies List */}
      <div className="acl-policies-list">
        {policies.map(policy => (
          <div key={policy.id} className="acl-policy-item">
            <div className="policy-info">
              <span className="subject">{policy.subjectType}: {policy.subjectValue}</span>
              <span className="resource">{policy.resourceType}: {policy.resourceValue}</span>
              <span className="permissions">{policy.permissions.join(', ')}</span>
            </div>
            <div className="policy-actions">
              <Button
                size="small"
                onClick={() => setEditingPolicy(policy)}
              >
                {t.EDIT}
              </Button>
              <Button
                size="small"
                danger
                onClick={() => handleDeletePolicy(policy.id)}
              >
                {t.DELETE}
              </Button>
            </div>
          </div>
        ))}
      </div>
      
      {/* Create Policy Modal */}
      <Modal
        title={t.CREATE_POLICY}
        visible={isCreateModalVisible}
        onCancel={() => setIsCreateModalVisible(false)}
        footer={null}
        width={800}
      >
        <AclPolicyForm
          onSubmit={handleCreatePolicy}
          onCancel={() => setIsCreateModalVisible(false)}
          capabilities={capabilities}
          t={t}
        />
      </Modal>
      
      {/* Edit Policy Modal */}
      <Modal
        title={t.EDIT_POLICY}
        visible={!!editingPolicy}
        onCancel={() => setEditingPolicy(null)}
        footer={null}
        width={800}
      >
        {editingPolicy && (
          <AclPolicyForm
            initialData={editingPolicy}
            onSubmit={handleUpdatePolicy}
            onCancel={() => setEditingPolicy(null)}
            capabilities={capabilities}
            t={t}
          />
        )}
      </Modal>
    </div>
  );
};

export default CapabilityAwareAcl;