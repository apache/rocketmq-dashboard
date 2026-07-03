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
import { render, screen, fireEvent } from '@testing-library/react';
import { ClusterCapabilitiesProvider } from '../../store/context/ClusterCapabilitiesContext';
import { AclVersionIndicator, AclSubjectSelector, AclResourceSelector, AclPermissionConfig } from './CapabilityAwareAcl';

// Mock translation function
const mockT = (key) => {
  const translations = {
    'ACL_2_0_FEATURES': 'Advanced ACL 2.0 features',
    'ADVANCED_FEATURES': 'Advanced Features',
    'CONSUMER_GROUP': 'Consumer Group',
    'PRODUCER_GROUP': 'Producer Group',
    'IP_WHITE_LIST': 'IP White List',
    'USER_ID': 'User ID',
    'ROLE': 'Role',
    'APPLICATION': 'Application',
    'SERVICE': 'Service',
    'SUBJECT_TYPE': 'Subject Type',
    'SUBJECT_VALUE': 'Subject Value',
    'SELECT_SUBJECT_TYPE': 'Select Subject Type',
    'SELECT_SUBJECT_VALUE': 'Select Subject Value',
    'LOAD_SUBJECT_OPTIONS_ERROR': 'Failed to load subject options',
    'TOPIC': 'Topic',
    'NAMESPACE': 'Namespace',
    'CLUSTER': 'Cluster',
    'TRANSACTION': 'Transaction',
    'RESOURCE_TYPE': 'Resource Type',
    'RESOURCE_PATTERN': 'Resource Pattern',
    'EXACT_MATCH': 'Exact Match',
    'PREFIX_MATCH': 'Prefix Match',
    'WILDCARD_MATCH': 'Wildcard Match',
    'REGEX_MATCH': 'Regex Match',
    'SELECT_RESOURCE_TYPE': 'Select Resource Type',
    'SELECT_PATTERN_TYPE': 'Select Pattern Type',
    'READ_PERMISSION': 'Read',
    'WRITE_PERMISSION': 'Write',
    'DELETE_PERMISSION': 'Delete',
    'ADMIN_PERMISSION': 'Admin',
    'CONFIGURE_PERMISSION': 'Configure',
    'CONSUME_PERMISSION': 'Consume',
    'PRODUCE_PERMISSION': 'Produce',
    'READ_DESC': 'Read access',
    'WRITE_DESC': 'Write access',
    'DELETE_DESC': 'Delete access',
    'ADMIN_DESC': 'Admin access',
    'CONFIGURE_DESC': 'Configure access',
    'CONSUME_DESC': 'Consume access',
    'PRODUCE_DESC': 'Produce access',
    'PERMISSIONS': 'Permissions',
    'ACL_2_0_PERMISSIONS': 'ACL 2.0 Permissions',
    'CURRENT_PERMISSIONS': 'Current Permissions',
    'NONE': 'None',
    'EFFECT': 'Effect',
    'ALLOW': 'Allow',
    'DENY': 'Deny',
    'PRIORITY': 'Priority',
    'ENTER_RESOURCE_NAME': 'Enter resource name',
    'FORM_VALIDATION_ERROR': 'Please fill in all required fields',
    'SUBMIT_ERROR': 'Failed to submit',
    'ACL_MANAGEMENT': 'ACL Management',
    'CREATE_POLICY': 'Create Policy',
    'EDIT_POLICY': 'Edit Policy',
    'CONFIRM_DELETE': 'Confirm Delete',
    'DELETE_POLICY_CONFIRM': 'Are you sure you want to delete this policy?',
    'EDIT': 'Edit',
    'SUBMIT': 'Submit',
    'CANCEL': 'Cancel'
  };
  return translations[key] || key;
};

// Test wrapper with capabilities provider
const TestWrapper = ({ children, capabilities = {} }) => {
  const defaultCapabilities = {
    hasNamespace: false,
    supportsLiteTopic: false,
    supportsPopConsumption: false,
    supportsGrpc: false,
    supportsAcl2: false,
    isV5Architecture: false,
    accessType: 'v4-namesrv'
  };

  return (
    <ClusterCapabilitiesProvider>
      {React.cloneElement(children, { capabilities: { ...defaultCapabilities, ...capabilities } })}
    </ClusterCapabilitiesProvider>
  );
};

describe('CapabilityAwareAcl Components', () => {
  describe('AclVersionIndicator', () => {
    test('should show ACL 1.0 for capabilities without ACL 2.0 support', () => {
      render(
        <TestWrapper>
          <AclVersionIndicator capabilities={{ supportsAcl2: false }} t={mockT} />
        </TestWrapper>
      );

      expect(screen.getByText('ACL 1.0')).toBeInTheDocument();
      expect(screen.queryByText('Advanced Features')).not.toBeInTheDocument();
    });

    test('should show ACL 2.0 with advanced features for capabilities with ACL 2.0 support', () => {
      render(
        <TestWrapper>
          <AclVersionIndicator capabilities={{ supportsAcl2: true }} t={mockT} />
        </TestWrapper>
      );

      expect(screen.getByText('ACL 2.0')).toBeInTheDocument();
      expect(screen.getByText('Advanced Features')).toBeInTheDocument();
    });
  });

  describe('AclSubjectSelector', () => {
    const mockOnSubjectTypeChange = jest.fn();
    const mockOnSubjectValueChange = jest.fn();

    beforeEach(() => {
      jest.clearAllMocks();
    });

    test('should show ACL 1.0 subject types for non-ACL 2.0 capabilities', () => {
      render(
        <TestWrapper capabilities={{ supportsAcl2: false }}>
          <AclSubjectSelector
            subjectType="consumerGroup"
            onSubjectTypeChange={mockOnSubjectTypeChange}
            subjectValue=""
            onSubjectValueChange={mockOnSubjectValueChange}
            capabilities={{ supportsAcl2: false }}
            t={mockT}
          />
        </TestWrapper>
      );

      const subjectTypeSelect = screen.getByLabelText('Subject Type');
      expect(subjectTypeSelect).toBeInTheDocument();

      // Should show ACL 1.0 subject types
      expect(screen.getByText('Consumer Group')).toBeInTheDocument();
      expect(screen.getByText('Producer Group')).toBeInTheDocument();
      expect(screen.getByText('IP White List')).toBeInTheDocument();

      // Should NOT show ACL 2.0 specific types
      expect(screen.queryByText('User ID')).not.toBeInTheDocument();
      expect(screen.queryByText('Role')).not.toBeInTheDocument();
      expect(screen.queryByText('Application')).not.toBeInTheDocument();
      expect(screen.queryByText('Service')).not.toBeInTheDocument();
    });

    test('should show ACL 2.0 subject types for ACL 2.0 capabilities', () => {
      render(
        <TestWrapper capabilities={{ supportsAcl2: true }}>
          <AclSubjectSelector
            subjectType="consumerGroup"
            onSubjectTypeChange={mockOnSubjectTypeChange}
            subjectValue=""
            onSubjectValueChange={mockOnSubjectValueChange}
            capabilities={{ supportsAcl2: true }}
            t={mockT}
          />
        </TestWrapper>
      );

      // Should show ACL 1.0 subject types
      expect(screen.getByText('Consumer Group')).toBeInTheDocument();
      expect(screen.getByText('Producer Group')).toBeInTheDocument();
      expect(screen.getByText('IP White List')).toBeInTheDocument();

      // Should also show ACL 2.0 specific types
      expect(screen.getByText('User ID')).toBeInTheDocument();
      expect(screen.getByText('Role')).toBeInTheDocument();
      expect(screen.getByText('Application')).toBeInTheDocument();
      expect(screen.getByText('Service')).toBeInTheDocument();
    });

    test('should have required fields marked', () => {
      render(
        <TestWrapper>
          <AclSubjectSelector
            subjectType="consumerGroup"
            onSubjectTypeChange={mockOnSubjectTypeChange}
            subjectValue=""
            onSubjectValueChange={mockOnSubjectValueChange}
            capabilities={{ supportsAcl2: false }}
            t={mockT}
          />
        </TestWrapper>
      );

      const subjectTypeLabel = screen.getByLabelText('Subject Type');
      const subjectValueLabel = screen.getByLabelText('Subject Value');
      
      expect(subjectTypeLabel).toBeRequired();
      expect(subjectValueLabel).toBeRequired();
    });
  });

  describe('AclResourceSelector', () => {
    const mockOnResourceTypeChange = jest.fn();
    const mockOnResourcePatternChange = jest.fn();

    beforeEach(() => {
      jest.clearAllMocks();
    });

    test('should show ACL 1.0 resource types for non-ACL 2.0 capabilities', () => {
      render(
        <TestWrapper capabilities={{ supportsAcl2: false }}>
          <AclResourceSelector
            resourceType="topic"
            onResourceTypeChange={mockOnResourceTypeChange}
            resourcePattern="exact"
            onResourcePatternChange={mockOnResourcePatternChange}
            capabilities={{ supportsAcl2: false }}
            t={mockT}
          />
        </TestWrapper>
      );

      // Should show basic resource types
      expect(screen.getByText('Topic')).toBeInTheDocument();
      expect(screen.getByText('Consumer Group')).toBeInTheDocument();

      // Should NOT show ACL 2.0 specific types
      expect(screen.queryByText('Cluster')).not.toBeInTheDocument();
      expect(screen.queryByText('Namespace')).not.toBeInTheDocument();
      expect(screen.queryByText('Transaction')).not.toBeInTheDocument();

      // Should NOT show pattern selector for ACL 1.0
      expect(screen.queryByLabelText('Resource Pattern')).not.toBeInTheDocument();
    });

    test('should show ACL 2.0 resource types and patterns for ACL 2.0 capabilities', () => {
      render(
        <TestWrapper capabilities={{ supportsAcl2: true }}>
          <AclResourceSelector
            resourceType="topic"
            onResourceTypeChange={mockOnResourceTypeChange}
            resourcePattern="exact"
            onResourcePatternChange={mockOnResourcePatternChange}
            capabilities={{ supportsAcl2: true }}
            t={mockT}
          />
        </TestWrapper>
      );

      // Should show basic resource types
      expect(screen.getByText('Topic')).toBeInTheDocument();
      expect(screen.getByText('Consumer Group')).toBeInTheDocument();

      // Should also show ACL 2.0 specific types
      expect(screen.getByText('Cluster')).toBeInTheDocument();
      expect(screen.getByText('Namespace')).toBeInTheDocument();
      expect(screen.getByText('Transaction')).toBeInTheDocument();

      // Should show pattern selector for ACL 2.0
      expect(screen.getByLabelText('Resource Pattern')).toBeInTheDocument();
      expect(screen.getByText('Exact Match')).toBeInTheDocument();
      expect(screen.getByText('Prefix Match')).toBeInTheDocument();
      expect(screen.getByText('Wildcard Match')).toBeInTheDocument();
      expect(screen.getByText('Regex Match')).toBeInTheDocument();
    });

    test('should have required resource type field', () => {
      render(
        <TestWrapper>
          <AclResourceSelector
            resourceType="topic"
            onResourceTypeChange={mockOnResourceTypeChange}
            resourcePattern="exact"
            onResourcePatternChange={mockOnResourcePatternChange}
            capabilities={{ supportsAcl2: false }}
            t={mockT}
          />
        </TestWrapper>
      );

      const resourceTypeLabel = screen.getByLabelText('Resource Type');
      expect(resourceTypeLabel).toBeRequired();
    });
  });

  describe('AclPermissionConfig', () => {
    const mockOnPermissionsChange = jest.fn();

    beforeEach(() => {
      jest.clearAllMocks();
    });

    test('should show basic permissions for ACL 1.0', () => {
      render(
        <TestWrapper capabilities={{ supportsAcl2: false }}>
          <AclPermissionConfig
            permissions={[]}
            onPermissionsChange={mockOnPermissionsChange}
            capabilities={{ supportsAcl2: false }}
            t={mockT}
          />
        </TestWrapper>
      );

      // Should show basic permissions
      expect(screen.getByText('Permissions')).toBeInTheDocument();
      expect(screen.getByText('Read')).toBeInTheDocument();
      expect(screen.getByText('Write')).toBeInTheDocument();

      // Should NOT show ACL 2.0 permissions
      expect(screen.queryByText('ACL 2.0 Permissions')).not.toBeInTheDocument();
      expect(screen.queryByText('Delete')).not.toBeInTheDocument();
      expect(screen.queryByText('Admin')).not.toBeInTheDocument();
      expect(screen.queryByText('Configure')).not.toBeInTheDocument();
      expect(screen.queryByText('Consume')).not.toBeInTheDocument();
      expect(screen.queryByText('Produce')).not.toBeInTheDocument();

      // Should show permission summary
      expect(screen.getByText('Current Permissions: None')).toBeInTheDocument();
    });

    test('should show extended permissions for ACL 2.0', () => {
      render(
        <TestWrapper capabilities={{ supportsAcl2: true }}>
          <AclPermissionConfig
            permissions={[]}
            onPermissionsChange={mockOnPermissionsChange}
            capabilities={{ supportsAcl2: true }}
            t={mockT}
          />
        </TestWrapper>
      );

      // Should show basic permissions
      expect(screen.getByText('Permissions')).toBeInTheDocument();
      expect(screen.getByText('Read')).toBeInTheDocument();
      expect(screen.getByText('Write')).toBeInTheDocument();

      // Should show ACL 2.0 permissions section
      expect(screen.getByText('ACL 2.0 Permissions')).toBeInTheDocument();
      expect(screen.getByText('Delete')).toBeInTheDocument();
      expect(screen.getByText('Admin')).toBeInTheDocument();
      expect(screen.getByText('Configure')).toBeInTheDocument();
      expect(screen.getByText('Consume')).toBeInTheDocument();
      expect(screen.getByText('Produce')).toBeInTheDocument();
    });

    test('should update permissions when toggled', () => {
      render(
        <TestWrapper capabilities={{ supportsAcl2: true }}>
          <AclPermissionConfig
            permissions={['read']}
            onPermissionsChange={mockOnPermissionsChange}
            capabilities={{ supportsAcl2: true }}
            t={mockT}
          />
        </TestWrapper>
      );

      // Find and click the Write permission switch
      const writeSwitches = screen.getAllByRole('switch');
      const writeSwitch = writeSwitches.find(switchEl => 
        switchEl.parentElement.textContent.includes('Write')
      );
      
      if (writeSwitch) {
        fireEvent.click(writeSwitch);
        expect(mockOnPermissionsChange).toHaveBeenCalledWith(['read', 'write']);
      }
    });

    test('should display current permissions correctly', () => {
      render(
        <TestWrapper capabilities={{ supportsAcl2: true }}>
          <AclPermissionConfig
            permissions={['read', 'write']}
            onPermissionsChange={mockOnPermissionsChange}
            capabilities={{ supportsAcl2: true }}
            t={mockT}
          />
        </TestWrapper>
      );

      expect(screen.getByText('Current Permissions: read, write')).toBeInTheDocument();
    });

    test('should display None when no permissions are set', () => {
      render(
        <TestWrapper capabilities={{ supportsAcl2: true }}>
          <AclPermissionConfig
            permissions={[]}
            onPermissionsChange={mockOnPermissionsChange}
            capabilities={{ supportsAcl2: true }}
            t={mockT}
          />
        </TestWrapper>
      );

      expect(screen.getByText('Current Permissions: None')).toBeInTheDocument();
    });
  });
});